package com.trancong.dexworkspacetouch.feature.embeddedapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedAppLifecycleCoordinatorTest {
    @Test fun synchronousReadyDuringConnectRemainsReady() {
        val lifecycle = EmbeddedAppLifecycleCoordinator()

        assertTrue(lifecycle.beginConnect())
        lifecycle.serviceReady()
        assertFalse(lifecycle.beginConnect())

        assertEquals(EmbeddedSessionPhase.READY, lifecycle.snapshot.phase)
        assertEquals(
            listOf(EmbeddedSessionPhase.CONNECTING, EmbeddedSessionPhase.READY),
            lifecycle.history.map { it.phase },
        )
    }

    @Test fun successfulStartPublishesStartingThenActiveWithDisplayReceipt() {
        val lifecycle = readyLifecycle()

        assertTrue(lifecycle.beginStart())
        lifecycle.startSucceeded(displayId = 41)

        assertEquals(EmbeddedSessionPhase.ACTIVE, lifecycle.snapshot.phase)
        assertEquals(41, lifecycle.snapshot.displayId)
        assertEquals(
            listOf(EmbeddedSessionPhase.STARTING, EmbeddedSessionPhase.ACTIVE),
            lifecycle.history.takeLast(2).map { it.phase },
        )
    }

    @Test fun startFailurePublishesFailedWithAStableFailureValue() {
        val lifecycle = readyLifecycle()
        val failure = EmbeddedSessionFailure("START_FAILED", "remote rejected")

        lifecycle.beginStart()
        lifecycle.startFailed(failure)
        lifecycle.startFailed(EmbeddedSessionFailure("OTHER", "later"))

        assertEquals(EmbeddedSessionSnapshot(EmbeddedSessionPhase.FAILED, failure = failure), lifecycle.snapshot)
    }

    @Test fun stopThenCloseEmitsOneStoppingAndOneStoppedAfterLeaseRelease() {
        val lifecycle = activeLifecycle()

        assertTrue(lifecycle.requestStop())
        assertTrue(lifecycle.requestClose())
        lifecycle.cleanupSucceeded(leaseReleased = false)
        assertEquals(EmbeddedSessionPhase.STOPPING, lifecycle.snapshot.phase)
        lifecycle.cleanupSucceeded(leaseReleased = true)

        assertEquals(EmbeddedSessionPhase.STOPPED, lifecycle.snapshot.phase)
        assertEquals(1, lifecycle.history.count { it.phase == EmbeddedSessionPhase.STOPPING })
        assertEquals(1, lifecycle.history.count { it.phase == EmbeddedSessionPhase.STOPPED })
    }

    @Test fun closeDuringExistingStopDoesNotIssueASecondRemoteStop() {
        val lifecycle = activeLifecycle()

        assertTrue(lifecycle.requestStop())
        assertTrue(lifecycle.requestClose())
        assertFalse(lifecycle.requestStop())

        assertEquals(1, lifecycle.remoteStopRequestCount)
    }

    @Test fun cleanupFailureEmitsCleanupIncomplete() {
        val lifecycle = activeLifecycle()
        val failure = EmbeddedSessionFailure("CLEANUP_FAILED", "input still present")

        lifecycle.requestStop()
        lifecycle.cleanupFailed(failure)

        assertEquals(
            EmbeddedSessionSnapshot(EmbeddedSessionPhase.CLEANUP_INCOMPLETE, failure = failure),
            lifecycle.snapshot,
        )
    }

    @Test fun binderDeathEmitsRemoteDiedAndNeverStopped() {
        val lifecycle = activeLifecycle()

        lifecycle.remoteDied()
        lifecycle.cleanupSucceeded(leaseReleased = true)

        assertEquals(EmbeddedSessionPhase.REMOTE_DIED, lifecycle.snapshot.phase)
        assertFalse(lifecycle.history.any { it.phase == EmbeddedSessionPhase.STOPPED })
    }

    @Test fun terminalCallbackDeliveredSynchronouslyIsRetained() {
        val lifecycle = activeLifecycle()

        lifecycle.requestStop()
        lifecycle.cleanupSucceeded(leaseReleased = true)
        lifecycle.requestClose()

        assertEquals(EmbeddedSessionPhase.STOPPED, lifecycle.snapshot.phase)
        assertEquals(1, lifecycle.history.count { it.phase == EmbeddedSessionPhase.STOPPED })
    }

    @Test fun stopRacingStartResultsNeverResurrectsActiveOrFailed() {
        val lifecycle = readyLifecycle()
        lifecycle.beginStart()
        lifecycle.requestStop()

        assertFalse(lifecycle.startSucceeded(41))
        assertFalse(lifecycle.startFailed(EmbeddedSessionFailure("START_FAILED", "late")))
        assertEquals(EmbeddedSessionPhase.STOPPING, lifecycle.snapshot.phase)
        assertFalse(lifecycle.history.any { it.phase == EmbeddedSessionPhase.ACTIVE ||
            it.phase == EmbeddedSessionPhase.FAILED })
    }

    @Test fun synchronousRemoteResultsStayTerminalAndEmitOnce() {
        val active = readyLifecycle()
        active.beginStart()
        assertTrue(active.startSucceeded(41))
        assertFalse(active.startFailed(EmbeddedSessionFailure("START_FAILED", "late")))
        assertEquals(1, active.history.count { it.phase == EmbeddedSessionPhase.ACTIVE })

        val failed = readyLifecycle()
        failed.beginStart()
        assertTrue(failed.startFailed(EmbeddedSessionFailure("START_FAILED", "remote")))
        assertFalse(failed.startSucceeded(41))
        assertEquals(1, failed.history.count { it.phase == EmbeddedSessionPhase.FAILED })
    }

    private fun readyLifecycle() = EmbeddedAppLifecycleCoordinator().apply {
        beginConnect()
        serviceReady()
    }

    private fun activeLifecycle() = readyLifecycle().apply {
        beginStart()
        startSucceeded(displayId = 41)
    }
}
