package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class EmbeddedProductRunGateTest {
    @Test fun startedAndCleanStopFollowProductPhases() {
        val gate = EmbeddedProductRunGate()
        val token = gate.tryAcquireEmbedded("ws")!!
        assertEquals(ProductRunPhase.STARTING, gate.state.value)
        gate.acceptResult(token, EmbeddedWorkspaceRunResult.Started(emptyList()))
        assertEquals(ProductRunPhase.ACTIVE, gate.state.value)
        assertFalse(gate.tryDispatchClassic { error("must not dispatch") })
        gate.markStopping(token)
        assertEquals(ProductRunPhase.STOPPING, gate.state.value)
        gate.acceptResult(token, EmbeddedWorkspaceRunResult.Stopped(emptyList(), emptyList()))
        assertEquals(ProductRunPhase.IDLE, gate.state.value)
        var dispatched = false
        assertTrue(gate.tryDispatchClassic { dispatched = true })
        assertTrue(dispatched)
    }

    @Test fun incompleteCleanupBlocksBothModesAndStaleCallback() {
        val gate = EmbeddedProductRunGate()
        val old = gate.tryAcquireEmbedded("old")!!
        gate.acceptResult(old, EmbeddedWorkspaceRunResult.PreflightRejected(
            com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspacePreflightRejection.InvalidPlan("x")))
        val current = gate.tryAcquireEmbedded("current")!!
        gate.acceptResult(old, EmbeddedWorkspaceRunResult.Started(emptyList()))
        assertEquals(ProductRunPhase.STARTING, gate.state.value)
        gate.acceptResult(current, EmbeddedWorkspaceRunResult.CleanupIncomplete(emptyList(), emptyList()))
        assertEquals(ProductRunPhase.CLEANUP_BLOCKED, gate.state.value)
        assertNull(gate.tryAcquireEmbedded("next"))
        assertFalse(gate.tryDispatchClassic { error("must not dispatch") })
    }

    @Test fun classicDispatchAndEmbeddedAcquireCannotOverlap() {
        val gate = EmbeddedProductRunGate()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val worker = thread {
            assertTrue(gate.tryDispatchClassic {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
            })
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        assertNull(gate.tryAcquireEmbedded("ws"))
        release.countDown()
        worker.join(5000)
        assertFalse(worker.isAlive)
        assertNotNull(gate.tryAcquireEmbedded("ws"))
    }
}
