package com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime

import android.content.Context
import android.content.ContextWrapper
import com.trancong.dexworkspacetouch.feature.embeddedapp.*
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlan
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlanItem
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmbeddedWorkspaceConnectionBoundaryIntegrationTest {
    @Test fun stopAndCleanTerminalCompleteWithRealBoundaryWhileVerifierRemainsHung() = runTest {
        val f = Fixture(this)
        try {
            val start = async { f.runner.start(request()) }
            runCurrent(); f.blockVerifier()
            val stop = async { f.runner.stop() }
            runCurrent(); f.cleanup.drain(); f.main.drain(); runCurrent()
            assertEquals(1L, f.release.count)
            val result = stop.await()
            assertTrue(result is EmbeddedWorkspaceRunResult.Stopped)
            assertEquals(result, start.await())
            assertFalse(f.phases.contains(EmbeddedSessionPhase.READY))
            f.release.countDown()
            assertTrue(f.uidReturned.await(1, TimeUnit.SECONDS))
            f.main.drain(); runCurrent()
            assertEquals(result, f.runner.stop())
        } finally { f.close() }
    }

    @Test fun surfaceLossIsProcessedBeforeBlockedVerifierReturns() = runTest {
        val f = Fixture(this)
        try {
            val start = async { f.runner.start(request()) }
            runCurrent(); f.blockVerifier()
            val lost = async { f.runner.surfaceLost("A") }
            runCurrent(); f.cleanup.drain(); f.main.drain(); runCurrent()
            assertEquals(1L, f.release.count)
            val result = lost.await() as EmbeddedWorkspaceRunResult.StartFailed
            assertEquals("SURFACE_LOST", result.failure.code)
            assertTrue(result.allOwnedSessionsClean)
            assertEquals(result, start.await())
        } finally { f.close() }
    }

    @Test fun readyTimeoutAndCleanupDeadlineRemainAuthoritativeWhileVerifierHung() = runTest {
        val f = Fixture(this)
        try {
            val start = async { f.runner.start(request()) }
            runCurrent(); f.blockVerifier()
            advanceTimeBy(10_000); runCurrent()
            assertFalse(start.isCompleted)
            assertTrue(f.cleanup.tasks.isNotEmpty())
            // Hold cleanup scheduling to exercise bounded terminal fallback, independently of UID.
            advanceTimeBy(20_000); runCurrent()
            val result = start.await() as EmbeddedWorkspaceRunResult.StartFailed
            assertEquals("READY_TIMEOUT", result.failure.code)
            assertEquals("CLEANUP_TIMEOUT",
                (result.rollbackOutcomes.single() as EmbeddedWorkspaceCleanupOutcome.Incomplete).failure?.code)
            assertFalse(result.allOwnedSessionsClean)
            assertEquals(1L, f.release.count)
            val delivered = f.phases.toList()
            f.cleanup.drain(); f.main.drain(); runCurrent()
            assertEquals(delivered, f.phases)
            f.release.countDown()
            assertTrue(f.uidReturned.await(1, TimeUnit.SECONDS))
            f.main.drain(); runCurrent()
            assertEquals(result, f.runner.stop())
        } finally { f.close() }
    }

    @Test fun stopCleanupTimeoutPublishesIncompleteAndRejectsLateReady() = runTest {
        val f = Fixture(this)
        try {
            val start = async { f.runner.start(request()) }
            runCurrent(); f.blockVerifier()
            val stop = async { f.runner.stop() }
            runCurrent()
            advanceTimeBy(20_000); runCurrent()
            val result = stop.await() as EmbeddedWorkspaceRunResult.CleanupIncomplete
            assertEquals("CLEANUP_TIMEOUT",
                (result.cleanupOutcomes.single() as EmbeddedWorkspaceCleanupOutcome.Incomplete).failure?.code)
            assertEquals(result, start.await())
            assertEquals(1L, f.release.count)
            assertFalse(f.phases.contains(EmbeddedSessionPhase.READY))
        } finally { f.close() }
    }

    @Test fun stopRemainsProcessableWhileConnectionTransportIsBlocked() = runTest {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val f = Fixture(this, BoundedExecutionLane("integration-transport", 1, 0))
        f.transport.onConnect = { entered.countDown(); release.await() }
        try {
            val start = async { f.runner.start(request()) }
            runCurrent()
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            val stop = async { f.runner.stop() }
            runCurrent(); f.cleanup.drain(); f.main.drain(); runCurrent()
            assertEquals(1L, release.count)
            assertTrue(stop.await() is EmbeddedWorkspaceRunResult.Stopped)
            assertEquals(stop.await(), start.await())
            assertFalse(f.phases.contains(EmbeddedSessionPhase.READY))
        } finally { release.countDown(); f.close() }
    }

    @Test fun remoteDeathDuringCleanupIsStillObservedWhileUidIsHung() = runTest {
        val f = Fixture(this)
        try {
            val start = async { f.runner.start(request()) }
            runCurrent(); f.blockVerifier()
            val stop = async { f.runner.stop() }
            runCurrent()
            // Close is queued but has not acknowledged scoped cleanup/lease release.
            f.transport.disconnected()
            f.notifications.drain(); f.main.drain(); runCurrent()
            assertEquals(1L, f.release.count)
            assertTrue(stop.await() is EmbeddedWorkspaceRunResult.RecoveryRequired)
            assertEquals(stop.await(), start.await())
            assertTrue(f.phases.contains(EmbeddedSessionPhase.REMOTE_DIED))
        } finally { f.close() }
    }

    private class Fixture(scope: TestScope, connectionLane: WorkAdmission? = null) : AutoCloseable {
        val main = ManualLane()
        val cleanup = ManualLane()
        val notifications = ManualLane()
        val transportLane = ManualLane()
        val finalization = ManualLane()
        val transport = FakeConnectionTransport()
        val manager = EmbeddedAppServiceConnectionManager(transport, connectionLane ?: transportLane, notifications, finalization)
        val release = CountDownLatch(1)
        val entered = CountDownLatch(1)
        val uidReturned = CountDownLatch(1)
        val verifier = BoundedExecutionLane("integration-uid", 1, 0)
        val phases = mutableListOf<EmbeddedSessionPhase>()
        lateinit var session: EmbeddedAppSession
        private val provider = object : AndroidEmbeddedAppSessionProvider {
            override fun create(applicationContext: Context, target: EmbeddedAppTarget,
                observer: (EmbeddedSessionSnapshot) -> Unit): AndroidEmbeddedAppSession {
                session = EmbeddedAppSession(target, manager, main, cleanup, verifier, {
                    phases += it.phase
                    observer(it)
                }, {})
                return ProductionAndroidEmbeddedAppSession(session)
            }
        }
        val runner = EmbeddedWorkspaceRunner(
            EmbeddedWorkspacePreflight { EmbeddedAppGeometry(900, 675, 320) },
            AndroidEmbeddedWorkspaceSessionFactory(allocateAndroid(ContextWrapper::class.java), provider),
            PROOF_EMBEDDED_WORKSPACE_TIMEOUTS, scope, StandardTestDispatcher(scope.testScheduler),
        )
        fun blockVerifier() {
            transportLane.drain()
            val enteredUid = entered
            val releaseUid = release
            val returnedUid = uidReturned
            transport.connected(fakeUidService {
                enteredUid.countDown()
                try { releaseUid.await(); 2000 } finally { returnedUid.countDown() }
            })
            notifications.drain(); main.drain()
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertEquals(listOf(EmbeddedSessionPhase.CONNECTING), phases)
        }
        override fun close() {
            release.countDown()
            if (this::session.isInitialized) {
                session.close(); cleanup.drain(); main.drain(); session.detachNotifications()
            }
        }
    }

    private fun request() = EmbeddedWorkspaceExecutionRequest(
        EmbeddedWorkspacePlan("boundary", "Boundary", listOf(EmbeddedWorkspacePlanItem(
            "A", "com.example.app", "com.example.app.Main", NormalizedBounds.FullCanvas, 0,
        ))),
        listOf(EmbeddedWorkspaceHostSlot("A", object : EmbeddedWorkspaceExecutionSurface {
            override val isValid = true
        })),
    )
}
