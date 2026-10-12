package com.trancong.dexworkspacetouch.workspace.close

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class PackageCloseCoordinator(
    private val backend: CloseTaskBackend,
    private val store: CloseOperationStore,
    private val clock: () -> Long,
    private val limits: CloseLimits = CloseLimits(),
) : PackageCloseBackend {
    private val admission = Mutex()
    private var recovered = false
    private var storageFailed = false

    suspend fun recoverPending() = admission.withLock { recoverLocked() }

    private fun recoverLocked() {
        if (recovered) return
        try {
            store.pending().forEach { pending ->
                val report = report(pending, if (hasIntent(pending)) PackageCloseStatus.PARTIAL else PackageCloseStatus.BACKEND_UNAVAILABLE,
                    "COORDINATOR_LOST", emptyList(), false)
                store.write(pending.copy(revision = pending.revision + 1, terminal = report))
            }
            recovered = true
        } catch (_: Exception) {
            storageFailed = true
            throw CloseBackendException("JOURNAL_IO_FAILED")
        }
    }

    override suspend fun closePackages(request: PackageCloseRequest): PackageCloseReport {
        val immutable = request.snapshot()
        return admission.withLock {
            // Caller cancellation cannot abandon an accepted mutation ledger.
            withContext(NonCancellable) { close(immutable) }
        }
    }

    private suspend fun close(request: PackageCloseRequest): PackageCloseReport {
        var record = CloseOperationRecord(request, startedAt = clock(), limits = limits)
        val verification = mutableListOf<CloseSnapshot>()
        var reason: String? = null
        var unsupported = false
        var verifiedTerminal = false
        var cleanSnapshots = 0
        var batches = 0
        val deadline = record.startedAt + limits.deadlineMs

        fun persist(next: CloseOperationRecord) {
            try { store.write(next); record = next }
            catch (e: Exception) { storageFailed = true; throw CloseBackendException("JOURNAL_IO_FAILED") }
        }
        suspend fun <T> rpc(timeoutMs: Long = limits.ipcMs, block: suspend () -> T): T {
            if (clock() >= deadline) throw CloseBackendException("DEADLINE_EXCEEDED")
            return try { withTimeout(minOf(timeoutMs, deadline - clock())) {
                withTimeout(minOf(limits.ipcMs, deadline - clock())) { backend.checkAvailable() }
                block()
            } }
            catch (e: TimeoutCancellationException) { throw CloseBackendException("IPC_TIMEOUT") }
        }
        fun discover(snapshot: CloseSnapshot, round: Int) {
            val targets = CloseTaskPolicy.related(request, snapshot)
            val additions = targets.filter { target -> record.results.none { it.expected.sameIdentity(target) } }
                .map { CloseTaskResult(it, round) }
            if (additions.isNotEmpty()) persist(record.copy(revision = record.revision + 1, results = record.results + additions))
        }
        suspend fun enumerate(round: Int): CloseSnapshot {
            val snapshot = rpc { backend.enumerate() }
            persist(record.copy(revision = record.revision + 1, lastSnapshot = snapshot,
                before = record.before ?: snapshot,
                tasksBefore = if (record.before == null) CloseTaskPolicy.related(request, snapshot) else record.tasksBefore))
            CloseTaskPolicy.coverageProblem(snapshot)?.let { unsupported = true; throw CloseBackendException(it) }
            discover(snapshot, round)
            if (CloseTaskPolicy.related(request, snapshot).any { !CloseTaskPolicy.safe(request, it) }) {
                unsupported = true
                throw CloseBackendException("UNSUPPORTED_TASK")
            }
            return snapshot
        }

        try {
            if (storageFailed) throw CloseBackendException("JOURNAL_IO_FAILED")
            // A pending operation is evidence of lost authority, never a replay instruction.
            recoverLocked()
            val existing = store.read(request.operationId)
            if (existing != null) {
                require(existing.request == request) { "OPERATION_ID_CONFLICT" }
                return checkNotNull(existing.terminal)
            }
            persist(record)
            CloseTaskPolicy.preflight(request)?.let { unsupported = true; throw CloseBackendException(it) }
            if (request.packagesRequested.isEmpty()) {
                verifiedTerminal = true
            } else {
                if (!backend.quiescent) throw CloseBackendException("EXECUTOR_IN_FLIGHT")
                withTimeout(limits.deadlineMs) {
                    // Cold UserService startup is bounded separately; enumerate/remove retain the IPC budget.
                    rpc(limits.connectMs) { backend.open(request, deadline) }
                    var snapshot = enumerate(0)
                    persist(record.copy(revision = record.revision + 1,
                        before = snapshot, tasksBefore = CloseTaskPolicy.related(request, snapshot)))
                    while (batches < limits.batches) {
                        val fresh = CloseTaskPolicy.related(request, snapshot).filter { target ->
                            record.results.none { it.expected.sameIdentity(target) && it.dispatch != CloseDispatch.NOT_SENT }
                        }
                        if (fresh.isEmpty()) break
                        batches++
                        for (target in fresh) {
                            if (record.results.count { it.dispatch != CloseDispatch.NOT_SENT } >= limits.attempts) {
                                reason = reason ?: "ATTEMPT_LIMIT"; break
                            }
                            val index = record.results.indexOfFirst { it.expected.sameIdentity(target) }
                            val intent = record.results[index].copy(dispatch = CloseDispatch.DISPATCH_INTENT, result = CloseRemoveResult.UNKNOWN)
                            persist(record.copy(revision = record.revision + 1, results = record.results.toMutableList().apply { set(index, intent) }))
                            val reply = rpc { backend.removeVerified(target) }
                            check(reply.expected.sameIdentity(target) && reply.dispatch == CloseDispatch.REPLY_RECEIVED) { "INVALID_REMOVE_REPLY" }
                            persist(record.copy(revision = record.revision + 1, results = record.results.toMutableList().apply {
                                set(index, reply.copy(discoveryRound = intent.discoveryRound))
                            }))
                            if (reply.result == CloseRemoveResult.IDENTITY_CHANGED || reply.result == CloseRemoveResult.UNSUPPORTED_TASK)
                                reason = reason ?: reply.result.name
                        }
                        snapshot = enumerate(batches)
                        if (reason == "ATTEMPT_LIMIT") break
                    }
                    val verificationStart = clock()
                    for (offset in listOf(0L, 200L, 500L)) {
                        delay((verificationStart + offset - clock()).coerceAtLeast(0))
                        val final = enumerate(batches + 1)
                        verification += final
                        cleanSnapshots = if (CloseTaskPolicy.related(request, final).isEmpty()) cleanSnapshots + 1 else 0
                        if (cleanSnapshots >= 2) { verifiedTerminal = true; break }
                    }
                    if (!verifiedTerminal) reason = reason ?: "TARGETS_REMAIN_OR_UNSTABLE"
                }
            }
        } catch (e: IllegalArgumentException) {
            if (e.message == "OPERATION_ID_CONFLICT") throw e
            reason = "INVALID_BACKEND_EVIDENCE"
        } catch (e: TimeoutCancellationException) {
            reason = "DEADLINE_EXCEEDED"
        } catch (e: CloseBackendException) {
            reason = e.reason
            if (e.reason.startsWith("UNSUPPORTED_") || e.reason == "PROTECTED_PACKAGE") unsupported = true
        } catch (e: Exception) {
            reason = if (!recovered) { storageFailed = true; "JOURNAL_IO_FAILED" } else "BACKEND_LOST"
        } finally {
            backend.invalidate()
        }
        val status = when {
            reason != null && hasIntent(record) -> PackageCloseStatus.PARTIAL
            unsupported -> PackageCloseStatus.UNSUPPORTED
            reason != null -> PackageCloseStatus.BACKEND_UNAVAILABLE
            verifiedTerminal && record.tasksBefore.isEmpty() && !hasIntent(record) -> PackageCloseStatus.NO_OP
            verifiedTerminal -> PackageCloseStatus.COMPLETE
            else -> PackageCloseStatus.PARTIAL
        }
        // Only a complete terminal enumeration proves present/absent. Earlier snapshots stay last-known.
        val terminalCoverage = verification.lastOrNull()?.let { it == record.lastSnapshot && CloseTaskPolicy.coverageProblem(it) == null } == true &&
            reason !in setOf("BACKEND_LOST", "PERMISSION_REVOKED", "IPC_TIMEOUT", "DEADLINE_EXCEEDED", "JOURNAL_IO_FAILED", "INVALID_BACKEND_EVIDENCE")
        var result = report(record, status, reason, verification, terminalCoverage)
        if (!storageFailed) {
            try { persist(record.copy(revision = record.revision + 1, terminal = result)) }
            catch (e: CloseBackendException) { result = result.copy(status = if (hasIntent(record)) PackageCloseStatus.PARTIAL else PackageCloseStatus.BACKEND_UNAVAILABLE,
                terminalReason = "JOURNAL_IO_FAILED", persistenceFailed = true) }
        } else result = result.copy(persistenceFailed = true)
        return result
    }

    private fun hasIntent(record: CloseOperationRecord) = record.results.any { it.dispatch != CloseDispatch.NOT_SENT }

    private fun report(record: CloseOperationRecord, status: PackageCloseStatus, reason: String?, verification: List<CloseSnapshot>, known: Boolean): PackageCloseReport {
        val snapshot = if (known) verification.lastOrNull() else record.lastSnapshot
        val remaining = record.request.packagesRequested.associateWith { pkg ->
            val tasks = snapshot?.tasks.orEmpty().filter { it.userId == 0 && (it.basePackage == pkg || it.topPackage == pkg) }
            CloseRemaining(if (!known) CloseObservation.UNKNOWN else if (tasks.isEmpty()) CloseObservation.VERIFIED_EMPTY else CloseObservation.VERIFIED_PRESENT,
                tasks, snapshot?.sequence, snapshot?.observedAt, if (known) null else reason ?: "NO_TERMINAL_ENUMERATION")
        }
        val results = record.results.map { r -> r.copy(finalObservation = if (!known) CloseObservation.UNKNOWN
            else if (snapshot!!.tasks.any { it.sameIdentity(r.expected) }) CloseObservation.VERIFIED_PRESENT else CloseObservation.VERIFIED_EMPTY) }
        return PackageCloseReport(record.request, status, reason, record.tasksBefore, results, remaining,
            record.before, verification.toList(), record.startedAt, clock(), record.limits)
    }
}
