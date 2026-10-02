package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppSessionId
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionFailure
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionPhase
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceCleanupOutcome
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceItemReceipt
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmbeddedCleanupBlockedUxTest {
    @Test fun cleanupIncompleteHasDistinctUnconfirmedReason() {
        val ui = blocked(incomplete()).ui()
        assertEquals(EmbeddedCleanupBlockedCause.INCOMPLETE, ui.cause)
        assertTrue(ui.reason.contains("chưa thể xác nhận", ignoreCase = true))
        assertEquals(CleanupEvidence.INCOMPLETE, ui.evidence.cleanupEvidence)
    }

    @Test fun recoveryRequiredExplainsLostConnectionAndUnresolvedCleanup() {
        val ui = blocked(EmbeddedWorkspaceRunResult.RecoveryRequired(sourceCellId = "cell", receipts = listOf(receipt()), cleanupOutcomes = listOf(
            EmbeddedWorkspaceCleanupOutcome.RecoveryRequired("cell", EmbeddedSessionFailure("REMOTE_DIED", "private detail"))
        ))).ui()
        assertEquals(EmbeddedCleanupBlockedCause.REMOTE_DIED, ui.cause)
        assertTrue(ui.reason.contains("kết nối", ignoreCase = true))
        assertTrue(ui.reason.contains("kết quả dọn"))
        assertEquals(CleanupEvidence.UNCERTAIN, ui.evidence.cleanupEvidence)
    }

    @Test fun recoveryRequiredWithoutRemoteDeathDoesNotInventConnectionLoss() {
        val ui = blocked(EmbeddedWorkspaceRunResult.RecoveryRequired(sourceCellId = null, receipts = emptyList(), cleanupOutcomes = emptyList())).ui()
        assertEquals(EmbeddedCleanupBlockedCause.RECOVERY_REQUIRED, ui.cause)
        assertTrue(ui.reason.contains("kiểm tra thêm"))
        assertFalse(ui.reason.contains("mất kết nối", ignoreCase = true))
    }

    @Test fun uncertainResultHasSafeReasonWithoutInventedSuccess() {
        val ui = blocked(null).ui()
        assertEquals(EmbeddedCleanupBlockedCause.UNCERTAIN, ui.cause)
        assertTrue(ui.reason.contains("chưa rõ", ignoreCase = true))
        assertNoSuccessClaim(ui)
    }

    @Test fun cleanupTimeoutUsesCopiedCodeAndRetainsUnresolvedEvidence() {
        val ui = blocked(incomplete("CLEANUP_TIMEOUT")).ui()
        assertEquals(EmbeddedCleanupBlockedCause.TIMEOUT, ui.cause)
        assertTrue(ui.reason.contains("Quá thời gian"))
        assertTrue(ui.reason.contains("CLEANUP_TIMEOUT"))
        assertEquals("CLEANUP_TIMEOUT", ui.evidence.cleanupOutcomes.single().failureCode)
        assertFalse(ui.reason.contains("private detail"))
        assertNoSuccessClaim(ui)
    }

    @Test fun noEmbeddedStartIsOfferedOrAdmitted() {
        val f = blocked(incomplete())
        assertFalse(f.ui().permittedActions.any { it.name == "START_EMBEDDED" })
        assertNull(f.gate.tryAcquireEmbedded("new-run"))
        assertFalse(f.gate.canEnterEmbedded())
    }

    @Test fun noClassicActionIsOfferedOrDispatched() {
        val f = blocked(incomplete())
        assertFalse(f.ui().permittedActions.any { it.name == "OPEN_CLASSIC" })
        assertFalse(f.gate.tryDispatchClassic { fail("Classic must remain blocked") })
    }

    @Test fun noRetryCleanupReconcileResetOrForceStopActionIsOffered() {
        val actions = blocked(incomplete()).ui().permittedActions.map { it.name }.toSet()
        assertTrue(actions.intersect(setOf("RETRY", "CLEANUP", "RECONCILE", "RESET", "FORCE_STOP", "GLOBAL_SCAN")).isEmpty())
    }

    @Test fun noManualUnlockOrFixedActionIsOffered() {
        val f = blocked(incomplete())
        assertFalse(f.ui().permittedActions.any { it.name in setOf("UNLOCK", "FIXED", "RELEASE_GATE") })
        assertFalse(f.gate.releaseWithoutAllocation(ProductNoAllocationProof(f.start)))
        assertNoSuccessClaim(f.ui())
    }

    @Test fun explicitReadinessRefreshCannotReleaseGateOrReplaceBlockedEvidence() {
        val f = blocked(incomplete("CLEANUP_TIMEOUT"))
        val before = f.ui()
        val callbacks = Callbacks()
        val refresh = refresh(f.gate, callbacks) { EmbeddedCapabilitySnapshot(true, true, true) }
        try {
            refresh.attach()
            refresh.refresh()
            assertEquals(EmbeddedReadinessResult.Ready, refresh.state.value?.readiness)
            assertEquals(before, f.ui())
            assertFalse(f.ui().permittedActions.any { it.name == "REFRESH_READINESS" })
            assertFalse(f.gate.canEnterEmbedded())
            assertFalse(f.gate.tryDispatchClassic { fail("refresh cannot unlock Classic") })
        } finally { refresh.dispose() }
    }

    @Test fun shizukuAndResumeChangesOnlyReadinessWhileBlocked() {
        val f = blocked(incomplete())
        val before = f.ui()
        var capability = EmbeddedCapabilitySnapshot(true, false, false)
        val callbacks = Callbacks()
        val refresh = refresh(f.gate, callbacks) { capability }
        try {
            refresh.attach()
            assertEquals(EmbeddedReadinessResult.ShizukuUnavailable, refresh.state.value?.readiness)
            capability = EmbeddedCapabilitySnapshot(true, true, true)
            callbacks.received!!()
            callbacks.permission!!(EMBEDDED_SHIZUKU_PERMISSION_REQUEST_CODE, true)
            refresh.onResume()
            assertEquals(EmbeddedReadinessResult.Ready, refresh.state.value?.readiness)
            assertFalse(refresh.requestPermission())
            capability = EmbeddedCapabilitySnapshot(true, false, false)
            callbacks.dead!!()
            assertEquals(before, f.ui())
            assertEquals(ProductRunPhase.CLEANUP_BLOCKED, f.gate.state.value)
            assertNull(f.gate.tryAcquireEmbedded("new"))
        } finally { refresh.dispose() }
    }

    @Test fun droppingOldControllerAndExecutionStillLeavesApplicationValueUx() = runTest {
        val gate = EmbeddedProductRunGate()
        var starts = 0
        var closes = 0
        suspend fun oldRoute() {
            val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
            val controller = EmbeddedWorkspaceProductController("ws", gate,
                EmbeddedCapabilityProbe { EmbeddedCapabilitySnapshot(true, true, true) },
                { ProductHostReadiness(true, true) }, object : EmbeddedProductExecution {
                    override suspend fun start(): EmbeddedWorkspaceRunResult { starts++; return EmbeddedWorkspaceRunResult.Started(listOf(receipt())) }
                    override suspend fun close(): EmbeddedWorkspaceRunResult { closes++; return incomplete("CLEANUP_TIMEOUT") }
                }, scope)
            controller.start()
            controller.onHostDisposed().join()
            assertTrue(scope.coroutineContext[Job]!!.isCompleted)
            for (name in listOf("execution", "hostReadiness", "backCallback")) {
                val field = controller.javaClass.getDeclaredField(name).apply { isAccessible = true }
                assertNull("old route reference: $name", field.get(controller))
            }
        }
        oldRoute()
        val ui = checkNotNull(gate.status.value.cleanupBlockedUi())
        assertTrue(ui.message.contains("đã kết thúc giao diện chạy cũ"))
        assertValueGraph(ui)
        assertEquals(1, starts)
        assertEquals(1, closes)
    }

    @Test fun uiRecreationRendersIdenticalCopiedValueEvidence() {
        val f = blocked(incomplete("CLEANUP_TIMEOUT"))
        val old = f.ui()
        val recreated = checkNotNull(f.gate.status.value.cleanupBlockedUi())
        assertEquals(old, recreated)
        assertEquals(f.start.generation, recreated.evidence.generation)
        assertEquals(f.cleanup.operationId, recreated.evidence.cleanupOperationId)
        assertValueGraph(recreated)
    }

    @Test fun homeCanExposeBlockedReasonAndStatusNavigationWithOnlyValues() {
        val ui = blocked(incomplete()).ui()
        assertTrue(EmbeddedCleanupBlockedAction.VIEW_STATUS in ui.permittedActions)
        assertEquals("ws", ui.evidence.token?.workspaceId)
        assertTrue(ui.message.contains("Classic"))
        assertValueGraph(ui)
    }

    @Test fun safeNavigationAwayLeavesGateAndEvidenceBlocked() {
        val f = blocked(incomplete())
        val before = f.gate.status.value
        val screen = f.ui()
        assertTrue(EmbeddedCleanupBlockedAction.BACK in screen.permittedActions)
        // Callback điều hướng của host không nhận gate/runtime; chỉ đổi vị trí màn hình.
        var route = "embedded"
        val navigateBack = { route = "home" }
        navigateBack()
        assertEquals("home", route)
        assertSame(before, f.gate.status.value)
        assertEquals(ProductRunPhase.CLEANUP_BLOCKED, f.gate.state.value)
    }

    @Test fun returningFromHomeShowsSameBlockedStatusWithoutStartingRun() {
        val f = blocked(incomplete("CLEANUP_TIMEOUT"))
        val before = f.ui()
        assertTrue(EmbeddedCleanupBlockedAction.VIEW_STATUS in before.permittedActions)
        val home = checkNotNull(f.gate.status.value.cleanupBlockedUi())
        val returned = checkNotNull(f.gate.status.value.cleanupBlockedUi())
        assertEquals(before, home)
        assertEquals(before, returned)
        assertNull(f.gate.tryAcquireEmbedded("ws"))
    }

    @Test fun blockedProjectionAndRecreatedAdmissionAllocateNothing() {
        val f = blocked(incomplete())
        var controllers = 0
        var executions = 0
        var surfaces = 0
        var cleanupCalls = 0
        var tokens = 0
        val generation = f.gate.status.value.generation
        repeat(3) {
            assertNotNull(f.gate.status.value.cleanupBlockedUi())
            assertNull(f.gate.createExecutionIfIdle {
                controllers++; executions++; surfaces++
                object : EmbeddedProductExecution {
                    override suspend fun start() = EmbeddedWorkspaceRunResult.Started(emptyList())
                    override suspend fun close(): EmbeddedWorkspaceRunResult { cleanupCalls++; return incomplete() }
                }
            })
            if (f.gate.tryAcquireEmbedded("conflict") != null) tokens++
        }
        assertEquals(listOf(0, 0, 0, 0, 0), listOf(controllers, executions, surfaces, tokens, cleanupCalls))
        assertEquals(generation, f.gate.status.value.generation)
    }

    @Test fun lateStartCallbackCannotClearOrReplaceBlockedUx() {
        val f = blocked(incomplete("CLEANUP_TIMEOUT"))
        val before = f.ui()
        for (result in listOf(EmbeddedWorkspaceRunResult.Started(listOf(receipt())), incomplete("OTHER"))) {
            assertFalse(f.gate.acceptResult(f.start, ProductExecutionValue.from(result)))
            assertEquals(before, f.ui())
        }
    }

    @Test fun cleanFromStaleOldOperationCannotReleaseCurrentBlockedRun() {
        val gate = EmbeddedProductRunGate()
        val old = invoke(gate, "ws")
        val oldCleanup = gate.markStopping(old)!!
        assertTrue(gate.acceptResult(oldCleanup, ProductExecutionValue.from(clean())))
        val f = blocked(incomplete(), gate)
        val before = f.ui()
        for (stale in listOf(oldCleanup, f.cleanup.copy(generation = old.generation),
            f.cleanup.copy(operationId = oldCleanup.operationId), f.cleanup.copy(token = old.token))) {
            assertFalse(gate.acceptResult(stale, ProductExecutionValue.from(clean())))
            assertEquals(before, f.ui())
        }
    }

    @Test fun repeatedCleanForExactCompletedCleanupCannotRetroactivelyUnlock() {
        val f = blocked(incomplete())
        val before = f.ui()
        assertFalse(f.gate.acceptResult(f.cleanup, ProductExecutionValue.from(clean())))
        assertEquals(before, f.ui())
    }

    @Test fun missingProvenanceShowsUnknownAndDoesNotFabricateIdentifiers() {
        val ui = checkNotNull(ProductRunStatus(phase = ProductRunPhase.CLEANUP_BLOCKED,
            allocationEvidence = AllocationEvidence.UNKNOWN, cleanupEvidence = CleanupEvidence.UNCERTAIN).cleanupBlockedUi())
        assertEquals(EmbeddedCleanupBlockedCause.UNKNOWN, ui.cause)
        assertTrue(ui.reason.contains("chưa rõ", ignoreCase = true))
        assertNull(ui.evidence.token)
        assertNull(ui.evidence.startOperationId)
        assertNull(ui.evidence.cleanupOperationId)
        assertTrue(ui.evidence.items.isEmpty())
        assertTrue(ui.evidence.cleanupOutcomes.isEmpty())
        assertEquals(setOf(EmbeddedCleanupBlockedAction.BACK), ui.permittedActions)
    }

    @Test fun actionModelContainsOnlySafeNavigation() {
        assertEquals(setOf(EmbeddedCleanupBlockedAction.BACK, EmbeddedCleanupBlockedAction.VIEW_STATUS),
            blocked(incomplete()).ui().permittedActions)
    }

    @Test fun copyExplainsLocalEndBothLaunchBlocksAndCurrentProcessBoundary() {
        val ui = blocked(incomplete()).ui()
        assertTrue(ui.message.contains("đã kết thúc giao diện chạy cũ"))
        assertTrue(ui.message.contains("Embedded mới và Classic đang bị chặn"))
        assertTrue(ui.scopeMessage.contains("DWT hiện tại"))
        assertTrue(ui.scopeMessage.contains("bằng chứng chưa được giải quyết"))
        assertTrue(ui.supportGuidance.contains("Không có thao tác thử dọn lại"))
        assertNoSuccessClaim(ui)
    }

    @Test fun typedIssueTakesPrecedenceOverUnrecognizedCode() {
        val ui = blocked(incomplete("UNRECOGNIZED_CODE")).ui()
        assertEquals(EmbeddedCleanupBlockedCause.INCOMPLETE, ui.cause)
        assertFalse(ui.reason.contains("UNRECOGNIZED_CODE"))
        assertEquals("UNRECOGNIZED_CODE", ui.evidence.cleanupOutcomes.single().failureCode)
    }

    @Test fun unknownCodeIsShownOnlyWhenTypedCauseIsMissing() {
        val ui = checkNotNull(ProductRunStatus(phase = ProductRunPhase.CLEANUP_BLOCKED,
            allocationEvidence = AllocationEvidence.UNKNOWN, cleanupEvidence = CleanupEvidence.UNCERTAIN,
            cleanupOutcomes = listOf(ProductCleanupStatus("recorded-cell", CleanupEvidence.UNCERTAIN, "OPAQUE_CODE"))).cleanupBlockedUi())
        assertEquals(EmbeddedCleanupBlockedCause.UNKNOWN, ui.cause)
        assertTrue(ui.reason.contains("chưa rõ", ignoreCase = true))
        assertTrue(ui.reason.contains("OPAQUE_CODE"))
        assertNoSuccessClaim(ui)
    }

    @Test fun timeoutRequiresUnresolvedOutcomeProvenance() {
        val f = blocked(incomplete())
        val status = f.gate.status.value.copy(cleanupOutcomes = listOf(
            ProductCleanupStatus("cell", CleanupEvidence.CLEAN_CONFIRMED, "CLEANUP_TIMEOUT")))
        val ui = checkNotNull(status.cleanupBlockedUi())
        assertEquals(EmbeddedCleanupBlockedCause.INCOMPLETE, ui.cause)
        assertFalse(ui.reason.contains("CLEANUP_TIMEOUT"))
    }

    @Test fun projectionCopiesListsAndItsWholeGraphContainsOnlyImmutableValues() {
        val f = blocked(incomplete("CLEANUP_TIMEOUT"))
        val items = f.gate.status.value.items.toMutableList()
        val outcomes = f.gate.status.value.cleanupOutcomes.toMutableList()
        val ui = checkNotNull(f.gate.status.value.copy(items = items, cleanupOutcomes = outcomes).cleanupBlockedUi())
        items.clear(); outcomes.clear()
        assertEquals("session", ui.evidence.items.single().sessionId)
        assertEquals("CLEANUP_TIMEOUT", ui.evidence.cleanupOutcomes.single().failureCode)
        assertValueGraph(ui)
        try { (ui.evidence.cleanupOutcomes as MutableList).clear(); fail("must be immutable") }
        catch (_: UnsupportedOperationException) { }
        try { (ui.permittedActions as MutableSet).clear(); fail("must be immutable") }
        catch (_: UnsupportedOperationException) { }
    }

    @Test fun otherPhasesDoNotRenderTerminalBlockedCopy() {
        for (phase in ProductRunPhase.entries.filter { it != ProductRunPhase.CLEANUP_BLOCKED }) {
            assertNull(ProductRunStatus(phase = phase).cleanupBlockedUi())
        }
    }

    @Test fun retainedSurfaceLostIssueDoesNotDescribeOldRunAsStillEnding() {
        val status = blocked(incomplete()).gate.status.value.copy(issue = EmbeddedProductIssue.SurfaceLost("cell"))
        val ui = checkNotNull(status.cleanupBlockedUi())
        assertTrue(ui.reason.contains("hiển thị"))
        assertTrue(ui.reason.contains("kết quả dọn"))
        assertFalse(ui.reason.contains("đang kết thúc"))
        assertTrue(ui.message.contains("đã kết thúc giao diện chạy cũ"))
    }

    private data class Blocked(val gate: EmbeddedProductRunGate, val start: ProductRunOperation, val cleanup: ProductRunOperation) {
        fun ui() = checkNotNull(gate.status.value.cleanupBlockedUi())
    }

    private fun blocked(result: EmbeddedWorkspaceRunResult?, gate: EmbeddedProductRunGate = EmbeddedProductRunGate()): Blocked {
        val start = invoke(gate, "ws")
        val cleanup = gate.markStopping(start)!!
        assertTrue(gate.acceptResult(cleanup, ProductExecutionValue.from(result)))
        return Blocked(gate, start, cleanup)
    }

    private fun invoke(gate: EmbeddedProductRunGate, workspaceId: String): ProductRunOperation =
        gate.startOperation(gate.tryAcquireEmbedded(workspaceId)!!)!!.also { assertTrue(gate.markInvoked(it)) }

    private fun receipt() = EmbeddedWorkspaceItemReceipt("cell", EmbeddedAppSessionId("session"),
        "pkg", "pkg.Main", 0, EmbeddedSessionPhase.ACTIVE, 10)

    private fun incomplete(code: String? = null) = EmbeddedWorkspaceRunResult.CleanupIncomplete(listOf(receipt()),
        listOf(EmbeddedWorkspaceCleanupOutcome.Incomplete("cell", code?.let { EmbeddedSessionFailure(it, "private detail") })))

    private fun clean() = EmbeddedWorkspaceRunResult.Stopped(emptyList(), emptyList())

    private fun assertNoSuccessClaim(ui: EmbeddedCleanupBlockedUi) {
        val copy = listOf(ui.title, ui.message, ui.reason, ui.scopeMessage, ui.supportGuidance).joinToString(" ").lowercase()
        for (claim in listOf("đã dọn xong", "đã sửa", "không còn tài nguyên", "an toàn để chạy lại")) {
            assertFalse("false cleanup claim: $claim", copy.contains(claim))
        }
    }

    private fun refresh(gate: EmbeddedProductRunGate, callbacks: Callbacks, capability: () -> EmbeddedCapabilitySnapshot) =
        EmbeddedShizukuRefresh(EmbeddedCapabilityProbe { capability() }, callbacks, { ProductHostReadiness(true, true) }, {
            val status = gate.status.value
            EmbeddedProductRecoveryMapper.snapshot(status.phase, gate.canEnterEmbedded(), status.issue,
                status.allocationEvidence, status.cleanupEvidence)
        })

    private class Callbacks : EmbeddedShizukuCallbacks {
        var received: (() -> Unit)? = null
        var dead: (() -> Unit)? = null
        var permission: ((Int, Boolean) -> Unit)? = null
        override fun listenBinderReceived(listener: () -> Unit): AutoCloseable { received = listener; return AutoCloseable { received = null } }
        override fun listenBinderDead(listener: () -> Unit): AutoCloseable { dead = listener; return AutoCloseable { dead = null } }
        override fun listenPermissionResult(listener: (Int, Boolean) -> Unit): AutoCloseable { permission = listener; return AutoCloseable { permission = null } }
        override fun requestPermission(requestCode: Int) { fail("blocked UX cannot request permission") }
    }
}
