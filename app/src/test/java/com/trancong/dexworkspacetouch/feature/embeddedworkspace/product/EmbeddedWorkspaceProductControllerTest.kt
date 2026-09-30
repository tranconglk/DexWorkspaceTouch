package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmbeddedWorkspaceProductControllerTest {
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
        controller.observeResult(EmbeddedWorkspaceRunResult.CleanupIncomplete(emptyList(), emptyList()))
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

    private class FakeExecution : EmbeddedProductExecution {
        var startCalls = 0
        var closeCalls = 0
        var closeResult: EmbeddedWorkspaceRunResult = EmbeddedWorkspaceRunResult.Stopped(emptyList(), emptyList())
        override suspend fun start(): EmbeddedWorkspaceRunResult { startCalls++; return EmbeddedWorkspaceRunResult.Started(emptyList()) }
        override suspend fun close(): EmbeddedWorkspaceRunResult { closeCalls++; return closeResult }
    }
}
