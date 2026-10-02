package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppSessionId
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionFailure
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionPhase
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceCleanupOutcome
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceItemReceipt
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspacePreflightRejection
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunResult
import com.trancong.dexworkspacetouch.workspace.launcher.model.LaunchReadiness
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmbeddedProductIssueTest {
    @Test fun eligibilityFailuresKeepDistinctIssuesAndOnlySafePreStartActions() {
        val cases = listOf(
            EmbeddedEligibilityFailure.MissingWorkspace("ws") to "MissingWorkspace",
            EmbeddedEligibilityFailure.LaunchNotReady(LaunchReadiness.EmptyWorkspace) to "LaunchNotReady",
            EmbeddedEligibilityFailure.UnsupportedLayout to "UnsupportedLayout",
            EmbeddedEligibilityFailure.UnsupportedEmbeddedItemCount(3) to "UnsupportedEmbeddedItemCount",
            EmbeddedEligibilityFailure.DuplicateTarget("pkg", "pkg.Main") to "DuplicateTarget",
            EmbeddedEligibilityFailure.GeometryUnavailable("cell") to "GeometryUnavailable",
        )
        for ((failure, code) in cases) {
            val recovery = EmbeddedProductRecoveryMapper.eligibility(failure, ProductRunPhase.IDLE, true)
            assertEquals(code, recovery.issue?.code)
            assertEquals(AllocationEvidence.NONE_CONFIRMED, recovery.allocationEvidence)
            assertEquals(CleanupEvidence.NOT_NEEDED, recovery.cleanupEvidence)
            assertTrue(recovery.permittedActions.contains(EmbeddedRecoveryAction.OPEN_CLASSIC))
            assertTrue(recovery.permittedActions.contains(EmbeddedRecoveryAction.BACK))
            assertFalse(recovery.permittedActions.contains(EmbeddedRecoveryAction.START_EMBEDDED))
        }
        assertEquals("cell", EmbeddedProductRecoveryMapper.eligibility(
            EmbeddedEligibilityFailure.GeometryUnavailable("cell"), ProductRunPhase.IDLE, true,
        ).issue?.sourceCellId)
    }

    @Test fun readinessFailuresKeepDistinctCodesAndPermissionActionIsSpecific() {
        val cases = listOf(
            EmbeddedReadinessResult.UnsupportedPlatform to "UnsupportedPlatform",
            EmbeddedReadinessResult.ShizukuUnavailable to "ShizukuUnavailable",
            EmbeddedReadinessResult.ShizukuPermissionMissing to "ShizukuPermissionMissing",
            EmbeddedReadinessResult.GeometryUnavailable to "GeometryUnavailable",
            EmbeddedReadinessResult.RendererNotReady to "RendererNotReady",
        )
        for ((reason, code) in cases) {
            val recovery = EmbeddedProductRecoveryMapper.readiness(reason, ProductRunPhase.IDLE, true)
            assertEquals(code, recovery.issue?.code)
            assertTrue(recovery.permittedActions.contains(EmbeddedRecoveryAction.OPEN_CLASSIC))
            assertFalse(recovery.permittedActions.contains(EmbeddedRecoveryAction.START_EMBEDDED))
            assertEquals(reason == EmbeddedReadinessResult.ShizukuPermissionMissing,
                recovery.permittedActions.contains(EmbeddedRecoveryAction.REQUEST_SHIZUKU_PERMISSION))
        }
        assertTrue(EmbeddedProductRecoveryMapper.readiness(
            EmbeddedReadinessResult.ShizukuUnavailable, ProductRunPhase.IDLE, true,
        ).permittedActions.contains(EmbeddedRecoveryAction.REFRESH_READINESS))
    }

    @Test fun capabilityReadyKeepsExperimentalCopyWithoutClaimingDeviceVerification() {
        val recovery = EmbeddedProductRecoveryMapper.readiness(EmbeddedReadinessResult.Ready, ProductRunPhase.IDLE, true)
        assertTrue(recovery.permittedActions.contains(EmbeddedRecoveryAction.START_EMBEDDED))
        assertTrue(recovery.message().contains("Thử nghiệm"))
        assertFalse(recovery.message().contains("Đã kiểm chứng"))
    }

    @Test fun noneConfirmedNeverPermitsClassicWhileAnyRunPhaseOwnsGate() {
        for (phase in listOf(ProductRunPhase.STARTING, ProductRunPhase.ACTIVE,
            ProductRunPhase.STOPPING, ProductRunPhase.CLEANUP_BLOCKED)) {
            for (cleanup in CleanupEvidence.entries) {
                val recovery = EmbeddedProductRecoveryMapper.snapshot(
                    phase, true, null, AllocationEvidence.NONE_CONFIRMED, cleanup,
                )
                assertFalse("$phase / $cleanup", recovery.permittedActions.contains(EmbeddedRecoveryAction.OPEN_CLASSIC))
            }
        }
    }

    @Test fun idleStillRequiresGateAvailabilityAndAuthoritativeEvidence() {
        val absent = EmbeddedProductRecoveryMapper.snapshot(ProductRunPhase.IDLE, true, null,
            AllocationEvidence.NONE_CONFIRMED, CleanupEvidence.NOT_NEEDED)
        assertTrue(absent.permittedActions.contains(EmbeddedRecoveryAction.OPEN_CLASSIC))
        assertFalse(EmbeddedProductRecoveryMapper.snapshot(ProductRunPhase.IDLE, false, null,
            AllocationEvidence.NONE_CONFIRMED, CleanupEvidence.NOT_NEEDED)
            .permittedActions.contains(EmbeddedRecoveryAction.OPEN_CLASSIC))
        for (allocation in listOf(AllocationEvidence.UNKNOWN, AllocationEvidence.POSSIBLE_OR_OWNED)) {
            for (cleanup in listOf(CleanupEvidence.NOT_NEEDED, CleanupEvidence.INCOMPLETE, CleanupEvidence.UNCERTAIN)) {
                assertFalse(EmbeddedProductRecoveryMapper.snapshot(ProductRunPhase.IDLE, true, null, allocation, cleanup)
                    .permittedActions.contains(EmbeddedRecoveryAction.OPEN_CLASSIC))
            }
        }
        assertFalse(EmbeddedProductRecoveryMapper.snapshot(ProductRunPhase.IDLE, true, null,
            AllocationEvidence.NONE_CONFIRMED, CleanupEvidence.UNCERTAIN)
            .permittedActions.contains(EmbeddedRecoveryAction.OPEN_CLASSIC))
    }

    @Test fun runtimeFailureCauseIsIndependentOfCleanVersusUncertainCleanup() {
        for ((failureCode, issueCode) in listOf(
            "START_COMMAND_FAILED" to "RuntimeStartFailed", "SURFACE_LOST" to "SurfaceLost", "REMOTE_DIED" to "RemoteDied",
        )) {
            val clean = EmbeddedProductRecoveryMapper.result(startFailed(failureCode, true), ProductRunPhase.IDLE, true)
            assertEquals(issueCode, clean.issue?.code)
            assertEquals("cell", clean.issue?.sourceCellId)
            assertEquals(CleanupEvidence.CLEAN_CONFIRMED, clean.cleanupEvidence)
            assertTrue(clean.permittedActions.contains(EmbeddedRecoveryAction.OPEN_CLASSIC))
            val uncertain = EmbeddedProductRecoveryMapper.result(startFailed(failureCode, false), ProductRunPhase.CLEANUP_BLOCKED, false)
            assertEquals(issueCode, uncertain.issue?.code)
            assertEquals(CleanupEvidence.UNCERTAIN, uncertain.cleanupEvidence)
            assertFalse(uncertain.permittedActions.contains(EmbeddedRecoveryAction.OPEN_CLASSIC))
            assertFalse(uncertain.message().contains("raw-secret-message"))
        }
    }

    @Test fun cleanResultCannotOfferClassicUntilGateReturnsIdleWithoutOwner() {
        val result = startFailed("SURFACE_LOST", true)
        for (phase in ProductRunPhase.entries.filter { it != ProductRunPhase.IDLE }) {
            assertFalse(EmbeddedProductRecoveryMapper.result(result, phase, true)
                .permittedActions.contains(EmbeddedRecoveryAction.OPEN_CLASSIC))
        }
        assertFalse(EmbeddedProductRecoveryMapper.result(result, ProductRunPhase.IDLE, false)
            .permittedActions.contains(EmbeddedRecoveryAction.OPEN_CLASSIC))
    }

    @Test fun preflightRejectionsRetainTypedCauseAndSourceWithoutAllocation() {
        val cases = listOf(
            EmbeddedWorkspacePreflightRejection.MissingSlot("cell") to "RendererNotReady",
            EmbeddedWorkspacePreflightRejection.UnknownSlot("cell") to "RendererNotReady",
            EmbeddedWorkspacePreflightRejection.DuplicateSlot("cell") to "RendererNotReady",
            EmbeddedWorkspacePreflightRejection.InvalidSurface("cell") to "RendererNotReady",
            EmbeddedWorkspacePreflightRejection.GeometryRejected("cell", "raw-secret-message") to "GeometryUnavailable",
            EmbeddedWorkspacePreflightRejection.InvalidPlan("raw-secret-message") to "UnsupportedLayout",
            EmbeddedWorkspacePreflightRejection.DuplicateTargetIdentity("pkg", "pkg.Main", listOf("cell", "other")) to "DuplicateTarget",
        )
        for ((rejection, code) in cases) {
            val recovery = EmbeddedProductRecoveryMapper.result(
                EmbeddedWorkspaceRunResult.PreflightRejected(rejection), ProductRunPhase.IDLE, true,
            )
            assertEquals(code, recovery.issue?.code)
            assertEquals(AllocationEvidence.NONE_CONFIRMED, recovery.allocationEvidence)
            assertEquals(CleanupEvidence.NOT_NEEDED, recovery.cleanupEvidence)
            assertTrue(recovery.permittedActions.contains(EmbeddedRecoveryAction.OPEN_CLASSIC))
            assertFalse(recovery.message().contains("raw-secret-message"))
        }
    }

    @Test fun duplicateCallAndMissingResultNeverBecomeCleanEvenAtIdle() {
        for (result in listOf(null, EmbeddedWorkspaceRunResult.DuplicateCall)) {
            val recovery = EmbeddedProductRecoveryMapper.result(result, ProductRunPhase.IDLE, true)
            assertEquals("CleanupOutcomeUncertain", recovery.issue?.code)
            assertEquals(AllocationEvidence.UNKNOWN, recovery.allocationEvidence)
            assertEquals(CleanupEvidence.UNCERTAIN, recovery.cleanupEvidence)
            assertFalse(recovery.permittedActions.contains(EmbeddedRecoveryAction.OPEN_CLASSIC))
        }
    }

    @Test fun incompleteAndRemoteDeathPreserveEvidenceInsteadOfGuessingClean() {
        val incomplete = EmbeddedProductRecoveryMapper.result(EmbeddedWorkspaceRunResult.CleanupIncomplete(
            listOf(receipt()), listOf(EmbeddedWorkspaceCleanupOutcome.Incomplete("cell", null)),
        ), ProductRunPhase.CLEANUP_BLOCKED, false)
        assertEquals("RuntimeCleanupIncomplete", incomplete.issue?.code)
        assertEquals(CleanupEvidence.INCOMPLETE, incomplete.cleanupEvidence)
        val remote = EmbeddedProductRecoveryMapper.result(EmbeddedWorkspaceRunResult.RecoveryRequired(
            "cell", listOf(receipt()), listOf(EmbeddedWorkspaceCleanupOutcome.RecoveryRequired("cell", null)),
        ), ProductRunPhase.CLEANUP_BLOCKED, false)
        assertEquals("RemoteDied", remote.issue?.code)
        assertEquals(CleanupEvidence.UNCERTAIN, remote.cleanupEvidence)
        assertTrue(remote.permittedActions.isEmpty())
        val unknown = EmbeddedProductRecoveryMapper.result(EmbeddedWorkspaceRunResult.RecoveryRequired(
            null, emptyList(), emptyList(),
        ), ProductRunPhase.CLEANUP_BLOCKED, false)
        assertEquals("CleanupOutcomeUncertain", unknown.issue?.code)
    }

    @Test fun stoppedNeedsCompleteNonEmptyOwnedSetWithoutDuplicateOrForeignOutcomes() {
        val cases = listOf(
            EmbeddedWorkspaceRunResult.Stopped(emptyList(), emptyList()),
            EmbeddedWorkspaceRunResult.Stopped(listOf(receipt()), emptyList()),
            EmbeddedWorkspaceRunResult.Stopped(listOf(receipt()), listOf(EmbeddedWorkspaceCleanupOutcome.Clean("other"))),
            EmbeddedWorkspaceRunResult.Stopped(listOf(receipt()), List(2) { EmbeddedWorkspaceCleanupOutcome.Clean("cell") }),
        )
        for (result in cases) {
            val recovery = EmbeddedProductRecoveryMapper.result(result, ProductRunPhase.IDLE, true)
            assertEquals(CleanupEvidence.UNCERTAIN, recovery.cleanupEvidence)
            assertFalse(recovery.permittedActions.contains(EmbeddedRecoveryAction.OPEN_CLASSIC))
        }
        val clean = EmbeddedProductRecoveryMapper.result(EmbeddedWorkspaceRunResult.Stopped(
            listOf(receipt()), listOf(EmbeddedWorkspaceCleanupOutcome.Clean("cell")),
        ), ProductRunPhase.IDLE, true)
        assertEquals(CleanupEvidence.CLEAN_CONFIRMED, clean.cleanupEvidence)
        assertTrue(clean.permittedActions.contains(EmbeddedRecoveryAction.OPEN_CLASSIC))
    }

    @Test fun cleanupCauseUsesFailingCellRatherThanFirstCleanOutcome() {
        val recovery = EmbeddedProductRecoveryMapper.result(EmbeddedWorkspaceRunResult.CleanupIncomplete(
            listOf(receipt()), listOf(EmbeddedWorkspaceCleanupOutcome.Clean("other"),
                EmbeddedWorkspaceCleanupOutcome.Incomplete("cell", null)),
        ), ProductRunPhase.CLEANUP_BLOCKED, false)
        assertEquals("cell", recovery.issue?.sourceCellId)
    }

    @Test fun remoteDeathRetainsKnownOutcomeSourceWhenResultOmitsIt() {
        val recovery = EmbeddedProductRecoveryMapper.result(EmbeddedWorkspaceRunResult.RecoveryRequired(
            null, listOf(receipt()), listOf(EmbeddedWorkspaceCleanupOutcome.RecoveryRequired("cell", null)),
        ), ProductRunPhase.CLEANUP_BLOCKED, false)
        assertEquals("RemoteDied", recovery.issue?.code)
        assertEquals("cell", recovery.issue?.sourceCellId)
    }

    @Test fun cleanFlagWithMissingOrContradictoryRollbackOutcomesRemainsUncertain() {
        val clean = startFailed("START_COMMAND_FAILED", true)
        for (outcomes in listOf(emptyList(), listOf(EmbeddedWorkspaceCleanupOutcome.Clean("other")),
            listOf(EmbeddedWorkspaceCleanupOutcome.Incomplete("cell", null)))) {
            val recovery = EmbeddedProductRecoveryMapper.result(clean.copy(rollbackOutcomes = outcomes), ProductRunPhase.IDLE, true)
            assertFalse(recovery.cleanupEvidence == CleanupEvidence.CLEAN_CONFIRMED)
            assertFalse(recovery.permittedActions.contains(EmbeddedRecoveryAction.OPEN_CLASSIC))
        }
    }

    @Test fun controllerConsumesIssuesAndRechecksGateWhileStartIsSuspended() = runTest {
        val gate = EmbeddedProductRunGate()
        val release = CompletableDeferred<EmbeddedWorkspaceRunResult>()
        val execution = object : EmbeddedProductExecution {
            override suspend fun start() = release.await()
            override suspend fun close(): EmbeddedWorkspaceRunResult? = null
        }
        val controller = EmbeddedWorkspaceProductController("ws", gate,
            EmbeddedCapabilityProbe { EmbeddedCapabilitySnapshot(true, true, true) },
            { ProductHostReadiness(true, true) }, execution, backgroundScope)
        assertTrue(controller.canOpenClassic())
        val starting = async { controller.start() }
        runCurrent()
        assertEquals(ProductRunPhase.STARTING, gate.state.value)
        assertFalse(controller.canOpenClassic())
        assertFalse(gate.tryDispatchClassic { error("must not dispatch") })
        release.complete(startFailed("SURFACE_LOST", true))
        starting.await()
        assertEquals("SurfaceLost", controller.recovery.value.issue?.code)
        assertEquals(ProductRunPhase.IDLE, gate.state.value)
        assertTrue(controller.canOpenClassic())
        gate.tryAcquireEmbedded("other")!!
        assertFalse(controller.canOpenClassic())
    }

    @Test fun controllerDoesNotOfferClassicAfterExceptionOrMissingStartResult() = runTest {
        for (throws in listOf(false, true)) {
            val controller = EmbeddedWorkspaceProductController("ws", EmbeddedProductRunGate(),
                EmbeddedCapabilityProbe { EmbeddedCapabilitySnapshot(true, true, true) },
                { ProductHostReadiness(true, true) }, object : EmbeddedProductExecution {
                    override suspend fun start(): EmbeddedWorkspaceRunResult? {
                        if (throws) error("raw-secret-message")
                        return null
                    }
                    override suspend fun close(): EmbeddedWorkspaceRunResult? = null
                }, backgroundScope)
            controller.start()
            assertEquals(AllocationEvidence.UNKNOWN, controller.recovery.value.allocationEvidence)
            assertEquals(CleanupEvidence.UNCERTAIN, controller.recovery.value.cleanupEvidence)
            assertFalse(controller.canOpenClassic())
        }
    }

    @Test fun laterReadinessFailureCannotEraseUnknownAllocationFromMissingResult() = runTest {
        var binderAvailable = true
        val controller = EmbeddedWorkspaceProductController("ws", EmbeddedProductRunGate(),
            EmbeddedCapabilityProbe { EmbeddedCapabilitySnapshot(true, binderAvailable, true) },
            { ProductHostReadiness(true, true) }, object : EmbeddedProductExecution {
                override suspend fun start(): EmbeddedWorkspaceRunResult? = null
                override suspend fun close(): EmbeddedWorkspaceRunResult? = null
            }, backgroundScope)
        controller.start()
        binderAvailable = false
        controller.start()
        assertEquals(AllocationEvidence.UNKNOWN, controller.recovery.value.allocationEvidence)
        assertEquals(CleanupEvidence.UNCERTAIN, controller.recovery.value.cleanupEvidence)
        assertFalse(controller.canOpenClassic())
    }

    private fun startFailed(code: String, clean: Boolean) = EmbeddedWorkspaceRunResult.StartFailed(
        "cell", EmbeddedSessionFailure(code, "raw-secret-message"), emptyList(), receipt(),
        listOf(EmbeddedWorkspaceCleanupOutcome.Clean("cell")), clean,
    )

    private fun receipt() = EmbeddedWorkspaceItemReceipt(
        "cell", EmbeddedAppSessionId("session"), "pkg", "pkg.Main", 0, EmbeddedSessionPhase.ACTIVE, 10,
    )
}
