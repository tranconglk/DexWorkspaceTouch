package com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime

import android.content.Context
import android.content.ContextWrapper
import com.trancong.dexworkspacetouch.feature.embeddedapp.*
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlan
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlanItem
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmbeddedWorkspaceHungStartIntegrationTest {
    @Test fun hungBinderStartAndStopPermitLocalTerminalDetachmentAndCannotReviveAfterLateReply() = runTest {
        for (trigger in listOf("stop", "surface", "active-timeout")) {
            val f = Fixture(this)
            try {
                val root = (readField(f.runner, "runnerScope") as CoroutineScope).coroutineContext[Job]!!
                val surface = AndroidEmbeddedWorkspaceExecutionSurface(validRawSurface())
                val start = async { f.runner.start(request(surface)) }
                runCurrent(); f.ready(); runCurrent()
                assertTrue(f.startEntered.await(2, TimeUnit.SECONDS))
                val terminal = when (trigger) {
                    "stop" -> async { f.runner.stop() }
                    "surface" -> async { f.runner.surfaceLost("A") }
                    else -> { advanceTimeBy(30_000); runCurrent(); start }
                }
                runCurrent()
                assertTrue(f.stopEntered.await(2, TimeUnit.SECONDS))
                assertFalse(terminal.isCompleted)
                advanceTimeBy(20_000); runCurrent()
                val result = terminal.await()
                if (trigger == "stop") {
                    assertTrue(result is EmbeddedWorkspaceRunResult.CleanupIncomplete)
                    assertEquals("CLEANUP_TIMEOUT", ((result as EmbeddedWorkspaceRunResult.CleanupIncomplete)
                        .cleanupOutcomes.single() as EmbeddedWorkspaceCleanupOutcome.Incomplete).failure?.code)
                } else {
                    val failure = result as EmbeddedWorkspaceRunResult.StartFailed
                    assertEquals(if (trigger == "surface") "SURFACE_LOST" else "ACTIVE_TIMEOUT", failure.failure.code)
                    assertFalse(failure.allOwnedSessionsClean)
                    assertTrue(failure.rollbackOutcomes.single() is EmbeddedWorkspaceCleanupOutcome.Incomplete)
                }
                assertSame(result, start.await())
                assertEquals(1L, f.release.count)
                assertTrue(root.isCompleted && root.children.none())
                assertNull(readField(f.runner, "runnerScope"))
                assertNull(readField(f.runner, "phaseWaitJob"))
                assertNull(readField(f.runner, "cleanupWaitJob"))
                assertNull(readField(f.runner, "sessionFactory"))
                assertTrue((readField(f.runner, "preparedItems") as List<*>).isEmpty())
                assertTrue((readField(f.runner, "ownedItems") as Map<*, *>).isEmpty())
                assertDetachedStartTask(checkNotNull(f.startTask), f.session)
                val before = f.phases.toList()
                assertSame(result, f.runner.stop())
                f.release.countDown()
                assertTrue(f.startReturned.await(2, TimeUnit.SECONDS))
                f.control.shutdown()
                assertTrue(f.control.awaitTermination(2, TimeUnit.SECONDS))
                f.completions.drain(); f.main.drain(); f.finalization.drain(); runCurrent()
                assertEquals(before, f.phases)
                assertFalse(f.phases.contains(EmbeddedSessionPhase.ACTIVE))
                assertSame(result, f.runner.stop())
                assertEquals(0, f.transport.removals)
            } finally { f.close() }
        }
    }

    private class Fixture(scope: TestScope) : AutoCloseable {
        val main = ManualLane()
        val verifier = ManualLane()
        val completions = ManualLane()
        val connection = ManualLane()
        val notifications = ManualLane()
        val finalization = ManualLane()
        val transport = FakeConnectionTransport()
        val manager = EmbeddedAppServiceConnectionManager(transport, connection, notifications, finalization)
        val control = Executors.newSingleThreadExecutor()
        private val startLane = BoundedExecutionLane("integration-start", 1, 0)
        val startEntered = CountDownLatch(1)
        val stopEntered = CountDownLatch(1)
        val startReturned = CountDownLatch(1)
        val release = CountDownLatch(1)
        var startTask: Runnable? = null
        val phases = mutableListOf<EmbeddedSessionPhase>()
        lateinit var session: EmbeddedAppSession
        val runner = EmbeddedWorkspaceRunner(
            EmbeddedWorkspacePreflight { EmbeddedAppGeometry(900, 675, 320) },
            AndroidEmbeddedWorkspaceSessionFactory(allocateAndroid(ContextWrapper::class.java),
                object : AndroidEmbeddedAppSessionProvider {
                    override fun create(applicationContext: Context, target: EmbeddedAppTarget,
                        observer: (EmbeddedSessionSnapshot) -> Unit): AndroidEmbeddedAppSession {
                        session = EmbeddedAppSession(target, manager, main, control, verifier,
                            { phases += it.phase; observer(it) }, {},
                            startLane = WorkAdmission { startTask = it; startLane.tryExecute(it) },
                            startCompletionExecutor = completions,
                        )
                        return ProductionAndroidEmbeddedAppSession(session)
                    }
                }), PROOF_EMBEDDED_WORKSPACE_TIMEOUTS, scope, StandardTestDispatcher(scope.testScheduler),
        )
        fun ready() {
            connection.drain()
            val enteredStart = startEntered
            val enteredStop = stopEntered
            val returnedStart = startReturned
            val releaseCalls = release
            transport.connected(fakeStartService(start = {
                enteredStart.countDown()
                try { releaseCalls.await(); throw IllegalStateException("Late Start failure") }
                finally { returnedStart.countDown() }
            }, stop = { enteredStop.countDown(); releaseCalls.await(); null }))
            notifications.drain(); main.drain(); verifier.drain(); main.drain()
        }
        override fun close() {
            release.countDown()
            if (this::session.isInitialized) { session.close(); session.detachNotifications() }
            control.shutdown(); control.awaitTermination(2, TimeUnit.SECONDS)
        }
    }

    private fun request(surface: EmbeddedWorkspaceExecutionSurface) = EmbeddedWorkspaceExecutionRequest(
        EmbeddedWorkspacePlan("hung-start", "Hung Start", listOf(EmbeddedWorkspacePlanItem(
            "A", "com.example.app", "com.example.app.Main", NormalizedBounds.FullCanvas, 0,
        ))), listOf(EmbeddedWorkspaceHostSlot("A", surface)),
    )
}
