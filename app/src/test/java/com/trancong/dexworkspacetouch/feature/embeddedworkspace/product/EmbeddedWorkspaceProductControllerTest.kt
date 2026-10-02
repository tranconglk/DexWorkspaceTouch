package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunResult
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.*
import com.trancong.dexworkspacetouch.feature.embeddedapp.*
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlan
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlanItem
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmbeddedWorkspaceProductControllerTest {
    @Test fun invokedNullStartRemainsUncertainAndBlocksBothModes() = runTest {
        val gate = EmbeddedProductRunGate()
        val execution = object : EmbeddedProductExecution {
            override suspend fun start(): EmbeddedWorkspaceRunResult? = null
            override suspend fun close(): EmbeddedWorkspaceRunResult? = null
        }
        val controller = readyController(gate, execution, backgroundScope)
        controller.start()
        assertEquals(ProductRunPhase.STARTING, gate.state.value)
        assertFalse(controller.canOpenClassic())
        assertFalse(gate.canEnterEmbedded())
        assertFalse(controller.requestExit())
        assertEquals(ProductRunPhase.CLEANUP_BLOCKED, gate.state.value)
    }

    @Test fun startPlusBackRecordsStopIntentBeforePendingStartReturns() = runTest {
        val gate = EmbeddedProductRunGate()
        val started = CompletableDeferred<EmbeddedWorkspaceRunResult>()
        val cleaned = CompletableDeferred<EmbeddedWorkspaceRunResult>()
        var closeCalls = 0
        val controller = readyController(gate, object : EmbeddedProductExecution {
            override suspend fun start() = started.await()
            override suspend fun close(): EmbeddedWorkspaceRunResult { closeCalls++; return cleaned.await() }
        }, backgroundScope)
        val starting = backgroundScope.async { controller.start() }
        runCurrent()
        val leaving = backgroundScope.async { controller.requestExit() }
        runCurrent()
        assertEquals(ProductRunPhase.STOPPING, gate.state.value)
        assertEquals(1, closeCalls)
        started.complete(EmbeddedWorkspaceRunResult.Started(emptyList()))
        runCurrent()
        assertEquals(ProductRunPhase.STOPPING, gate.state.value)
        cleaned.complete(EmbeddedWorkspaceRunResult.Stopped(emptyList(), emptyList()))
        assertTrue(leaving.await())
        starting.await()
    }

    @Test fun doubleStartReturnsBusyWhileFirstInvocationIsPending() = runTest {
        val gate = EmbeddedProductRunGate()
        val release = CompletableDeferred<EmbeddedWorkspaceRunResult>()
        var starts = 0
        val controller = readyController(gate, object : EmbeddedProductExecution {
            override suspend fun start(): EmbeddedWorkspaceRunResult { starts++; return release.await() }
            override suspend fun close() = EmbeddedWorkspaceRunResult.Stopped(emptyList(), emptyList())
        }, backgroundScope)
        val first = backgroundScope.async { controller.start() }
        runCurrent()
        val second = backgroundScope.async { controller.start() }
        runCurrent()
        assertTrue("Duplicate Start must not wait for pending Start", second.isCompleted)
        assertEquals(ProductStartOutcome.Busy, second.await())
        assertEquals(1, starts)
        release.complete(EmbeddedWorkspaceRunResult.Started(emptyList()))
        first.await()
    }

    @Test fun doubleBackAndDisposeJoinOneBlockedCleanup() = runTest {
        val gate = EmbeddedProductRunGate()
        val release = CompletableDeferred<EmbeddedWorkspaceRunResult>()
        var closes = 0
        val controller = readyController(gate, object : EmbeddedProductExecution {
            override suspend fun start() = EmbeddedWorkspaceRunResult.Started(emptyList())
            override suspend fun close(): EmbeddedWorkspaceRunResult { closes++; return release.await() }
        }, backgroundScope)
        controller.start()
        val back1 = async { controller.requestExit() }
        val back2 = async { controller.requestExit() }
        runCurrent()
        val dispose = controller.onHostDisposed()
        runCurrent()
        release.complete(EmbeddedWorkspaceRunResult.CleanupIncomplete(emptyList(), emptyList()))
        assertFalse(back1.await())
        assertFalse(back2.await())
        dispose.join()
        assertEquals(1, closes)
        assertEquals(ProductRunPhase.CLEANUP_BLOCKED, gate.state.value)
    }

    private fun readyController(gate: EmbeddedProductRunGate, execution: EmbeddedProductExecution,
        scope: kotlinx.coroutines.CoroutineScope) = EmbeddedWorkspaceProductController("ws", gate,
        EmbeddedCapabilityProbe { EmbeddedCapabilitySnapshot(true, true, true) },
        { ProductHostReadiness(true, true) }, execution, scope)

    @Test fun actionWithoutStartNeverInvokesExecutionAndAbsentShizukuReportsReason() = runTest {
        val gate = EmbeddedProductRunGate()
        val execution = FakeExecution()
        val controller = EmbeddedWorkspaceProductController("ws", gate,
            EmbeddedCapabilityProbe { EmbeddedCapabilitySnapshot(true, false, false) },
            { ProductHostReadiness(true, true) }, execution, backgroundScope)
        assertEquals(0, execution.startCalls)
        assertEquals(ProductStartOutcome.NotReady(EmbeddedReadinessResult.ShizukuUnavailable), controller.start())
        assertEquals(0, execution.startCalls)
        assertEquals(ProductRunPhase.IDLE, gate.state.value)
    }

    @Test fun backWhileActiveWaitsForCleanClose() = runTest {
        val gate = EmbeddedProductRunGate()
        val execution = FakeExecution()
        val controller = EmbeddedWorkspaceProductController("ws", gate,
            EmbeddedCapabilityProbe { EmbeddedCapabilitySnapshot(true, true, true) },
            { ProductHostReadiness(true, true) }, execution, backgroundScope)
        assertTrue(controller.start() is ProductStartOutcome.RunResult)
        assertEquals(ProductRunPhase.ACTIVE, gate.state.value)
        assertTrue(controller.requestExit())
        assertEquals(1, execution.closeCalls)
        assertEquals(ProductRunPhase.IDLE, gate.state.value)
    }

    @Test fun incompleteCleanupKeepsGateBlockedAfterExit() = runTest {
        val gate = EmbeddedProductRunGate()
        val execution = FakeExecution().apply { closeResult = EmbeddedWorkspaceRunResult.CleanupIncomplete(emptyList(), emptyList()) }
        val controller = EmbeddedWorkspaceProductController("ws", gate,
            EmbeddedCapabilityProbe { EmbeddedCapabilitySnapshot(true, true, true) },
            { ProductHostReadiness(true, true) }, execution, backgroundScope)
        controller.start()
        assertFalse(controller.requestExit())
        assertEquals(ProductRunPhase.CLEANUP_BLOCKED, gate.state.value)
        assertFalse(gate.tryDispatchClassic { error("must not launch") })
    }

    @Test fun surfaceLossResultBlocksProductGate() = runTest {
        val gate = EmbeddedProductRunGate()
        val controller = EmbeddedWorkspaceProductController("ws", gate,
            EmbeddedCapabilityProbe { EmbeddedCapabilitySnapshot(true, true, true) },
            { ProductHostReadiness(true, true) }, FakeExecution(), backgroundScope)
        controller.start()
        controller.observeResult(controller.startOperation!!, EmbeddedWorkspaceRunResult.CleanupIncomplete(emptyList(), emptyList()))
        assertEquals(ProductRunPhase.CLEANUP_BLOCKED, gate.state.value)
    }

    @Test fun backWaitsForCleanupResultBeforeAllowingClassic() = runTest {
        val gate = EmbeddedProductRunGate()
        val release = CompletableDeferred<EmbeddedWorkspaceRunResult>()
        val execution = object : EmbeddedProductExecution {
            override suspend fun start(): EmbeddedWorkspaceRunResult = EmbeddedWorkspaceRunResult.Started(emptyList())
            override suspend fun close(): EmbeddedWorkspaceRunResult = release.await()
        }
        val controller = EmbeddedWorkspaceProductController("ws", gate,
            EmbeddedCapabilityProbe { EmbeddedCapabilitySnapshot(true, true, true) },
            { ProductHostReadiness(true, true) }, execution, backgroundScope)
        controller.start()
        val leaving = async { controller.requestExit() }
        runCurrent()
        assertEquals(ProductRunPhase.STOPPING, gate.state.value)
        assertFalse(leaving.isCompleted)
        assertFalse(gate.tryDispatchClassic { error("must not launch") })
        release.complete(EmbeddedWorkspaceRunResult.Stopped(emptyList(), emptyList()))
        assertTrue(leaving.await())
        assertEquals(ProductRunPhase.IDLE, gate.state.value)
    }

    @Test fun recreatedRouteCannotPopPastPreviousBlockedOwnership() = runTest {
        val gate = EmbeddedProductRunGate()
        val previous = gate.tryAcquireEmbedded("ws")!!
        gate.acceptResult(previous, EmbeddedWorkspaceRunResult.CleanupIncomplete(emptyList(), emptyList()))
        val recreated = EmbeddedWorkspaceProductController("ws", gate,
            EmbeddedCapabilityProbe { EmbeddedCapabilitySnapshot(true, true, true) },
            { ProductHostReadiness(true, true) }, FakeExecution(), backgroundScope)
        assertFalse(recreated.requestExit())
        assertEquals(ProductRunPhase.CLEANUP_BLOCKED, gate.state.value)
    }

    @Test fun recreatedRouteCannotPopWhilePreviousRunActiveOrStopping() = runTest {
        for (phase in listOf(ProductRunPhase.ACTIVE, ProductRunPhase.STOPPING)) {
            val gate = EmbeddedProductRunGate()
            val previous = gate.tryAcquireEmbedded("ws")!!
            gate.acceptResult(previous, EmbeddedWorkspaceRunResult.Started(emptyList()))
            if (phase == ProductRunPhase.STOPPING) gate.markStopping(previous)
            val recreated = EmbeddedWorkspaceProductController("ws", gate,
                EmbeddedCapabilityProbe { EmbeddedCapabilitySnapshot(true, true, true) },
                { ProductHostReadiness(true, true) }, FakeExecution(), backgroundScope)
            assertFalse(recreated.requestExit())
            assertEquals(phase, gate.state.value)
        }
    }

    @Test fun hostDisposalKeepsCleanupWorkUntilItReturnsIncomplete() = runTest {
        val gate = EmbeddedProductRunGate()
        val release = CompletableDeferred<EmbeddedWorkspaceRunResult>()
        val execution = object : EmbeddedProductExecution {
            override suspend fun start(): EmbeddedWorkspaceRunResult = EmbeddedWorkspaceRunResult.Started(emptyList())
            override suspend fun close(): EmbeddedWorkspaceRunResult = release.await()
        }
        val controller = EmbeddedWorkspaceProductController("ws", gate,
            EmbeddedCapabilityProbe { EmbeddedCapabilitySnapshot(true, true, true) },
            { ProductHostReadiness(true, true) }, execution, backgroundScope)
        controller.start()
        val cleanup = controller.onHostDisposed()
        runCurrent()
        assertEquals(ProductRunPhase.STOPPING, gate.state.value)
        assertFalse(cleanup.isCompleted)
        release.complete(EmbeddedWorkspaceRunResult.CleanupIncomplete(emptyList(), emptyList()))
        cleanup.join()
        assertEquals(ProductRunPhase.CLEANUP_BLOCKED, gate.state.value)
    }

    @Test fun invocationIsRecordedBeforeExecutionAndExceptionIsCopiedAsUncertain() = runTest {
        val gate = EmbeddedProductRunGate()
        val controller = readyController(gate, object : EmbeddedProductExecution {
            override suspend fun start(): EmbeddedWorkspaceRunResult? {
                assertEquals(ProductInvocationCategory.INVOKED, gate.status.value.invocationCategory)
                assertNotNull(gate.status.value.startOperationId)
                error("exception must not be retained")
            }
            override suspend fun close(): EmbeddedWorkspaceRunResult? = null
        }, backgroundScope)
        assertEquals(ProductStartOutcome.Uncertain, controller.start())
        assertEquals(ProductRunPhase.STARTING, gate.state.value)
        assertEquals(CleanupEvidence.UNCERTAIN, gate.status.value.cleanupEvidence)
        assertValueGraph(gate.status.value)
        assertFalse(controller.requestExit())
        assertEquals(ProductRunPhase.CLEANUP_BLOCKED, gate.state.value)
    }

    @Test fun preRunnerRendererChangeHasNoInvocationAndAuthoritativeRelease() = runTest {
        val gate = EmbeddedProductRunGate()
        val execution = FakeExecution()
        var checks = 0
        val controller = EmbeddedWorkspaceProductController("ws", gate,
            EmbeddedCapabilityProbe { EmbeddedCapabilitySnapshot(true, true, true) },
            { checks++; ProductHostReadiness(checks == 1, true) }, execution, backgroundScope)
        assertEquals(ProductStartOutcome.RendererChanged, controller.start())
        assertEquals(0, execution.startCalls)
        assertEquals(ProductInvocationCategory.PRE_RUNNER_REJECTED, gate.status.value.invocationCategory)
        assertEquals(ProductRunPhase.IDLE, gate.state.value)
        assertTrue(controller.canOpenClassic())
    }

    @Test fun executionCancellationAfterInvocationRemainsUncertain() = runTest {
        val gate = EmbeddedProductRunGate()
        val controller = readyController(gate, object : EmbeddedProductExecution {
            override suspend fun start(): EmbeddedWorkspaceRunResult = throw CancellationException("execution cancelled")
            override suspend fun close(): EmbeddedWorkspaceRunResult? = null
        }, backgroundScope)
        assertEquals(ProductStartOutcome.Uncertain, controller.start())
        assertEquals(ProductRunPhase.STARTING, gate.state.value)
        assertEquals(ProductInvocationCategory.RESULT_OR_UNCERTAIN, gate.status.value.invocationCategory)
        assertFalse(controller.canOpenClassic())
        assertFalse(controller.requestExit())
        assertEquals(ProductRunPhase.CLEANUP_BLOCKED, gate.state.value)
        assertNull(readControllerField(controller, "execution"))
    }

    @Test fun cleanupCancellationDoesNotStrandStopping() = runTest {
        val gate = EmbeddedProductRunGate()
        val controller = readyController(gate, object : EmbeddedProductExecution {
            override suspend fun start() = EmbeddedWorkspaceRunResult.Started(emptyList())
            override suspend fun close(): EmbeddedWorkspaceRunResult = throw CancellationException("cleanup cancelled")
        }, backgroundScope)
        controller.start()
        try { assertFalse(controller.requestExit()) } catch (_: CancellationException) { }
        assertEquals(ProductRunPhase.CLEANUP_BLOCKED, gate.state.value)
        assertEquals(CleanupEvidence.UNCERTAIN, gate.status.value.cleanupEvidence)
        assertNull(readControllerField(controller, "execution"))
    }

    @Test fun missingOrExceptionalStartJoinsOneCleanupBeforeDroppingRuntime() = runTest {
        for (form in listOf("null", "exception", "cancellation")) {
            val gate = EmbeddedProductRunGate()
            val release = CompletableDeferred<EmbeddedWorkspaceRunResult>()
            var closes = 0
            val controller = readyController(gate, object : EmbeddedProductExecution {
                override suspend fun start(): EmbeddedWorkspaceRunResult? = when (form) {
                    "exception" -> error("missing authoritative result")
                    "cancellation" -> throw CancellationException("missing authoritative result")
                    else -> null
                }
                override suspend fun close(): EmbeddedWorkspaceRunResult { closes++; return release.await() }
            }, backgroundScope)
            val starting = backgroundScope.async { controller.start() }
            runCurrent()
            assertEquals(ProductRunPhase.STARTING, gate.state.value)
            assertEquals(ProductInvocationCategory.RESULT_OR_UNCERTAIN, gate.status.value.invocationCategory)
            assertEquals(0, closes)
            assertEquals(ProductStartOutcome.Uncertain, starting.await())
            assertNotNull(readControllerField(controller, "execution"))
            val back = backgroundScope.async { controller.requestExit() }
            val secondBack = backgroundScope.async { controller.requestExit() }
            runCurrent()
            assertEquals(ProductRunPhase.STOPPING, gate.state.value)
            assertEquals(1, closes)
            release.complete(EmbeddedWorkspaceRunResult.CleanupIncomplete(emptyList(), emptyList()))
            assertFalse(back.await())
            assertFalse(secondBack.await())
            assertEquals(ProductRunPhase.CLEANUP_BLOCKED, gate.state.value)
            assertNull(readControllerField(controller, "execution"))
        }
    }

    @Test fun exactPreflightRejectedIsAcceptedAfterInvocation() = runTest {
        val gate = EmbeddedProductRunGate()
        val result = EmbeddedWorkspaceRunResult.PreflightRejected(EmbeddedWorkspacePreflightRejection.InvalidPlan("bad plan"))
        val controller = readyController(gate, object : EmbeddedProductExecution {
            override suspend fun start(): EmbeddedWorkspaceRunResult {
                assertEquals(ProductInvocationCategory.INVOKED, gate.status.value.invocationCategory)
                return result
            }
            override suspend fun close() = result
        }, backgroundScope)
        assertEquals(ProductStartOutcome.RunResult(result), controller.start())
        assertEquals(ProductRunPhase.IDLE, gate.state.value)
        assertEquals(ProductInvocationCategory.RESULT_OR_UNCERTAIN, gate.status.value.invocationCategory)
        assertEquals(AllocationEvidence.NONE_CONFIRMED, gate.status.value.allocationEvidence)
        assertTrue(controller.canOpenClassic())
    }

    @Test fun missingStartCallbackAndMissingCleanupResultRemainBlockedAfterDispose() = runTest {
        val gate = EmbeddedProductRunGate()
        val never = CompletableDeferred<EmbeddedWorkspaceRunResult>()
        var closes = 0
        val controller = readyController(gate, object : EmbeddedProductExecution {
            override suspend fun start() = never.await()
            override suspend fun close(): EmbeddedWorkspaceRunResult? { closes++; return null }
        }, backgroundScope)
        val pending = async { controller.start() }
        runCurrent()
        assertEquals(ProductInvocationCategory.INVOKED, gate.status.value.invocationCategory)
        controller.onHostDisposed().join()
        assertEquals(ProductStartOutcome.Uncertain, pending.await())
        assertEquals(1, closes)
        assertEquals(ProductRunPhase.CLEANUP_BLOCKED, gate.state.value)
        assertEquals(CleanupEvidence.UNCERTAIN, gate.status.value.cleanupEvidence)
        assertNull(readControllerField(controller, "execution"))
        assertNull(readControllerField(controller, "hostReadiness"))
        val blocked = gate.status.value
        never.complete(EmbeddedWorkspaceRunResult.Started(emptyList()))
        runCurrent()
        assertEquals(blocked, gate.status.value)
    }

    @Test fun doubleBackSharesExactCleanupIdentityAndDisposeJoinsIt() = runTest {
        val gate = EmbeddedProductRunGate()
        val release = CompletableDeferred<EmbeddedWorkspaceRunResult>()
        var closes = 0
        val controller = readyController(gate, object : EmbeddedProductExecution {
            override suspend fun start() = EmbeddedWorkspaceRunResult.Started(emptyList())
            override suspend fun close(): EmbeddedWorkspaceRunResult { closes++; return release.await() }
        }, backgroundScope)
        controller.start()
        val first = async { controller.requestExit() }
        runCurrent()
        val cleanupId = gate.status.value.cleanupOperationId
        assertNotNull(cleanupId)
        val second = async { controller.requestExit() }
        val dispose = controller.onHostDisposed()
        assertSame(dispose, controller.onHostDisposed())
        runCurrent()
        assertEquals(cleanupId, gate.status.value.cleanupOperationId)
        assertEquals(1, closes)
        release.complete(EmbeddedWorkspaceRunResult.Stopped(emptyList(), emptyList()))
        assertTrue(first.await())
        assertTrue(second.await())
        dispose.join()
        assertTrue(controller.requestExit())
        assertEquals(1, closes)
    }

    @Test fun staleControllerCallbacksCannotMutateItsStatusOrNewerRun() = runTest {
        val gate = EmbeddedProductRunGate()
        val controller = readyController(gate, FakeExecution(), backgroundScope)
        controller.start()
        val start = controller.startOperation!!
        val active = gate.status.value
        for (stale in listOf(start.copy(token = RunToken("ws")), start.copy(generation = start.generation - 1),
            start.copy(operationId = start.operationId + 1))) {
            controller.observeResult(stale, EmbeddedWorkspaceRunResult.CleanupIncomplete(emptyList(), emptyList()))
            assertEquals(active, gate.status.value)
            assertEquals(CleanupEvidence.UNCERTAIN, controller.recovery.value.cleanupEvidence)
        }
        assertTrue(controller.requestExit())
        val newer = gate.tryAcquireEmbedded("newer")!!
        val before = gate.status.value
        controller.observeResult(start, EmbeddedWorkspaceRunResult.Started(emptyList()))
        controller.observeResult(start, EmbeddedWorkspaceRunResult.Stopped(emptyList(), emptyList()))
        assertEquals(before, gate.status.value)
        assertSame(newer, gate.status.value.token)
    }

    @Test fun applicationStatusSurvivesDroppedControllerAndExecutionReferences() = runTest {
        val gate = EmbeddedProductRunGate()
        suspend fun oldRoute() {
            val controller = readyController(gate, FakeExecution().apply {
                closeResult = EmbeddedWorkspaceRunResult.CleanupIncomplete(emptyList(), emptyList())
            }, backgroundScope)
            controller.start()
            assertFalse(controller.requestExit())
            assertNull(readControllerField(controller, "execution"))
            assertNull(readControllerField(controller, "hostReadiness"))
        }
        oldRoute()
        assertEquals(ProductRunPhase.CLEANUP_BLOCKED, gate.status.value.phase)
        assertEquals("RuntimeCleanupIncomplete", gate.status.value.issue?.code)
        assertValueGraph(gate.status.value)
        val observer = readyController(gate, FakeExecution(), backgroundScope)
        assertEquals("RuntimeCleanupIncomplete", observer.currentRecovery().issue?.code)
        assertFalse(observer.canOpenClassic())
    }

    @Test fun terminalNotificationResolvesPendingStartAndDropsRouteWaiter() = runTest {
        val gate = EmbeddedProductRunGate()
        val never = CompletableDeferred<EmbeddedWorkspaceRunResult>()
        val routeScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val controller = readyController(gate, object : EmbeddedProductExecution {
            override suspend fun start() = never.await()
            override suspend fun close(): EmbeddedWorkspaceRunResult? = null
        }, routeScope)
        try {
            val pending = backgroundScope.async { controller.start() }
            runCurrent()
            controller.observeResult(controller.startOperation!!,
                EmbeddedWorkspaceRunResult.CleanupIncomplete(emptyList(), emptyList()))
            runCurrent()
            assertTrue("Terminal callback must resolve the route Start waiter", pending.isCompleted)
            assertEquals(ProductStartOutcome.Uncertain, pending.await())
            assertTrue(routeScope.coroutineContext[Job]!!.children.none())
            assertNull(readControllerField(controller, "execution"))
            assertFalse(controller.requestExit())
        } finally { routeScope.cancel() }
    }

    @Test fun task3gTerminatedRunnerCacheFeedsBlockedProductAndLateStartStaysFenced() = runTest {
        val gate = EmbeddedProductRunGate()
        val routeScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        var callback: ((EmbeddedSessionSnapshot) -> Unit)? = null
        var lateCallback: ((EmbeddedSessionSnapshot) -> Unit)? = null
        var starts = 0
        var closes = 0
        var detaches = 0
        val runner = EmbeddedWorkspaceRunner(EmbeddedWorkspacePreflight { EmbeddedAppGeometry(900, 675, 320) },
            object : EmbeddedWorkspaceSessionFactory {
                override fun create(target: EmbeddedAppTarget, observer: (EmbeddedSessionSnapshot) -> Unit): EmbeddedWorkspaceSessionHandle {
                    callback = observer
                    lateCallback = observer
                    return object : EmbeddedWorkspaceSessionHandle {
                        override val sessionId = EmbeddedAppSessionId("hung-session")
                        override fun connect() { callback?.invoke(EmbeddedSessionSnapshot(EmbeddedSessionPhase.READY)) }
                        override fun start(surface: EmbeddedWorkspaceExecutionSurface) { starts++ }
                        override fun sendTouch(event: EmbeddedTouchEvent) = false
                        override fun stop() { }
                        override fun close() { closes++ }
                        override fun detachNotifications() { detaches++; callback = null }
                    }
                }
            }, PROOF_EMBEDDED_WORKSPACE_TIMEOUTS, this, StandardTestDispatcher(testScheduler))
        val runtimeRoot = (readControllerField(runner, "runnerScope") as CoroutineScope).coroutineContext[Job]!!
        val surface = object : EmbeddedWorkspaceExecutionSurface { override val isValid = true }
        val request = EmbeddedWorkspaceExecutionRequest(EmbeddedWorkspacePlan("ws", "Workspace", listOf(
            EmbeddedWorkspacePlanItem("cell", "pkg", "pkg.Main", NormalizedBounds.FullCanvas, 0))),
            listOf(EmbeddedWorkspaceHostSlot("cell", surface)))
        val controller = readyController(gate, object : EmbeddedProductExecution {
            override suspend fun start() = runner.start(request)
            override suspend fun close() = runner.stop()
        }, routeScope)
        try {
            val starting = async { controller.start() }
            runCurrent()
            assertEquals(1, starts)
            val startOp = controller.startOperation!!
            val leaving = async { controller.requestExit() }
            runCurrent()
            assertEquals(ProductRunPhase.STOPPING, gate.state.value)
            advanceTimeBy(20_000); runCurrent()
            assertFalse(leaving.await())
            starting.await()
            assertTrue(runtimeRoot.isCompleted && runtimeRoot.children.none())
            assertNull(readControllerField(runner, "runnerScope"))
            assertNull(readControllerField(runner, "sessionFactory"))
            assertEquals(1, closes)
            assertEquals(1, detaches)
            assertNull(callback)
            val cached = runner.stop()
            assertSame(cached, runner.stop())
            val blocked = gate.status.value
            assertEquals(ProductRunPhase.CLEANUP_BLOCKED, blocked.phase)
            assertEquals(CleanupEvidence.INCOMPLETE, blocked.cleanupEvidence)
            assertEquals("CLEANUP_TIMEOUT", blocked.cleanupOutcomes.single().failureCode)
            assertNull(readControllerField(controller, "execution"))
            lateCallback?.invoke(EmbeddedSessionSnapshot(EmbeddedSessionPhase.ACTIVE, 42))
            controller.observeResult(startOp, EmbeddedWorkspaceRunResult.Started(emptyList()))
            runCurrent()
            assertSame(cached, runner.stop())
            assertEquals(blocked, gate.status.value)
            assertFalse(gate.canEnterEmbedded())
            assertFalse(gate.tryDispatchClassic { error("must not dispatch") })
            assertValueGraph(blocked)
        } finally { routeScope.cancel() }
    }

    private fun readControllerField(target: Any, name: String): Any? =
        target.javaClass.getDeclaredField(name).also { it.isAccessible = true }.get(target)

    private class FakeExecution : EmbeddedProductExecution {
        var startCalls = 0
        var closeCalls = 0
        var closeResult: EmbeddedWorkspaceRunResult = EmbeddedWorkspaceRunResult.Stopped(emptyList(), emptyList())
        override suspend fun start(): EmbeddedWorkspaceRunResult { startCalls++; return EmbeddedWorkspaceRunResult.Started(emptyList()) }
        override suspend fun close(): EmbeddedWorkspaceRunResult { closeCalls++; return closeResult }
    }
}
