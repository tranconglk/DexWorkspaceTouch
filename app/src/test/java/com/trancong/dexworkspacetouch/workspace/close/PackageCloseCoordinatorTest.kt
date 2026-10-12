package com.trancong.dexworkspacetouch.workspace.close

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PackageCloseCoordinatorTest {
    private val pkg = "example.target"
    private fun task(id: Int, display: Int = 0, user: Int = 0) = CloseTask(id, user, display, 99, "$pkg/.Main", "$pkg/.Main", true)
    private fun request(vararg packages: String) = PackageCloseRequest(UUID.randomUUID().toString(), packages.toList())

    private class Store : CloseOperationStore {
        val records = mutableMapOf<String, CloseOperationRecord>()
        var failOnIntent = false
        override fun read(operationId: String) = records[operationId]
        override fun pending() = records.values.filter { it.terminal == null }
        override fun write(record: CloseOperationRecord) {
            if (failOnIntent && record.results.any { it.dispatch == CloseDispatch.DISPATCH_INTENT }) error("disk full")
            val old = read(record.request.operationId)
            check(record.revision == (old?.revision?.plus(1) ?: 0))
            records[record.request.operationId] = record
        }
    }
    private class Backend(val tasks: MutableList<CloseTask>, val store: Store) : CloseTaskBackend {
        override var quiescent = true
        var opened = 0
        var calls = 0
        var sequence = 0
        var keepTask = false
        var unavailable = false
        var incomplete = false
        var dieAfterRemove = false
        var result = CloseRemoveResult.REMOVE_TRUE
        var hangRemove = false
        var hangAvailability = false
        var startupDelayMs = 0L
        var onEnumerate: (() -> Unit)? = null
        override suspend fun checkAvailable() {
            if (hangAvailability) { quiescent = false; delay(20_000) }
            if (unavailable) throw CloseBackendException("PERMISSION_REVOKED")
        }
        override suspend fun open(request: PackageCloseRequest, deadline: Long) {
            opened++
            delay(startupDelayMs)
            if (unavailable) throw CloseBackendException("PERMISSION_REVOKED")
        }
        override suspend fun enumerate(): CloseSnapshot {
            if (unavailable) throw CloseBackendException("BACKEND_LOST")
            onEnumerate?.invoke()
            return CloseSnapshot(tasks.toList(), !incomplete, ++sequence, sequence.toLong(), "fake", "fake", if (incomplete) "ENUMERATION_INCOMPLETE" else null)
        }
        override suspend fun removeVerified(expected: CloseTask): CloseTaskResult {
            check(store.records.values.any { r -> r.results.any { it.expected == expected && it.dispatch == CloseDispatch.DISPATCH_INTENT } })
            calls++
            if (hangRemove) { quiescent = false; delay(20_000) }
            if (!keepTask && result != CloseRemoveResult.IDENTITY_CHANGED) tasks.remove(expected)
            if (dieAfterRemove) unavailable = true
            return CloseTaskResult(expected, 0, CloseDispatch.REPLY_RECEIVED, result, expected)
        }
        override fun invalidate() = Unit
    }
    private fun coordinator(backend: Backend, store: Store, limits: CloseLimits = CloseLimits()) =
        PackageCloseCoordinator(backend, store, { 0L }, limits)

    @Test fun closesDistinctPackagesAcrossDisplaysButOnlyUserZero() = runTest {
        val store = Store()
        val otherUser = task(4, 0, 10)
        val b = Backend(mutableListOf(task(1), task(2, 2), task(3, 5), otherUser), store)
        val r = coordinator(b, store).closePackages(request(pkg, pkg))
        assertEquals(listOf(pkg), r.request.packagesRequested)
        assertEquals(PackageCloseStatus.COMPLETE, r.status)
        assertEquals(3, b.calls)
        assertEquals(listOf(otherUser), b.tasks)
        assertEquals(CloseObservation.VERIFIED_EMPTY, r.remainingByPackage.getValue(pkg).state)
        assertTrue(r.verification.takeLast(2).all { it.complete && it.tasks.none { t -> t.userId == 0 } })
    }
    @Test fun protectedDwtRejectsEntireRequestWithoutBinding() = runTest {
        val s = Store(); val b = Backend(mutableListOf(task(1)), s)
        val r = coordinator(b, s).closePackages(request(pkg, DWT_PROTECTED_PACKAGE))
        assertEquals(PackageCloseStatus.UNSUPPORTED, r.status)
        assertEquals("PROTECTED_PACKAGE", r.terminalReason)
        assertEquals(0, b.opened)
        assertEquals(2, r.remainingByPackage.size)
    }
    @Test fun trueReplyWithRemainingTaskIsPartialAndNeverRetried() = runTest {
        val s = Store(); val b = Backend(mutableListOf(task(1)), s).apply { keepTask = true }
        val r = coordinator(b, s).closePackages(request(pkg))
        assertEquals(PackageCloseStatus.PARTIAL, r.status)
        assertEquals(CloseObservation.VERIFIED_PRESENT, r.remainingByPackage.getValue(pkg).state)
        assertEquals(1, b.calls)
    }
    @Test fun deniedPermissionIsUnknownAndNotNoOp() = runTest {
        val s = Store(); val b = Backend(mutableListOf(), s).apply { unavailable = true }
        val r = coordinator(b, s).closePackages(request(pkg))
        assertEquals(PackageCloseStatus.BACKEND_UNAVAILABLE, r.status)
        assertEquals(CloseObservation.UNKNOWN, r.remainingByPackage.getValue(pkg).state)
    }
    @Test fun revokeAfterReplyKeepsRemainingUnknownAndStopsDispatch() = runTest {
        val s = Store(); val b = Backend(mutableListOf(task(1), task(2)), s).apply { dieAfterRemove = true }
        val r = coordinator(b, s).closePackages(request(pkg))
        assertEquals(PackageCloseStatus.PARTIAL, r.status)
        assertEquals(CloseObservation.UNKNOWN, r.remainingByPackage.getValue(pkg).state)
        assertEquals(1, b.calls)
    }
    @Test fun incompleteEnumerationNeverClaimsEmpty() = runTest {
        val s = Store(); val b = Backend(mutableListOf(), s).apply { incomplete = true }
        val r = coordinator(b, s).closePackages(request(pkg))
        assertEquals(PackageCloseStatus.UNSUPPORTED, r.status)
        assertEquals(CloseObservation.UNKNOWN, r.remainingByPackage.getValue(pkg).state)
    }
    @Test fun noRunningPackageNeedsTwoCompleteSnapshots() = runTest {
        val s = Store(); val b = Backend(mutableListOf(), s)
        val r = coordinator(b, s).closePackages(request(pkg))
        assertEquals(PackageCloseStatus.NO_OP, r.status)
        assertTrue(r.verification.size >= 2)
    }
    @Test fun mixedPackageTaskIsRejectedBeforeAnyMutation() = runTest {
        val s = Store(); val b = Backend(mutableListOf(task(1).copy(topActivity = "another.pkg/.Main")), s)
        val r = coordinator(b, s).closePackages(request(pkg))
        assertEquals(PackageCloseStatus.UNSUPPORTED, r.status)
        assertEquals(0, b.calls)
    }
    @Test fun identityChangeRemainsPartialEvenIfTaskDisappears() = runTest {
        val s = Store(); val b = Backend(mutableListOf(task(1)), s).apply { result = CloseRemoveResult.IDENTITY_CHANGED }
        val r = coordinator(b, s).closePackages(request(pkg))
        assertEquals(PackageCloseStatus.PARTIAL, r.status)
        assertEquals("IDENTITY_CHANGED", r.terminalReason)
    }
    @Test fun attemptBudgetStopsFurtherMutation() = runTest {
        val s = Store(); val b = Backend(mutableListOf(task(1), task(2)), s)
        val r = coordinator(b, s, CloseLimits(attempts = 1)).closePackages(request(pkg))
        assertEquals(PackageCloseStatus.PARTIAL, r.status)
        assertEquals(1, b.calls)
    }
    @Test fun diskFailureBeforeIntentPreventsRemove() = runTest {
        val s = Store().apply { failOnIntent = true }; val b = Backend(mutableListOf(task(1)), s)
        val r = coordinator(b, s).closePackages(request(pkg))
        assertEquals("JOURNAL_IO_FAILED", r.terminalReason)
        assertTrue(r.persistenceFailed)
        assertEquals(0, b.calls)
    }
    @Test fun terminalIdempotencyDoesNotReplayAndConflictIsRejected() = runTest {
        val s = Store(); val b = Backend(mutableListOf(task(1)), s); val c = coordinator(b, s); val req = request(pkg)
        val first = c.closePackages(req)
        assertEquals(first, c.closePackages(req))
        assertEquals(1, b.calls)
        try { c.closePackages(req.copy(packagesRequested = listOf("different.pkg"))); fail() }
        catch (e: IllegalArgumentException) { assertEquals("OPERATION_ID_CONFLICT", e.message) }
    }
    @Test fun recoveryConcludesUnknownWithoutReplayingIntent() = runTest {
        val s = Store(); val req = request(pkg)
        s.write(CloseOperationRecord(req, results = listOf(CloseTaskResult(task(1), 0, CloseDispatch.DISPATCH_INTENT, CloseRemoveResult.UNKNOWN)), startedAt = 0))
        val b = Backend(mutableListOf(task(1)), s)
        val r = coordinator(b, s).closePackages(req)
        assertEquals(PackageCloseStatus.PARTIAL, r.status)
        assertEquals("COORDINATOR_LOST", r.terminalReason)
        assertEquals(0, b.calls)
        assertEquals(CloseObservation.UNKNOWN, r.remainingByPackage.getValue(pkg).state)
    }
    @Test fun timeoutKeepsIntentUnknownAndRejectsNewMutationUntilQuiescent() = runTest {
        val s = Store(); val b = Backend(mutableListOf(task(1), task(2)), s).apply { hangRemove = true }
        val c = PackageCloseCoordinator(b, s, { testScheduler.currentTime }, CloseLimits(deadlineMs = 500, ipcMs = 200))
        val r = c.closePackages(request(pkg))
        assertEquals(PackageCloseStatus.PARTIAL, r.status)
        assertEquals("IPC_TIMEOUT", r.terminalReason)
        assertEquals(CloseRemoveResult.UNKNOWN, r.perTaskResults.first().result)
        assertTrue(testScheduler.currentTime <= 500)
        val second = c.closePackages(request(pkg))
        assertEquals("EXECUTOR_IN_FLIGHT", second.terminalReason)
        assertEquals(1, b.calls)
    }
    @Test fun falseReplyFollowedByVerifiedAbsenceCanCompleteWithoutInventingTrue() = runTest {
        val s = Store(); val b = Backend(mutableListOf(task(1)), s).apply { result = CloseRemoveResult.REMOVE_FALSE }
        val r = coordinator(b, s).closePackages(request(pkg))
        assertEquals(PackageCloseStatus.COMPLETE, r.status)
        assertEquals(CloseRemoveResult.REMOVE_FALSE, r.perTaskResults.single().result)
    }
    @Test fun newTasksAreBoundedByThreeMutationBatches() = runTest {
        val s = Store(); val b = Backend(mutableListOf(task(1)), s)
        var nextId = 1
        b.onEnumerate = { if (b.tasks.isEmpty()) b.tasks += task(++nextId) }
        val r = coordinator(b, s).closePackages(request(pkg))
        assertEquals(PackageCloseStatus.PARTIAL, r.status)
        assertEquals(3, b.calls)
        assertEquals(CloseObservation.VERIFIED_PRESENT, r.remainingByPackage.getValue(pkg).state)
    }
    @Test fun missingPostEnumerationNeverUsesEarlierCleanSnapshotAsTerminalEvidence() = runTest {
        val s = Store(); val b = Backend(mutableListOf(task(1)), s)
        b.onEnumerate = { if (b.sequence >= 3) b.incomplete = true }
        val r = coordinator(b, s).closePackages(request(pkg))
        assertEquals(PackageCloseStatus.PARTIAL, r.status)
        assertEquals(CloseObservation.UNKNOWN, r.remainingByPackage.getValue(pkg).state)
    }
    @Test fun unsupportedUserAndLeafAreNeverMutated() = runTest {
        val s = Store(); val b = Backend(mutableListOf(task(1).copy(leaf = false)), s); val c = coordinator(b, s)
        assertEquals("UNSUPPORTED_USER", c.closePackages(request(pkg).copy(userId = 10)).terminalReason)
        assertEquals(PackageCloseStatus.UNSUPPORTED, c.closePackages(request(pkg)).status)
        assertEquals(0, b.calls)
    }
    @Test fun nullPackageMetadataCannotProvePreviouslySeenTaskAbsent() = runTest {
        val s = Store(); val b = Backend(mutableListOf(task(1)), s).apply { keepTask = true; result = CloseRemoveResult.REMOVE_FALSE }
        b.onEnumerate = { if (b.calls > 0) b.tasks[0] = task(1).copy(baseActivity = null, topActivity = null) }
        val r = coordinator(b, s).closePackages(request(pkg))
        assertEquals(PackageCloseStatus.PARTIAL, r.status)
        assertEquals(CloseObservation.UNKNOWN, r.remainingByPackage.getValue(pkg).state)
    }
    @Test fun availabilityProbeIsAlsoSubjectToIpcDeadline() = runTest {
        val s = Store(); val b = Backend(mutableListOf(task(1)), s).apply { hangAvailability = true }
        val c = PackageCloseCoordinator(b, s, { testScheduler.currentTime }, CloseLimits(deadlineMs = 500, ipcMs = 200))
        val r = c.closePackages(request(pkg))
        assertEquals(PackageCloseStatus.BACKEND_UNAVAILABLE, r.status)
        assertEquals("IPC_TIMEOUT", r.terminalReason)
        assertTrue(testScheduler.currentTime <= 500)
        assertEquals(0, b.calls)
    }
    @Test fun coldServiceStartupHasSeparateBoundedBudget() = runTest {
        val s = Store(); val b = Backend(mutableListOf(task(1)), s).apply { startupDelayMs = 2_500 }
        val c = PackageCloseCoordinator(b, s, { testScheduler.currentTime })
        val r = c.closePackages(request(pkg))
        assertEquals(PackageCloseStatus.COMPLETE, r.status)
        assertEquals(1, b.calls)
        assertTrue(testScheduler.currentTime < 10_000)
    }
    @Test fun stalledStartupCannotConsumeWholeOperationDeadline() = runTest {
        val s = Store(); val b = Backend(mutableListOf(task(1)), s).apply { startupDelayMs = 20_000 }
        val c = PackageCloseCoordinator(b, s, { testScheduler.currentTime })
        val r = c.closePackages(request(pkg))
        assertEquals(PackageCloseStatus.BACKEND_UNAVAILABLE, r.status)
        assertEquals("IPC_TIMEOUT", r.terminalReason)
        assertEquals(5_000L, testScheduler.currentTime)
        assertEquals(0, b.calls)
    }
}
