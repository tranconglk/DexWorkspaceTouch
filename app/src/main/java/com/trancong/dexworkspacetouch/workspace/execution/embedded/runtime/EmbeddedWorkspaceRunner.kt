package com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime

import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionFailure
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionPhase
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionSnapshot
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedTouchEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.trancong.dexworkspacetouch.diagnostics.embedded.EmbeddedEvidence
import com.trancong.dexworkspacetouch.diagnostics.embedded.EmbeddedResultEvidence

class EmbeddedWorkspaceRunner(
    preflight: EmbeddedWorkspacePreflight,
    sessionFactory: EmbeddedWorkspaceSessionFactory,
    private val timeoutPolicy: EmbeddedWorkspaceRunnerTimeoutPolicy,
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    val evidenceGraphId: String = java.util.UUID.randomUUID().toString(),
) {
    private val events = Channel<Event>(Channel.UNLIMITED)
    private val admissionLock = Any()
    private var runnerScope: CoroutineScope? = CoroutineScope(scope.coroutineContext + SupervisorJob() + dispatcher)
    private var preflight: EmbeddedWorkspacePreflight? = preflight
    private var sessionFactory: EmbeddedWorkspaceSessionFactory? = sessionFactory
    private var terminalSources: Set<String> = emptySet()

    private var phase = EmbeddedWorkspaceRunnerPhase.IDLE
    private var startAttempted = false
    private var preparedItems: List<EmbeddedWorkspacePreparedItem> = emptyList()
    private var nextStartIndex = 0
    private var currentStartupSourceId: String? = null
    private val ownedItems = linkedMapOf<String, OwnedItem>()

    private var startReply: CompletableDeferred<EmbeddedWorkspaceRunResult>? = null
    private val terminalWaiters = mutableListOf<CompletableDeferred<EmbeddedWorkspaceRunResult>>()
    private var terminalResult: EmbeddedWorkspaceRunResult? = null

    private var phaseWaitKind: PhaseWaitKind? = null
    private var phaseWaitSourceId: String? = null
    private var phaseWaitToken = 0L
    private var phaseWaitJob: Job? = null

    private var cleanupActive = false
    private var cleanupReason: CleanupReason? = null
    private var cleanupOrder: List<String> = emptyList()
    private var cleanupIndex = 0
    private var currentCleanupSourceId: String? = null
    private val cleanupOutcomes = mutableListOf<EmbeddedWorkspaceCleanupOutcome>()
    private var cleanupWaitToken = 0L
    private var cleanupWaitJob: Job? = null

    init {
        checkNotNull(runnerScope).launch {
            for (event in events) {
                handle(event)
            }
        }
    }

    suspend fun start(request: EmbeddedWorkspaceExecutionRequest): EmbeddedWorkspaceRunResult {
        if (cachedTerminal() != null) return EmbeddedWorkspaceRunResult.DuplicateCall
        val reply = CompletableDeferred<EmbeddedWorkspaceRunResult>()
        admit(Event.Start(request, reply))
        return reply.await()
    }

    suspend fun stop(): EmbeddedWorkspaceRunResult {
        cachedTerminal()?.let { return it }
        val reply = CompletableDeferred<EmbeddedWorkspaceRunResult>()
        admit(Event.Stop(reply))
        return reply.await()
    }

    suspend fun surfaceLost(sourceCellId: String): EmbeddedWorkspaceRunResult {
        cachedTerminal()?.let { return it }
        val reply = CompletableDeferred<EmbeddedWorkspaceRunResult>()
        admit(Event.SurfaceLost(sourceCellId, reply))
        return reply.await()
    }

    suspend fun sendTouch(
        sourceCellId: String,
        event: EmbeddedTouchEvent,
    ): EmbeddedWorkspaceTouchResult {
        val reply = CompletableDeferred<EmbeddedWorkspaceTouchResult>()
        admit(Event.Touch(sourceCellId, event, reply))
        return reply.await()
    }

    private fun cachedTerminal(): EmbeddedWorkspaceRunResult? = synchronized(admissionLock) { terminalResult }

    /** Publication and enqueue are one boundary; replies/external work run outside its lock. */
    private fun admit(event: Event) {
        val terminal = synchronized(admissionLock) {
            terminalResult.also { if (it == null) check(events.trySend(event).isSuccess) }
        }
        if (terminal != null) resolveTerminal(event, terminal)
    }

    private fun resolveTerminal(event: Event, result: EmbeddedWorkspaceRunResult) {
        when (event) {
            is Event.Start -> event.reply.complete(EmbeddedWorkspaceRunResult.DuplicateCall)
            is Event.Stop -> event.reply.complete(result)
            is Event.SurfaceLost -> event.reply.complete(result)
            is Event.Touch -> event.reply.complete(EmbeddedWorkspaceTouchResult.Rejected(event.sourceCellId,
                if (event.sourceCellId in terminalSources) "NOT_ACTIVE" else "UNKNOWN_SOURCE"))
            is Event.Snapshot, is Event.PhaseTimeout, is Event.CleanupTimeout -> Unit
        }
    }

    private fun handle(event: Event) {
        when (event) {
            is Event.Start -> handleStart(event)
            is Event.Stop -> handleStop(event)
            is Event.SurfaceLost -> handleSurfaceLost(event)
            is Event.Touch -> handleTouch(event)
            is Event.Snapshot -> handleSnapshot(event)
            is Event.PhaseTimeout -> handlePhaseTimeout(event)
            is Event.CleanupTimeout -> handleCleanupTimeout(event)
        }
    }

    private fun handleStart(event: Event.Start) {
        if (startAttempted || phase != EmbeddedWorkspaceRunnerPhase.IDLE) {
            event.reply.complete(EmbeddedWorkspaceRunResult.DuplicateCall)
            return
        }
        startAttempted = true
        startReply = event.reply
        phase = EmbeddedWorkspaceRunnerPhase.PREFLIGHT

        when (val result = checkNotNull(preflight).prepare(event.request)) {
            is EmbeddedWorkspacePreflightResult.Rejected -> {
                val rejected = EmbeddedWorkspaceRunResult.PreflightRejected(result.rejection)
                phase = EmbeddedWorkspaceRunnerPhase.FAILED_CLEAN
                terminate(rejected)
            }

            is EmbeddedWorkspacePreflightResult.Prepared -> {
                preparedItems = result.items.sortedBy { it.planItem.order }
                phase = EmbeddedWorkspaceRunnerPhase.STARTING
                if (preparedItems.isEmpty()) {
                    val started = EmbeddedWorkspaceRunResult.Started(emptyList())
                    phase = EmbeddedWorkspaceRunnerPhase.ACTIVE
                    event.reply.complete(started)
                } else {
                    startNextItem()
                }
            }
        }
    }

    private fun handleStop(event: Event.Stop) {
        terminalResult?.let {
            event.reply.complete(it)
            return
        }
        if (cleanupActive) {
            terminalWaiters += event.reply
            return
        }
        if (phase == EmbeddedWorkspaceRunnerPhase.IDLE) {
            val stopped = EmbeddedWorkspaceRunResult.Stopped(emptyList(), emptyList())
            phase = EmbeddedWorkspaceRunnerPhase.STOPPED
            terminate(stopped)
            event.reply.complete(stopped)
            return
        }

        terminalWaiters += event.reply
        beginCleanup(CleanupReason.Stop)
    }

    private fun handleSurfaceLost(event: Event.SurfaceLost) {
        terminalResult?.let {
            event.reply.complete(it)
            return
        }
        if (cleanupActive) {
            terminalWaiters += event.reply
            return
        }

        terminalWaiters += event.reply
        beginCleanup(CleanupReason.SurfaceLoss(event.sourceCellId))
    }

    private fun handleTouch(event: Event.Touch) {
        val item = ownedItems[event.sourceCellId]
        if (item == null) {
            event.reply.complete(
                EmbeddedWorkspaceTouchResult.Rejected(event.sourceCellId, "UNKNOWN_SOURCE"),
            )
            return
        }
        if (item.latestSnapshot.phase != EmbeddedSessionPhase.ACTIVE) {
            event.reply.complete(
                EmbeddedWorkspaceTouchResult.Rejected(event.sourceCellId, "NOT_ACTIVE"),
            )
            return
        }

        val accepted = runCatching { item.handle.sendTouch(event.touch) }.getOrDefault(false)
        event.reply.complete(
            if (accepted) EmbeddedWorkspaceTouchResult.Accepted
            else EmbeddedWorkspaceTouchResult.Rejected(event.sourceCellId, "DELIVERY_REJECTED"),
        )
    }

    private fun handleSnapshot(event: Event.Snapshot) {
        if (terminalResult != null) return
        val item = ownedItems[event.sourceCellId] ?: return
        item.latestSnapshot = event.snapshot
        EmbeddedEvidence.observe { EmbeddedEvidence.app("runner.snapshot", item.handle.sessionId.value,
            event.sourceCellId, evidenceGraphId, buildMap {
                put("phase", event.snapshot.phase.name); put("display_id", event.snapshot.displayId.toString())
                event.snapshot.failure?.let { put("failure_code", it.code); it.message?.let { message -> put("message", message) } }
            }) }

        if (cleanupActive) {
            if (event.sourceCellId == currentCleanupSourceId && event.snapshot.phase.isCleanupTerminal()) {
                completeCurrentCleanup(event.sourceCellId, event.snapshot)
            }
            return
        }

        if (event.snapshot.phase == EmbeddedSessionPhase.REMOTE_DIED) {
            beginCleanup(CleanupReason.Recovery(event.sourceCellId))
            return
        }

        if (phase != EmbeddedWorkspaceRunnerPhase.STARTING) return

        if (event.snapshot.phase == EmbeddedSessionPhase.FAILED ||
            event.snapshot.phase == EmbeddedSessionPhase.CLEANUP_INCOMPLETE
        ) {
            val failure = event.snapshot.failure
                ?: EmbeddedSessionFailure("SESSION_FAILED", "Embedded session failed")
            beginCleanup(CleanupReason.StartFailure(event.sourceCellId, failure))
            return
        }

        if (event.sourceCellId != currentStartupSourceId) return

        when (event.snapshot.phase) {
            EmbeddedSessionPhase.READY -> onReady(item)
            EmbeddedSessionPhase.ACTIVE -> onActive(item, event.snapshot)
            EmbeddedSessionPhase.STOPPED -> beginCleanup(
                CleanupReason.StartFailure(
                    event.sourceCellId,
                    EmbeddedSessionFailure("SESSION_STOPPED", "Session stopped during startup"),
                ),
            )
            else -> Unit
        }
    }

    private fun handlePhaseTimeout(event: Event.PhaseTimeout) {
        if (cleanupActive || phase != EmbeddedWorkspaceRunnerPhase.STARTING) return
        if (event.token != phaseWaitToken ||
            event.kind != phaseWaitKind ||
            event.sourceCellId != phaseWaitSourceId
        ) return

        clearPhaseWait()
        val failure = when (event.kind) {
            PhaseWaitKind.READY -> EmbeddedSessionFailure("READY_TIMEOUT", "Timed out waiting for READY")
            PhaseWaitKind.ACTIVE -> EmbeddedSessionFailure("ACTIVE_TIMEOUT", "Timed out waiting for ACTIVE")
        }
        beginCleanup(CleanupReason.StartFailure(event.sourceCellId, failure))
    }

    private fun handleCleanupTimeout(event: Event.CleanupTimeout) {
        if (!cleanupActive) return
        if (event.token != cleanupWaitToken || event.sourceCellId != currentCleanupSourceId) return

        cleanupWaitJob = null
        val item = ownedItems[event.sourceCellId] ?: return
        val outcome = if (item.latestSnapshot.phase == EmbeddedSessionPhase.REMOTE_DIED) {
            EmbeddedWorkspaceCleanupOutcome.RecoveryRequired(
                event.sourceCellId,
                item.latestSnapshot.failure,
            )
        } else {
            EmbeddedWorkspaceCleanupOutcome.Incomplete(
                event.sourceCellId,
                EmbeddedSessionFailure("CLEANUP_TIMEOUT", "Timed out waiting for cleanup"),
            )
        }
        finishCurrentCleanup(outcome)
    }

    private fun startNextItem() {
        if (cleanupActive || phase != EmbeddedWorkspaceRunnerPhase.STARTING) return
        if (nextStartIndex >= preparedItems.size) {
            currentStartupSourceId = null
            phase = EmbeddedWorkspaceRunnerPhase.ACTIVE
            startReply?.complete(EmbeddedWorkspaceRunResult.Started(activeReceipts()))
            EmbeddedResultEvidence.record(EmbeddedWorkspaceRunResult.Started(activeReceipts()), evidenceGraphId)
            return
        }

        val prepared = preparedItems[nextStartIndex]
        val sourceCellId = prepared.planItem.sourceCellId
        currentStartupSourceId = sourceCellId

        val handle = try {
            checkNotNull(sessionFactory).create(prepared.target) { snapshot ->
                admit(Event.Snapshot(sourceCellId, snapshot))
            }
        } catch (failure: Exception) {
            EmbeddedEvidence.observe { EmbeddedEvidence.app("runner.create.failure", cell = sourceCellId,
                graph = evidenceGraphId, fields = EmbeddedEvidence.errorFields(failure)) }
            beginCleanup(
                CleanupReason.StartFailure(
                    sourceCellId,
                    EmbeddedSessionFailure("CREATE_FAILED", failure.message),
                ),
            )
            return
        }

        val item = OwnedItem(prepared, handle)
        ownedItems[sourceCellId] = item
        EmbeddedEvidence.observe { EmbeddedEvidence.app("session.link", handle.sessionId.value, sourceCellId,
            evidenceGraphId, mapOf("surface_identity" to System.identityHashCode(prepared.executionSurface).toString(),
                "surface_valid" to prepared.executionSurface.isValid.toString())) }

        schedulePhaseTimeout(sourceCellId, PhaseWaitKind.READY)
        try {
            handle.connect()
        } catch (failure: Exception) {
            EmbeddedEvidence.observe { EmbeddedEvidence.app("runner.connect.failure", handle.sessionId.value,
                sourceCellId, evidenceGraphId, EmbeddedEvidence.errorFields(failure)) }
            beginCleanup(
                CleanupReason.StartFailure(
                    sourceCellId,
                    EmbeddedSessionFailure("CONNECT_FAILED", failure.message),
                ),
            )
            return
        }
    }

    private fun onReady(item: OwnedItem) {
        clearPhaseWait()
        val sourceCellId = item.prepared.planItem.sourceCellId
        if (!item.prepared.executionSurface.isValid) {
            beginCleanup(
                CleanupReason.StartFailure(
                    sourceCellId,
                    EmbeddedSessionFailure("SURFACE_INVALID", "Execution surface is no longer valid"),
                ),
            )
            return
        }

        try {
            item.handle.start(item.prepared.executionSurface)
        } catch (failure: Exception) {
            EmbeddedEvidence.observe { EmbeddedEvidence.app("runner.start.failure", item.handle.sessionId.value,
                sourceCellId, evidenceGraphId, EmbeddedEvidence.errorFields(failure)) }
            beginCleanup(
                CleanupReason.StartFailure(
                    sourceCellId,
                    EmbeddedSessionFailure("START_COMMAND_FAILED", failure.message),
                ),
            )
            return
        }
        schedulePhaseTimeout(sourceCellId, PhaseWaitKind.ACTIVE)
    }

    private fun onActive(item: OwnedItem, snapshot: EmbeddedSessionSnapshot) {
        clearPhaseWait()
        item.activeReceipt = item.toReceipt(snapshot)
        nextStartIndex++
        currentStartupSourceId = null
        startNextItem()
    }

    private fun schedulePhaseTimeout(sourceCellId: String, kind: PhaseWaitKind) {
        phaseWaitJob?.cancel()
        val token = ++phaseWaitToken
        phaseWaitKind = kind
        phaseWaitSourceId = sourceCellId
        val timeout = when (kind) {
            PhaseWaitKind.READY -> timeoutPolicy.readyTimeout
            PhaseWaitKind.ACTIVE -> timeoutPolicy.activeTimeout
        }
        phaseWaitJob = checkNotNull(runnerScope).launch {
            delay(timeout)
            admit(Event.PhaseTimeout(sourceCellId, kind, token))
        }
    }

    private fun clearPhaseWait() {
        phaseWaitJob?.cancel()
        phaseWaitJob = null
        phaseWaitKind = null
        phaseWaitSourceId = null
    }

    private fun beginCleanup(reason: CleanupReason) {
        if (cleanupActive || terminalResult != null) return
        cleanupActive = true
        cleanupReason = reason
        clearPhaseWait()
        phase = when (reason) {
            is CleanupReason.StartFailure, is CleanupReason.SurfaceLoss -> EmbeddedWorkspaceRunnerPhase.ROLLING_BACK
            CleanupReason.Stop, is CleanupReason.Recovery -> EmbeddedWorkspaceRunnerPhase.STOPPING
        }
        EmbeddedEvidence.app("runner.rollback.begin", graph = evidenceGraphId, fields = mapOf("kind" to reason.javaClass.simpleName))
        cleanupOrder = ownedItems.keys.toList().asReversed()
        cleanupIndex = 0
        cleanupOutcomes.clear()
        cleanupNext()
    }

    private fun cleanupNext() {
        if (!cleanupActive) return
        if (cleanupIndex >= cleanupOrder.size) {
            finishCleanup()
            return
        }

        val sourceCellId = cleanupOrder[cleanupIndex]
        val item = ownedItems.getValue(sourceCellId)
        currentCleanupSourceId = sourceCellId

        if (item.latestSnapshot.phase == EmbeddedSessionPhase.REMOTE_DIED) {
            finishCurrentCleanup(
                EmbeddedWorkspaceCleanupOutcome.RecoveryRequired(
                    sourceCellId,
                    item.latestSnapshot.failure,
                ),
            )
            return
        }

        val token = ++cleanupWaitToken
        cleanupWaitJob = checkNotNull(runnerScope).launch {
            delay(timeoutPolicy.cleanupTimeout)
            admit(Event.CleanupTimeout(sourceCellId, token))
        }

        EmbeddedEvidence.observe { EmbeddedEvidence.app("runner.rollback.step", item.handle.sessionId.value,
            sourceCellId, evidenceGraphId, mapOf("step" to "stop", "state" to "begin")) }
        runCatching { item.handle.stop() }
            .onSuccess { EmbeddedEvidence.observe { EmbeddedEvidence.app("runner.rollback.step", item.handle.sessionId.value,
                sourceCellId, evidenceGraphId, mapOf("step" to "stop", "state" to "returned")) } }
            .onFailure { error -> EmbeddedEvidence.observe { EmbeddedEvidence.app("runner.rollback.failure", item.handle.sessionId.value,
                sourceCellId, evidenceGraphId, EmbeddedEvidence.errorFields(error) + ("step" to "stop")) } }
        runCatching { item.handle.close() }
            .onSuccess { EmbeddedEvidence.observe { EmbeddedEvidence.app("runner.rollback.step", item.handle.sessionId.value,
                sourceCellId, evidenceGraphId, mapOf("step" to "close", "state" to "returned")) } }
            .onFailure { error -> EmbeddedEvidence.observe { EmbeddedEvidence.app("runner.rollback.failure", item.handle.sessionId.value,
                sourceCellId, evidenceGraphId, EmbeddedEvidence.errorFields(error) + ("step" to "close")) } }
    }

    private fun completeCurrentCleanup(sourceCellId: String, snapshot: EmbeddedSessionSnapshot) {
        val outcome = when (snapshot.phase) {
            EmbeddedSessionPhase.STOPPED -> EmbeddedWorkspaceCleanupOutcome.Clean(sourceCellId)
            EmbeddedSessionPhase.REMOTE_DIED -> EmbeddedWorkspaceCleanupOutcome.RecoveryRequired(
                sourceCellId,
                snapshot.failure,
            )
            EmbeddedSessionPhase.CLEANUP_INCOMPLETE,
            EmbeddedSessionPhase.FAILED,
            -> EmbeddedWorkspaceCleanupOutcome.Incomplete(sourceCellId, snapshot.failure)
            else -> return
        }
        finishCurrentCleanup(outcome)
    }

    private fun finishCurrentCleanup(outcome: EmbeddedWorkspaceCleanupOutcome) {
        cleanupWaitJob?.cancel()
        cleanupWaitJob = null
        cleanupOutcomes += outcome
        currentCleanupSourceId = null
        cleanupIndex++
        cleanupNext()
    }

    private fun finishCleanup() {
        cleanupWaitJob?.cancel()
        cleanupWaitJob = null
        cleanupActive = false
        currentCleanupSourceId = null

        val reason = cleanupReason ?: CleanupReason.Stop
        val receipts = activeReceipts()
        val outcomes = cleanupOutcomes.toList()
        val result = when (reason) {
            CleanupReason.Stop -> stopResult(receipts, outcomes)
            is CleanupReason.Recovery -> EmbeddedWorkspaceRunResult.RecoveryRequired(
                sourceCellId = reason.sourceCellId,
                receipts = receipts,
                cleanupOutcomes = outcomes,
            )
            is CleanupReason.SurfaceLoss -> {
                if (outcomes.any { it is EmbeddedWorkspaceCleanupOutcome.RecoveryRequired }) {
                    EmbeddedWorkspaceRunResult.RecoveryRequired(
                        sourceCellId = reason.sourceCellId,
                        receipts = receipts,
                        cleanupOutcomes = outcomes,
                    )
                } else {
                    EmbeddedWorkspaceRunResult.StartFailed(
                        sourceCellId = reason.sourceCellId,
                        failure = EmbeddedSessionFailure("SURFACE_LOST", "Execution surface was lost"),
                        receipts = receipts.filterNot { it.sourceCellId == reason.sourceCellId },
                        partialReceipt = partialReceipt(reason.sourceCellId),
                        rollbackOutcomes = outcomes,
                        allOwnedSessionsClean = outcomes.size == ownedItems.size &&
                            outcomes.all { it is EmbeddedWorkspaceCleanupOutcome.Clean },
                    )
                }
            }
            is CleanupReason.StartFailure -> EmbeddedWorkspaceRunResult.StartFailed(
                sourceCellId = reason.sourceCellId,
                failure = reason.failure,
                receipts = receipts.filterNot { it.sourceCellId == reason.sourceCellId },
                partialReceipt = partialReceipt(reason.sourceCellId),
                rollbackOutcomes = outcomes,
                allOwnedSessionsClean = outcomes.size == ownedItems.size &&
                    outcomes.all { it is EmbeddedWorkspaceCleanupOutcome.Clean },
            )
        }

        phase = when (result) {
            is EmbeddedWorkspaceRunResult.Stopped -> EmbeddedWorkspaceRunnerPhase.STOPPED
            is EmbeddedWorkspaceRunResult.RecoveryRequired -> EmbeddedWorkspaceRunnerPhase.RECOVERY_REQUIRED
            else -> EmbeddedWorkspaceRunnerPhase.FAILED_CLEAN
        }
        terminate(result)
    }

    private fun terminate(result: EmbeddedWorkspaceRunResult) {
        EmbeddedResultEvidence.record(result, evidenceGraphId)
        // The result is already a complete value copy. Close admission before detaching handles.
        synchronized(admissionLock) {
            terminalSources = ownedItems.keys.toSet()
            terminalResult = result
            events.close()
        }
        ownedItems.values.forEach { runCatching { it.handle.detachNotifications() } }
        startReply?.let { if (!it.isCompleted) it.complete(result) }
        startReply = null
        terminalWaiters.forEach { if (!it.isCompleted) it.complete(result) }
        terminalWaiters.clear()
        while (true) resolveTerminal(events.tryReceive().getOrNull() ?: break, result)
        preparedItems = emptyList()
        ownedItems.clear()
        nextStartIndex = 0
        currentStartupSourceId = null
        cleanupActive = false
        cleanupReason = null
        cleanupOrder = emptyList()
        cleanupIndex = 0
        currentCleanupSourceId = null
        cleanupOutcomes.clear()
        clearPhaseWait()
        phaseWaitToken++
        cleanupWaitJob?.cancel()
        cleanupWaitJob = null
        cleanupWaitToken++
        preflight = null
        sessionFactory = null
        val terminatingScope = runnerScope
        runnerScope = null
        terminatingScope?.coroutineContext?.get(Job)?.cancel()
    }

    private fun stopResult(
        receipts: List<EmbeddedWorkspaceItemReceipt>,
        outcomes: List<EmbeddedWorkspaceCleanupOutcome>,
    ): EmbeddedWorkspaceRunResult = when {
        outcomes.any { it is EmbeddedWorkspaceCleanupOutcome.RecoveryRequired } ->
            EmbeddedWorkspaceRunResult.RecoveryRequired(
                sourceCellId = outcomes.firstOrNull {
                    it is EmbeddedWorkspaceCleanupOutcome.RecoveryRequired
                }?.sourceCellId,
                receipts = receipts,
                cleanupOutcomes = outcomes,
            )
        outcomes.any { it is EmbeddedWorkspaceCleanupOutcome.Incomplete } ->
            EmbeddedWorkspaceRunResult.CleanupIncomplete(receipts, outcomes)
        else -> EmbeddedWorkspaceRunResult.Stopped(receipts, outcomes)
    }

    private fun activeReceipts(): List<EmbeddedWorkspaceItemReceipt> =
        ownedItems.values.mapNotNull { it.activeReceipt }.sortedBy { it.order }

    private fun partialReceipt(sourceCellId: String): EmbeddedWorkspaceItemReceipt? =
        ownedItems[sourceCellId]?.let { it.toReceipt(it.latestSnapshot) }

    private data class OwnedItem(
        val prepared: EmbeddedWorkspacePreparedItem,
        val handle: EmbeddedWorkspaceSessionHandle,
        var latestSnapshot: EmbeddedSessionSnapshot = EmbeddedSessionSnapshot(EmbeddedSessionPhase.IDLE),
        var activeReceipt: EmbeddedWorkspaceItemReceipt? = null,
    ) {
        fun toReceipt(snapshot: EmbeddedSessionSnapshot) = EmbeddedWorkspaceItemReceipt(
            sourceCellId = prepared.planItem.sourceCellId,
            sessionId = handle.sessionId,
            packageName = prepared.planItem.packageName,
            componentName = prepared.planItem.componentName,
            order = prepared.planItem.order,
            phase = snapshot.phase,
            displayId = snapshot.displayId,
        )
    }

    private sealed interface CleanupReason {
        data object Stop : CleanupReason
        data class StartFailure(
            val sourceCellId: String,
            val failure: EmbeddedSessionFailure,
        ) : CleanupReason
        data class SurfaceLoss(val sourceCellId: String) : CleanupReason
        data class Recovery(val sourceCellId: String) : CleanupReason
    }

    private enum class PhaseWaitKind { READY, ACTIVE }

    private sealed interface Event {
        data class Start(
            val request: EmbeddedWorkspaceExecutionRequest,
            val reply: CompletableDeferred<EmbeddedWorkspaceRunResult>,
        ) : Event

        data class Stop(val reply: CompletableDeferred<EmbeddedWorkspaceRunResult>) : Event

        data class SurfaceLost(
            val sourceCellId: String,
            val reply: CompletableDeferred<EmbeddedWorkspaceRunResult>,
        ) : Event

        data class Touch(
            val sourceCellId: String,
            val touch: EmbeddedTouchEvent,
            val reply: CompletableDeferred<EmbeddedWorkspaceTouchResult>,
        ) : Event

        data class Snapshot(
            val sourceCellId: String,
            val snapshot: EmbeddedSessionSnapshot,
        ) : Event

        data class PhaseTimeout(
            val sourceCellId: String,
            val kind: PhaseWaitKind,
            val token: Long,
        ) : Event

        data class CleanupTimeout(
            val sourceCellId: String,
            val token: Long,
        ) : Event
    }

    private fun EmbeddedSessionPhase.isCleanupTerminal(): Boolean = this in setOf(
        EmbeddedSessionPhase.STOPPED,
        EmbeddedSessionPhase.FAILED,
        EmbeddedSessionPhase.CLEANUP_INCOMPLETE,
        EmbeddedSessionPhase.REMOTE_DIED,
    )
}
