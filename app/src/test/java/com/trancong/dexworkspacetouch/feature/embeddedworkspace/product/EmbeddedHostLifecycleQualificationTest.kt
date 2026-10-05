package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import com.trancong.dexworkspacetouch.feature.embeddedapp.*
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.*
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.execution.embedded.*
import com.trancong.dexworkspacetouch.workspace.execution.embedded.layout.*
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Category A only: no Android host, Binder, or native Surface lifetime claim. */
@OptIn(ExperimentalCoroutinesApi::class)
class EmbeddedHostLifecycleQualificationTest {
    @Test fun backDisposeJoinExactOwnedCleanupAndRequireFreshExplicitStart() = runTest {
        val gate = EmbeddedProductRunGate()
        val reply = CompletableDeferred<EmbeddedWorkspaceRunResult?>()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        var closeCalls = 0
        var oldPops = 0
        val controller = EmbeddedWorkspaceProductController("010", gate,
            EmbeddedCapabilityProbe { EmbeddedCapabilitySnapshot(true, true, true) },
            { ProductHostReadiness(true, true) }, object : EmbeddedProductExecution {
                override suspend fun start() = EmbeddedWorkspaceRunResult.Started(receipts())
                override suspend fun close(): EmbeddedWorkspaceRunResult? { closeCalls++; return reply.await() }
            }, scope)
        try {
            controller.start()
            val start = controller.startOperation!!
            controller.requestBack { oldPops++ }
            val cleanupId = gate.status.value.cleanupOperationId
            val disposal = controller.onHostDisposed()
            var newAllocations = 0
            assertNull(gate.createExecutionIfIdle { newAllocations++; Any() })
            assertEquals(1, closeCalls)
            assertEquals(cleanupId, gate.status.value.cleanupOperationId)
            assertEquals(ProductRunPhase.STOPPING, gate.status.value.phase)
            assertFalse(gate.acceptResult(start, ProductExecutionValue.from(clean())))
            reply.complete(clean())
            disposal.join(); runCurrent()
            assertEquals(0, oldPops)
            assertEquals(0, newAllocations)
            assertEquals(ProductRunPhase.IDLE, gate.status.value.phase)
            assertEquals(setOf("calc", "waze"), gate.status.value.cleanupOutcomes.map { it.sourceCellId }.toSet())
            assertEquals(CleanupEvidence.CLEAN_CONFIRMED, gate.status.value.cleanupEvidence)
            val terminal = gate.status.value
            runCurrent()
            assertEquals(terminal, gate.status.value)
            // Only this explicit acquisition creates a fresh run.
            val fresh = gate.tryAcquireEmbedded("010")!!
            assertNotSame(start.token, fresh)
            assertTrue(gate.status.value.generation > start.generation)
            assertFalse(gate.acceptResult(start, ProductExecutionValue.from(clean())))
            assertFalse(gate.acceptResult(ProductRunOperation(start.token, start.generation, cleanupId!!,
                ProductOperationKind.CLEANUP), ProductExecutionValue.from(clean())))
            assertSame(fresh, gate.status.value.token)
        } finally { scope.cancel() }
    }

    @Test fun missingOwnedReceiptOrIncompleteTerminalCannotUnlockAfterNavigationAndRefresh() {
        val results = listOf(
            EmbeddedWorkspaceRunResult.Stopped(receipts().take(1), listOf(EmbeddedWorkspaceCleanupOutcome.Clean("calc"))),
            EmbeddedWorkspaceRunResult.CleanupIncomplete(receipts(), listOf(
                EmbeddedWorkspaceCleanupOutcome.Clean("calc"),
                EmbeddedWorkspaceCleanupOutcome.Incomplete("waze", EmbeddedSessionFailure("CONTROLLED_010", null)))),
        )
        results.forEach { result ->
            val gate = EmbeddedProductRunGate()
            val token = gate.tryAcquireEmbedded("010")!!
            val start = gate.startOperation(token)!!
            assertTrue(gate.markInvoked(start))
            assertTrue(gate.acceptResult(start, ProductExecutionValue.from(EmbeddedWorkspaceRunResult.Started(receipts()))))
            val cleanup = gate.markStopping(start)!!
            assertTrue(gate.acceptResult(cleanup, ProductExecutionValue.from(result)))
            val blocked = gate.status.value
            assertEquals(ProductRunPhase.CLEANUP_BLOCKED, blocked.phase)
            assertNotNull(blocked.cleanupBlockedUi())
            val recovery = EmbeddedProductRecoveryMapper.snapshot(blocked.phase, gate.canEnterEmbedded(),
                blocked.issue, blocked.allocationEvidence, blocked.cleanupEvidence)
            assertEquals(recovery, EmbeddedWorkspaceReadiness.withRecovery(EmbeddedReadinessResult.Ready, recovery))
            assertFalse(gate.tryDispatchClassic { fail("Classic dispatched while blocked") })
            assertNull(gate.createExecutionIfIdle { fail("Navigation allocated while blocked") })
            assertNull(gate.tryAcquireEmbedded("010"))
            assertFalse(gate.acceptResult(cleanup, ProductExecutionValue.from(clean())))
            assertEquals(blocked, gate.status.value)
        }
    }

    @Test fun wrongGenerationOrOperationCannotCompleteCurrentCleanup() {
        val gate = EmbeddedProductRunGate()
        val start = gate.startOperation(gate.tryAcquireEmbedded("010")!!)!!
        gate.markInvoked(start)
        gate.acceptResult(start, ProductExecutionValue.from(EmbeddedWorkspaceRunResult.Started(receipts())))
        val cleanup = gate.markStopping(start)!!
        val stopping = gate.status.value
        assertFalse(gate.acceptResult(cleanup.copy(generation = cleanup.generation + 1), ProductExecutionValue.from(clean())))
        assertFalse(gate.acceptResult(cleanup.copy(operationId = cleanup.operationId + 1), ProductExecutionValue.from(clean())))
        assertEquals(stopping, gate.status.value)
        assertTrue(gate.acceptResult(cleanup, ProductExecutionValue.from(clean())))
        assertEquals(ProductRunPhase.IDLE, gate.status.value.phase)
    }

    @Test fun cleanSurfaceLossResultHasOneReceiptPerOwnedCellAndReleasesGate() = runTest {
        val gate = EmbeddedProductRunGate()
        val plan = EmbeddedWorkspacePlan("010", "010", listOf(
            EmbeddedWorkspacePlanItem("calc", "calc", "calc.Main", NormalizedBounds.FullCanvas, 0)))
        val factory = object : EmbeddedWorkspaceSessionFactory {
            override fun create(target: EmbeddedAppTarget, observer: (EmbeddedSessionSnapshot) -> Unit) =
                object : EmbeddedWorkspaceSessionHandle {
                    override val sessionId = EmbeddedAppSessionId("010-surface-clean")
                    override fun connect() = observer(EmbeddedSessionSnapshot(EmbeddedSessionPhase.READY))
                    override fun start(surface: EmbeddedWorkspaceExecutionSurface) = observer(EmbeddedSessionSnapshot(EmbeddedSessionPhase.ACTIVE, 42))
                    override fun sendTouch(event: EmbeddedTouchEvent) = true
                    override fun stop() = Unit
                    override fun close() = observer(EmbeddedSessionSnapshot(EmbeddedSessionPhase.STOPPED))
                }
        }
        val runner = EmbeddedWorkspaceRunner(EmbeddedWorkspacePreflight { EmbeddedAppGeometry(900, 675, 320) },
            factory, PROOF_EMBEDDED_WORKSPACE_TIMEOUTS, this, StandardTestDispatcher(testScheduler))
        val surface = object : EmbeddedWorkspaceExecutionSurface { override val isValid = true }
        try {
            val token = gate.tryAcquireEmbedded("010")!!
            val operation = gate.startOperation(token)!!
            gate.markInvoked(operation)
            val started = runner.start(EmbeddedWorkspaceExecutionRequest(plan, listOf(EmbeddedWorkspaceHostSlot("calc", surface))))
            assertTrue(gate.acceptResult(operation, ProductExecutionValue.from(started)))
            val terminal = runner.surfaceLost("calc") as EmbeddedWorkspaceRunResult.StartFailed
            assertEquals("SURFACE_LOST", terminal.failure.code)
            assertTrue(terminal.allOwnedSessionsClean)
            val value = ProductExecutionValue.from(terminal)
            assertEquals("owned result must contain calc exactly once", listOf("calc"), value.items.map { it.sourceCellId })
            assertTrue(value.authoritativeClean)
            assertTrue(gate.acceptResult(operation, value))
            assertEquals(ProductRunPhase.IDLE, gate.status.value.phase)
        } finally { runner.stop() }
    }

    @Test fun cleanSurfaceLossAllowsExplicitFreshStartPolicyWhileUncertainStaysBlocked() {
        for (allClean in listOf(true, false)) {
            val gate = EmbeddedProductRunGate()
            val operation = gate.startOperation(gate.tryAcquireEmbedded("010")!!)!!
            gate.markInvoked(operation)
            val owned = receipts()
            gate.acceptResult(operation, ProductExecutionValue.from(EmbeddedWorkspaceRunResult.Started(owned)))
            val cleanup = gate.markStopping(operation)!!
            val terminal = EmbeddedWorkspaceRunResult.StartFailed(
                sourceCellId = "calc",
                failure = EmbeddedSessionFailure("SURFACE_LOST", "Execution surface was lost"),
                receipts = owned.filterNot { it.sourceCellId == "calc" },
                partialReceipt = owned.first().copy(phase = EmbeddedSessionPhase.STOPPED),
                rollbackOutcomes = listOf(EmbeddedWorkspaceCleanupOutcome.Clean("calc"),
                    EmbeddedWorkspaceCleanupOutcome.Clean("waze")),
                allOwnedSessionsClean = allClean,
            )
            assertTrue(gate.acceptResult(cleanup, ProductExecutionValue.from(terminal)))
            val status = gate.status.value
            val recovery = EmbeddedProductRecoveryMapper.snapshot(status.phase, gate.canEnterEmbedded(),
                status.issue, status.allocationEvidence, status.cleanupEvidence)
            assertEquals(EmbeddedProductIssue.SurfaceLost("calc"), recovery.issue)
            assertEquals(if (allClean) ProductRunPhase.IDLE else ProductRunPhase.CLEANUP_BLOCKED, status.phase)
            assertEquals("exact clean allows a fresh explicit run; prior SurfaceLost is retained as history",
                allClean, EmbeddedRecoveryAction.START_EMBEDDED in recovery.permittedActions)
            assertEquals(allClean, EmbeddedRecoveryAction.OPEN_CLASSIC in recovery.permittedActions)
            assertEquals(1L, status.generation)
            if (allClean) {
                assertFalse(EmbeddedRecoveryAction.START_EMBEDDED in EmbeddedProductRecoveryMapper.readiness(
                    EmbeddedReadinessResult.ShizukuUnavailable, status.phase, gate.canEnterEmbedded()).permittedActions)
            }
        }
    }

    @Test fun viewportAndStaleSurfaceEventsCannotAffectNewRendererGeneration() = runTest {
        var stops = 0
        var closes = 0
        val plan = EmbeddedWorkspacePlan("010", "010", listOf(
            EmbeddedWorkspacePlanItem("calc", "calc", "calc.Main", NormalizedBounds.FullCanvas, 0)))
        val snapshot = (EmbeddedWorkspaceGeometrySnapshot.resolve(plan,
            EmbeddedGeometryPolicy { EmbeddedAppGeometry(900, 675, 320) }) as GeometrySnapshotResult.Ready).snapshot
        fun renderer(): EmbeddedWorkspaceRendererCoordinator {
            val factory = object : EmbeddedWorkspaceSessionFactory {
                override fun create(target: EmbeddedAppTarget, observer: (EmbeddedSessionSnapshot) -> Unit) =
                    object : EmbeddedWorkspaceSessionHandle {
                        override val sessionId = EmbeddedAppSessionId("010-calc")
                        override fun connect() = observer(EmbeddedSessionSnapshot(EmbeddedSessionPhase.READY))
                        override fun start(surface: EmbeddedWorkspaceExecutionSurface) = observer(EmbeddedSessionSnapshot(EmbeddedSessionPhase.ACTIVE, 10))
                        override fun sendTouch(event: EmbeddedTouchEvent) = true
                        override fun stop() { stops++ }
                        override fun close() { closes++; observer(EmbeddedSessionSnapshot(EmbeddedSessionPhase.STOPPED)) }
                    }
            }
            val runner = EmbeddedWorkspaceRunner(EmbeddedWorkspacePreflight(snapshot.asPolicy()), factory,
                PROOF_EMBEDDED_WORKSPACE_TIMEOUTS, this, StandardTestDispatcher(testScheduler))
            return EmbeddedWorkspaceRendererCoordinator(snapshot, EmbeddedWorkspaceLayoutMapper(),
                EmbeddedWorkspaceRunnerController(plan, runner))
        }
        val old = renderer()
        val fresh = renderer()
        val oldView = Any(); val oldSurface = Any(); val freshView = Any(); val freshSurface = Any()
        val surface = object : EmbeddedWorkspaceExecutionSurface { override val isValid = true }
        try {
            old.onViewportChanged(EmbeddedWorkspaceViewport(1000, 800))
            old.onSurfaceAvailable(old.generationToken, "calc", oldView, oldSurface, surface)
            old.start()
            old.onViewportChanged(EmbeddedWorkspaceViewport(0, 0))
            assertFalse(old.state.value.touchEnabled)
            assertEquals(0, stops)
            old.onViewportChanged(EmbeddedWorkspaceViewport(1200, 900))
            assertTrue(old.state.value.touchEnabled)
            // Replacement loses the old slot; it does not hot-swap an active execution.
            old.onSurfaceAvailable(old.generationToken, "calc", oldView, Any(), surface)
            assertEquals(1, stops)
            assertEquals(1, closes)
            assertFalse(old.state.value.surfaceValidity.getValue("calc"))
            old.close()
            fresh.onViewportChanged(EmbeddedWorkspaceViewport(1000, 800))
            fresh.onSurfaceAvailable(fresh.generationToken, "calc", freshView, freshSurface, surface)
            fresh.onSurfaceDestroyed(old.generationToken, "calc", oldView, oldSurface)
            fresh.onSurfaceDestroyed(fresh.generationToken, "calc", freshView, oldSurface)
            fresh.onSurfaceAvailable(old.generationToken, "calc", oldView, oldSurface, surface)
            assertTrue(fresh.state.value.canStart)
            assertTrue(fresh.state.value.surfaceValidity.getValue("calc"))
            assertEquals(1, stops)
        } finally { old.close(); fresh.close() }
    }

    private fun receipts() = listOf("calc", "waze").mapIndexed { index, id ->
        EmbeddedWorkspaceItemReceipt(id, EmbeddedAppSessionId("010-$id"), id, "$id.Main", index,
            EmbeddedSessionPhase.ACTIVE, 10 + index)
    }
    private fun clean() = EmbeddedWorkspaceRunResult.Stopped(receipts(),
        listOf(EmbeddedWorkspaceCleanupOutcome.Clean("waze"), EmbeddedWorkspaceCleanupOutcome.Clean("calc")))
}
