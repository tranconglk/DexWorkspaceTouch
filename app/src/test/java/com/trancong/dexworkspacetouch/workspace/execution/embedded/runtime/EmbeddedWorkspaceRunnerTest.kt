package com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime

import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppGeometry
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppSessionId
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppTarget
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionFailure
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionPhase
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionSnapshot
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedTouchEvent
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlan
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlanItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class EmbeddedWorkspaceRunnerTest {
    private val timeouts = EmbeddedWorkspaceRunnerTimeoutPolicy(
        readyTimeout = 10.seconds,
        activeTimeout = 30.seconds,
        cleanupTimeout = 20.seconds,
    )

    @Test fun `every true terminal clears runtime graph and ends root job while cache remains usable`() = runTest {
        for (form in listOf("preflight", "idle", "clean", "incomplete", "recovery", "rollback")) {
            val factory = readyActiveFactory("A")
            if (form == "clean" || form == "rollback") {
                factory.behavior("A").cleanupSnapshot = EmbeddedSessionSnapshot(EmbeddedSessionPhase.STOPPED)
            }
            if (form == "rollback") factory.behavior("A").startSnapshot = EmbeddedSessionSnapshot(
                EmbeddedSessionPhase.FAILED, failure = EmbeddedSessionFailure("PRIMARY", "failure"),
            )
            val runner = runner(factory, this)
            val root = (field(runner, "runnerScope") as CoroutineScope).coroutineContext[Job]!!
            val terminal = when (form) {
                "preflight" -> runner.start(request(listOf("A" to FakeSurface(false))))
                "idle" -> runner.stop()
                "rollback" -> runner.start(request("A"))
                else -> {
                    assertTrue(runner.start(request("A")) is EmbeddedWorkspaceRunResult.Started)
                    if (form == "recovery") {
                        factory.handle("A").emit(EmbeddedSessionSnapshot(EmbeddedSessionPhase.REMOTE_DIED))
                        runCurrent()
                    }
                    val stopped = async { runner.stop() }
                    runCurrent()
                    if (form == "incomplete") { advanceTimeBy(20_000); runCurrent() }
                    stopped.await()
                }
            }
            runCurrent()
            when (form) {
                "preflight" -> assertTrue(terminal is EmbeddedWorkspaceRunResult.PreflightRejected)
                "idle", "clean" -> assertTrue(terminal is EmbeddedWorkspaceRunResult.Stopped)
                "incomplete" -> assertTrue(terminal is EmbeddedWorkspaceRunResult.CleanupIncomplete)
                "recovery" -> assertTrue(terminal is EmbeddedWorkspaceRunResult.RecoveryRequired)
                "rollback" -> {
                    val failed = terminal as EmbeddedWorkspaceRunResult.StartFailed
                    assertEquals("PRIMARY", failed.failure.code)
                    assertTrue(failed.allOwnedSessionsClean)
                    assertEquals("session-A", failed.partialReceipt?.sessionId?.value)
                }
            }
            assertRuntimeDetached(runner, root)
            assertSame(terminal, runner.stop())
            assertSame(terminal, runner.surfaceLost("A"))
            assertEquals(EmbeddedWorkspaceRunResult.DuplicateCall, runner.start(request("A")))
            assertEquals(EmbeddedWorkspaceTouchResult.Rejected("unknown", "UNKNOWN_SOURCE"),
                runner.sendTouch("unknown", touch()))
            if (form !in listOf("preflight", "idle")) {
                assertEquals(EmbeddedWorkspaceTouchResult.Rejected("A", "NOT_ACTIVE"), runner.sendTouch("A", touch()))
                factory.handle("A").emit(EmbeddedSessionSnapshot(EmbeddedSessionPhase.ACTIVE, 99))
                runCurrent()
                assertSame(terminal, runner.stop())
                assertEquals(1, factory.handle("A").detachCalls)
            }
        }
    }

    @Test fun `queued Stop SurfaceLost duplicate Start and Touch all resolve across terminal admission`() = runTest {
        val factory = readyActiveFactory("A")
        factory.behavior("A").cleanupSnapshot = EmbeddedSessionSnapshot(EmbeddedSessionPhase.STOPPED)
        val runner = runner(factory, this)
        val root = (field(runner, "runnerScope") as CoroutineScope).coroutineContext[Job]!!
        runner.start(request("A"))
        var copiedBeforeClear = false
        factory.behavior("A").onDetach = {
            copiedBeforeClear = field(runner, "terminalResult") is EmbeddedWorkspaceRunResult.Stopped &&
                (field(runner, "ownedItems") as Map<*, *>).isNotEmpty()
        }
        val stop = async { runner.stop() }
        val lost = async { runner.surfaceLost("A") }
        val duplicate = async { runner.start(request("B")) }
        val knownTouch = async { runner.sendTouch("A", touch()) }
        val unknownTouch = async { runner.sendTouch("missing", touch()) }
        runCurrent()
        val terminal = stop.await()
        assertSame(terminal, lost.await())
        assertEquals(EmbeddedWorkspaceRunResult.DuplicateCall, duplicate.await())
        assertTrue(knownTouch.isCompleted && unknownTouch.isCompleted)
        assertEquals(EmbeddedWorkspaceTouchResult.Rejected("missing", "UNKNOWN_SOURCE"), unknownTouch.await())
        assertTrue(copiedBeforeClear)
        assertRuntimeDetached(runner, root)
        assertEquals(listOf("A"), factory.created)
        assertEquals(1, factory.handle("A").closeCalls)
    }

    private fun field(runner: EmbeddedWorkspaceRunner, name: String): Any? =
        runner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(runner)

    @Test fun `terminal publication serves concurrent callers while detach is still running`() = runTest {
        val factory = readyActiveFactory("A")
        factory.behavior("A").cleanupSnapshot = EmbeddedSessionSnapshot(EmbeddedSessionPhase.STOPPED)
        val runner = runner(factory, this)
        val root = (field(runner, "runnerScope") as CoroutineScope).coroutineContext[Job]!!
        runner.start(request("A"))
        val callers = java.util.concurrent.Executors.newFixedThreadPool(4)
        var resolved = false
        factory.behavior("A").onDetach = {
            assertFalse(Thread.holdsLock(checkNotNull(field(runner, "admissionLock"))))
            val stop = callers.submit<EmbeddedWorkspaceRunResult> { kotlinx.coroutines.runBlocking { runner.stop() } }
            val lost = callers.submit<EmbeddedWorkspaceRunResult> { kotlinx.coroutines.runBlocking { runner.surfaceLost("A") } }
            val start = callers.submit<EmbeddedWorkspaceRunResult> { kotlinx.coroutines.runBlocking { runner.start(request("B")) } }
            val touch = callers.submit<EmbeddedWorkspaceTouchResult> { kotlinx.coroutines.runBlocking { runner.sendTouch("A", touch()) } }
            assertSame(field(runner, "terminalResult"), stop.get(2, java.util.concurrent.TimeUnit.SECONDS))
            assertSame(stop.get(), lost.get(2, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(EmbeddedWorkspaceRunResult.DuplicateCall, start.get(2, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(EmbeddedWorkspaceTouchResult.Rejected("A", "NOT_ACTIVE"), touch.get(2, java.util.concurrent.TimeUnit.SECONDS))
            resolved = true
        }
        try {
            runner.stop(); runCurrent()
            assertTrue(resolved)
            assertRuntimeDetached(runner, root)
        } finally { callers.shutdownNow() }
    }

    private fun assertRuntimeDetached(runner: EmbeddedWorkspaceRunner, root: Job) {
        assertTrue("Runner root must terminate", root.isCompleted)
        assertTrue(root.children.none())
        assertTrue((field(runner, "preparedItems") as List<*>).isEmpty())
        assertTrue((field(runner, "ownedItems") as Map<*, *>).isEmpty())
        assertTrue((field(runner, "cleanupOrder") as List<*>).isEmpty())
        assertTrue((field(runner, "cleanupOutcomes") as List<*>).isEmpty())
        assertTrue((field(runner, "terminalWaiters") as List<*>).isEmpty())
        for (name in listOf("currentStartupSourceId", "currentCleanupSourceId", "startReply",
            "phaseWaitJob", "cleanupWaitJob", "preflight", "sessionFactory", "runnerScope")) {
            assertNull("Terminal holder $name", field(runner, name))
        }
        @Suppress("UNCHECKED_CAST")
        val channel = field(runner, "events") as kotlinx.coroutines.channels.Channel<Any>
        assertTrue("No pending request/Surface payload", channel.tryReceive().isClosed)
    }

    @Test
    fun `sequential start retains synchronous callbacks and requires every item active`() = runTest {
        val events = mutableListOf<String>()
        val factory = FakeFactory(events).apply {
            behavior("A").syncReady = true
            behavior("A").syncActive = true
            behavior("B").syncReady = true
            behavior("B").syncActive = true
        }
        val runner = runner(factory, this)

        val result = runner.start(request("A", "B"))

        assertTrue(result is EmbeddedWorkspaceRunResult.Started)
        assertEquals(
            listOf("create:A", "connect:A", "start:A", "create:B", "connect:B", "start:B"),
            events.filter { it.startsWith("create") || it.startsWith("connect") || it.startsWith("start") },
        )
        assertTrue(events.indexOf("active:A") < events.indexOf("create:B"))
        assertEquals(listOf("A", "B"), (result as EmbeddedWorkspaceRunResult.Started).receipts.map { it.sourceCellId })
    }

    @Test
    fun `duplicate start does not allocate duplicate handles`() = runTest {
        val factory = readyActiveFactory("A")
        val runner = runner(factory, this)

        assertTrue(runner.start(request("A")) is EmbeddedWorkspaceRunResult.Started)
        assertEquals(EmbeddedWorkspaceRunResult.DuplicateCall, runner.start(request("A")))
        assertEquals(1, factory.created.size)
    }

    @Test
    fun `C failure rolls back C B A and preserves primary failure when B cleanup fails`() = runTest {
        val factory = readyActiveFactory("A", "B")
        factory.behavior("C").apply {
            syncReady = true
            startSnapshot = EmbeddedSessionSnapshot(
                EmbeddedSessionPhase.FAILED,
                failure = EmbeddedSessionFailure("C_FAIL", "C failed"),
            )
            cleanupSnapshot = EmbeddedSessionSnapshot(EmbeddedSessionPhase.STOPPED)
        }
        factory.behavior("B").cleanupSnapshot = EmbeddedSessionSnapshot(
            EmbeddedSessionPhase.CLEANUP_INCOMPLETE,
            failure = EmbeddedSessionFailure("B_CLEANUP", "B cleanup failed"),
        )
        factory.behavior("A").cleanupSnapshot = EmbeddedSessionSnapshot(EmbeddedSessionPhase.STOPPED)
        val runner = runner(factory, this)

        val result = runner.start(request("A", "B", "C")) as EmbeddedWorkspaceRunResult.StartFailed

        assertEquals("C", result.sourceCellId)
        assertEquals("C_FAIL", result.failure.code)
        assertEquals(listOf("C", "B", "A"), result.rollbackOutcomes.map { it.sourceCellId })
        assertTrue(result.rollbackOutcomes[1] is EmbeddedWorkspaceCleanupOutcome.Incomplete)
        assertFalse(result.allOwnedSessionsClean)
        assertEquals(
            listOf("stop:C", "close:C", "stop:B", "close:B", "stop:A", "close:A"),
            factory.events.filter { it.startsWith("stop") || it.startsWith("close") },
        )
    }

    @Test
    fun `external session is untouched by rollback`() = runTest {
        val factory = readyActiveFactory("A")
        factory.behavior("B").apply {
            syncReady = true
            startSnapshot = EmbeddedSessionSnapshot(
                EmbeddedSessionPhase.FAILED,
                failure = EmbeddedSessionFailure("FAIL", "failed"),
            )
            cleanupSnapshot = EmbeddedSessionSnapshot(EmbeddedSessionPhase.STOPPED)
        }
        factory.behavior("A").cleanupSnapshot = EmbeddedSessionSnapshot(EmbeddedSessionPhase.STOPPED)
        val external = FakeHandle("external", FakeBehavior(), mutableListOf())
        val runner = runner(factory, this)

        runner.start(request("A", "B"))

        assertEquals(0, external.stopCalls)
        assertEquals(0, external.closeCalls)
    }

    @Test
    fun `stop before start creates nothing and repeated stop is idempotent`() = runTest {
        val factory = FakeFactory()
        val runner = runner(factory, this)

        val first = runner.stop()
        val second = runner.stop()

        assertTrue(first is EmbeddedWorkspaceRunResult.Stopped)
        assertEquals(first, second)
        assertTrue(factory.created.isEmpty())
    }

    @Test
    fun `stop during B starting prevents C and uses one reverse cleanup sequence`() = runTest {
        val factory = readyActiveFactory("A")
        factory.behavior("B").syncReady = true
        factory.behavior("A").cleanupSnapshot = EmbeddedSessionSnapshot(EmbeddedSessionPhase.STOPPED)
        factory.behavior("B").cleanupSnapshot = EmbeddedSessionSnapshot(EmbeddedSessionPhase.STOPPED)
        val runner = runner(factory, this)

        val start = async { runner.start(request("A", "B", "C")) }
        runCurrent()
        assertEquals(listOf("A", "B"), factory.created)

        val stopped = runner.stop()

        assertTrue(stopped is EmbeddedWorkspaceRunResult.Stopped)
        assertEquals(listOf("A", "B"), factory.created)
        assertEquals(
            listOf("stop:B", "close:B", "stop:A", "close:A"),
            factory.events.filter { it.startsWith("stop") || it.startsWith("close") },
        )
        assertTrue(start.await() is EmbeddedWorkspaceRunResult.Stopped)
    }

    @Test
    fun `stop racing surface loss converges on one cleanup path`() = runTest {
        val factory = readyActiveFactory("A", "B")
        factory.behavior("A").cleanupSnapshot = EmbeddedSessionSnapshot(EmbeddedSessionPhase.STOPPED)
        factory.behavior("B").cleanupSnapshot = EmbeddedSessionSnapshot(EmbeddedSessionPhase.STOPPED)
        val runner = runner(factory, this)
        runner.start(request("A", "B"))

        val stop = async { runner.stop() }
        val lost = async { runner.surfaceLost("B") }
        runCurrent()
        stop.await()
        lost.await()

        assertEquals(1, factory.handle("A").stopCalls)
        assertEquals(1, factory.handle("A").closeCalls)
        assertEquals(1, factory.handle("B").stopCalls)
        assertEquals(1, factory.handle("B").closeCalls)
    }

    @Test
    fun `binder death produces RecoveryRequired without normal operations on dead handle`() = runTest {
        val factory = readyActiveFactory("A")
        val runner = runner(factory, this)
        runner.start(request("A"))

        factory.handle("A").emit(EmbeddedSessionSnapshot(EmbeddedSessionPhase.REMOTE_DIED))
        runCurrent()
        val result = runner.stop()

        assertTrue(result is EmbeddedWorkspaceRunResult.RecoveryRequired)
        assertEquals(0, factory.handle("A").stopCalls)
        assertEquals(0, factory.handle("A").closeCalls)
    }

    @Test
    fun `surface invalid immediately before B start prevents B start and rolls back B A`() = runTest {
        val surfaceB = FakeSurface(true)
        val factory = readyActiveFactory("A")
        factory.behavior("B").apply {
            syncReady = true
            onConnect = { surfaceB.valid = false }
            cleanupSnapshot = EmbeddedSessionSnapshot(EmbeddedSessionPhase.STOPPED)
        }
        factory.behavior("A").cleanupSnapshot = EmbeddedSessionSnapshot(EmbeddedSessionPhase.STOPPED)
        val runner = runner(factory, this)

        val result = runner.start(request(listOf("A" to FakeSurface(true), "B" to surfaceB)))

        assertTrue(result is EmbeddedWorkspaceRunResult.StartFailed)
        assertEquals(0, factory.handle("B").startCalls)
        assertEquals(
            listOf("B", "A"),
            (result as EmbeddedWorkspaceRunResult.StartFailed).rollbackOutcomes.map { it.sourceCellId },
        )
    }

    @Test
    fun `touch routes only to owned active source and rejects unknown source`() = runTest {
        val factory = readyActiveFactory("A", "B")
        val runner = runner(factory, this)
        runner.start(request("A", "B"))
        val touch = touch()

        assertEquals(EmbeddedWorkspaceTouchResult.Accepted, runner.sendTouch("A", touch))
        assertEquals(EmbeddedWorkspaceTouchResult.Accepted, runner.sendTouch("B", touch))
        assertTrue(runner.sendTouch("unknown", touch) is EmbeddedWorkspaceTouchResult.Rejected)
        assertEquals(1, factory.handle("A").touchCalls)
        assertEquals(1, factory.handle("B").touchCalls)
    }

    @Test
    fun `ready timeout fails current item and prevents later allocation`() = runTest {
        val factory = FakeFactory()
        factory.behavior("A").cleanupSnapshot = EmbeddedSessionSnapshot(EmbeddedSessionPhase.STOPPED)
        val runner = runner(factory, this)
        val start = async { runner.start(request("A", "B")) }
        runCurrent()

        advanceTimeBy(10_000)
        runCurrent()
        val result = start.await() as EmbeddedWorkspaceRunResult.StartFailed

        assertEquals("A", result.sourceCellId)
        assertEquals("READY_TIMEOUT", result.failure.code)
        assertEquals(listOf("A"), factory.created)
    }

    @Test
    fun `active timeout rolls back with no retry`() = runTest {
        val factory = FakeFactory()
        factory.behavior("A").syncReady = true
        factory.behavior("A").cleanupSnapshot = EmbeddedSessionSnapshot(EmbeddedSessionPhase.STOPPED)
        val runner = runner(factory, this)
        val start = async { runner.start(request("A", "B")) }
        runCurrent()

        advanceTimeBy(30_000)
        runCurrent()
        val result = start.await() as EmbeddedWorkspaceRunResult.StartFailed

        assertEquals("ACTIVE_TIMEOUT", result.failure.code)
        assertEquals(1, factory.handle("A").startCalls)
        assertEquals(listOf("A"), factory.created)
    }

    @Test
    fun `cleanup timeout is bounded and cannot report clean stopped`() = runTest {
        val factory = readyActiveFactory("A")
        val runner = runner(factory, this)
        runner.start(request("A"))
        val stop = async { runner.stop() }
        runCurrent()

        advanceTimeBy(20_000)
        runCurrent()
        val result = stop.await()

        assertTrue(result is EmbeddedWorkspaceRunResult.CleanupIncomplete)
        assertEquals(1, factory.handle("A").stopCalls)
        assertEquals(1, factory.handle("A").closeCalls)
    }

    @Test
    fun `cleanup remote death produces RecoveryRequired`() = runTest {
        val factory = readyActiveFactory("A")
        factory.behavior("A").cleanupSnapshot = EmbeddedSessionSnapshot(EmbeddedSessionPhase.REMOTE_DIED)
        val runner = runner(factory, this)
        runner.start(request("A"))

        val result = runner.stop()

        assertTrue(result is EmbeddedWorkspaceRunResult.RecoveryRequired)
    }

    @Test
    fun `callback queued before ready timeout wins exactly once`() = runTest {
        val factory = FakeFactory()
        val runner = runner(factory, this)
        val start = async { runner.start(request("A")) }
        runCurrent()

        advanceTimeBy(9_999)
        factory.handle("A").emit(EmbeddedSessionSnapshot(EmbeddedSessionPhase.READY))
        runCurrent()
        factory.handle("A").emit(EmbeddedSessionSnapshot(EmbeddedSessionPhase.ACTIVE, displayId = 7))
        runCurrent()

        assertTrue(start.await() is EmbeddedWorkspaceRunResult.Started)
        assertEquals(0, factory.handle("A").stopCalls)
    }

    @Test
    fun `terminal cleanup detaches notifications and late READY cannot restart`() = runTest {
        val factory = FakeFactory()
        val runner = runner(factory, this)
        val start = async { runner.start(request("A")) }
        runCurrent()
        val stop = async { runner.stop() }
        runCurrent()
        advanceTimeBy(20_000); runCurrent()
        val result = stop.await()
        assertTrue(result is EmbeddedWorkspaceRunResult.CleanupIncomplete)
        assertEquals(result, start.await())
        assertEquals(1, factory.handle("A").detachCalls)
        factory.handle("A").emit(EmbeddedSessionSnapshot(EmbeddedSessionPhase.READY))
        runCurrent()
        assertEquals(0, factory.handle("A").startCalls)
        assertEquals(result, runner.stop())
    }

    private fun runner(factory: FakeFactory, scope: CoroutineScope) =
        EmbeddedWorkspaceRunner(
            preflight = EmbeddedWorkspacePreflight { EmbeddedAppGeometry(900, 675, 320) },
            sessionFactory = factory,
            timeoutPolicy = timeouts,
            scope = scope,
            dispatcher = StandardTestDispatcher((scope as kotlinx.coroutines.test.TestScope).testScheduler),
        )

    private fun readyActiveFactory(vararg ids: String): FakeFactory =
        FakeFactory().apply {
            ids.forEach {
                behavior(it).syncReady = true
                behavior(it).syncActive = true
            }
        }

    private fun request(vararg ids: String): EmbeddedWorkspaceExecutionRequest =
        request(ids.map { it to FakeSurface(true) })

    private fun request(items: List<Pair<String, FakeSurface>>): EmbeddedWorkspaceExecutionRequest {
        val planItems = items.mapIndexed { index, pair ->
            val id = pair.first
            EmbeddedWorkspacePlanItem(
                sourceCellId = id,
                packageName = "com.example." + id.lowercase(),
                componentName = "com.example." + id.lowercase() + ".Main",
                normalizedBounds = NormalizedBounds.FullCanvas,
                order = index,
            )
        }
        return EmbeddedWorkspaceExecutionRequest(
            plan = EmbeddedWorkspacePlan("workspace", "Workspace", planItems),
            hostSlots = items.map { EmbeddedWorkspaceHostSlot(it.first, it.second) },
        )
    }

    private fun touch() = EmbeddedTouchEvent(0, 1f, 2f, 1f, 3L)

    private class FakeSurface(var valid: Boolean) : EmbeddedWorkspaceExecutionSurface {
        override val isValid: Boolean get() = valid
    }

    private data class FakeBehavior(
        var syncReady: Boolean = false,
        var syncActive: Boolean = false,
        var startSnapshot: EmbeddedSessionSnapshot? = null,
        var cleanupSnapshot: EmbeddedSessionSnapshot? = null,
        var onConnect: () -> Unit = {},
        var onDetach: () -> Unit = {},
    )

    private class FakeFactory(
        val events: MutableList<String> = mutableListOf(),
    ) : EmbeddedWorkspaceSessionFactory {
        val created = mutableListOf<String>()
        private val behaviors = mutableMapOf<String, FakeBehavior>()
        private val handles = mutableMapOf<String, FakeHandle>()

        fun behavior(id: String) = behaviors.getOrPut(id) { FakeBehavior() }
        fun handle(id: String) = handles.getValue(id)

        override fun create(
            target: EmbeddedAppTarget,
            observer: (EmbeddedSessionSnapshot) -> Unit,
        ): EmbeddedWorkspaceSessionHandle {
            val id = target.packageName.substringAfterLast('.').uppercase()
            created += id
            events += "create:" + id
            return FakeHandle(id, behavior(id), events, observer).also { handles[id] = it }
        }
    }

    private class FakeHandle(
        private val id: String,
        private val behavior: FakeBehavior,
        private val events: MutableList<String>,
        private val observer: (EmbeddedSessionSnapshot) -> Unit = {},
    ) : EmbeddedWorkspaceSessionHandle {
        override val sessionId = EmbeddedAppSessionId("session-" + id)
        var startCalls = 0
        var stopCalls = 0
        var closeCalls = 0
        var touchCalls = 0
        var detachCalls = 0

        override fun detachNotifications() { detachCalls++; behavior.onDetach() }

        override fun connect() {
            events += "connect:" + id
            behavior.onConnect()
            if (behavior.syncReady) {
                events += "ready:" + id
                observer(EmbeddedSessionSnapshot(EmbeddedSessionPhase.READY))
            }
        }

        override fun start(surface: EmbeddedWorkspaceExecutionSurface) {
            startCalls++
            events += "start:" + id
            val snapshot = behavior.startSnapshot
            when {
                snapshot != null -> observer(snapshot)
                behavior.syncActive -> {
                    events += "active:" + id
                    observer(EmbeddedSessionSnapshot(EmbeddedSessionPhase.ACTIVE, displayId = id.hashCode()))
                }
            }
        }

        override fun sendTouch(event: EmbeddedTouchEvent): Boolean {
            touchCalls++
            return true
        }

        override fun stop() {
            stopCalls++
            events += "stop:" + id
        }

        override fun close() {
            closeCalls++
            events += "close:" + id
            behavior.cleanupSnapshot?.let(observer)
        }

        fun emit(snapshot: EmbeddedSessionSnapshot) = observer(snapshot)
    }
}
