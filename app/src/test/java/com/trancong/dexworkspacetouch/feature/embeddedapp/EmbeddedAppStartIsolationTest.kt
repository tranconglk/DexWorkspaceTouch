package com.trancong.dexworkspacetouch.feature.embeddedapp

import android.graphics.SurfaceTexture
import android.os.Bundle
import android.os.IBinder
import android.view.Surface
import com.trancong.dexworkspacetouch.feature.embeddedapp.remote.IEmbeddedAppService
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class EmbeddedAppStartIsolationTest {
    @Test fun hungStartAllowsIndependentStopAndCloseWithoutFalseClean() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val lane = BoundedExecutionLane("test-start", 1, 0)
        var task: Runnable? = null
        val f = StartBoundaryFixture(WorkAdmission { task = it; lane.tryExecute(it) })
        try {
            f.ready(fakeStartService(start = {
                entered.countDown()
                try { release.await(); throw IllegalStateException("late failure") }
                finally { returned.countDown() }
            }))
            f.session.startSession(validRawSurface())
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertEquals(false, readField(f.session, "startedRemotely"))
            f.session.stop(); f.session.close()
            assertTrue(f.worker.tasks.isNotEmpty())
            f.worker.drain(); f.main.drain(); f.finalization.drain()
            assertEquals(1L, release.count)
            assertEquals(EmbeddedSessionPhase.CLEANUP_INCOMPLETE, f.phases.last())
            assertFalse(f.phases.contains(EmbeddedSessionPhase.STOPPED))
            assertEquals(0, f.transport.removals)
            f.session.detachNotifications()
            assertDetachedStartTask(checkNotNull(task), f.session)
            val before = f.phases.toList()
            release.countDown()
            assertTrue(returned.await(2, TimeUnit.SECONDS))
            f.completion.drain(); f.main.drain(); f.finalization.drain()
            assertEquals(before, f.phases)
            assertEquals(0, f.transport.removals)
        } finally { release.countDown(); f.close() }
    }

    @Test fun closeAloneCannotTreatSubmittedButUnrepliedStartAsNeverAllocated() {
        val startLane = ManualLane()
        val f = StartBoundaryFixture(startLane)
        try {
            f.ready()
            f.session.startSession(validRawSurface())
            assertEquals(1, startLane.tasks.size)
            assertEquals(false, readField(f.session, "startedRemotely"))
            f.session.close(); f.worker.drain(); f.main.drain(); f.finalization.drain()
            assertEquals(EmbeddedSessionPhase.CLEANUP_INCOMPLETE, f.phases.last())
            assertEquals(0, f.transport.removals)
            f.session.detachNotifications()
            assertDetachedStartTask(startLane.tasks.single(), f.session)
        } finally { f.close() }
    }

    @Test fun rejectedStartAdmissionDoesNotCallBinderOrLeaveMayAllocateEvidence() {
        var calls = 0
        val f = StartBoundaryFixture(WorkAdmission { false })
        try {
            f.ready(fakeStartService(start = { calls++; null }))
            f.session.startSession(validRawSurface()); f.main.drain()
            assertEquals(0, calls)
            assertEquals("START_CAPACITY_EXHAUSTED", f.snapshots.last().failure?.code)
            f.session.close(); f.worker.drain(); f.main.drain(); f.finalization.drain()
            assertEquals(1, f.transport.removals)
        } finally { f.close() }
    }

    @Test fun saturatedStartLaneRejectsWithoutRetryOrCallerRuns() {
        val lane = BoundedExecutionLane("test-full-start", 2, 0)
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        repeat(2) { assertTrue(lane.tryExecute(Runnable { entered.countDown(); release.await() })) }
        val f = StartBoundaryFixture(lane)
        var calls = 0
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            f.ready(fakeStartService(start = { calls++; null }))
            f.session.startSession(validRawSurface()); f.main.drain()
            assertEquals(0, calls)
            assertEquals("START_CAPACITY_EXHAUSTED", f.snapshots.last().failure?.code)
        } finally { release.countDown(); f.close() }
    }

    @Test fun queuedLateSuccessCompletionIsDroppedAfterDetach() {
        val startLane = ManualLane()
        val f = StartBoundaryFixture(startLane)
        try {
            f.ready(); f.session.startSession(validRawSurface()); f.main.drain()
            @Suppress("UNCHECKED_CAST")
            val destination = readField(startLane.tasks.single(), "destination") as DetachableMailbox<StartCompletion>
            val operation = readField(startLane.tasks.single(), "operation") as Long
            destination.offer(StartCompletion(operation, true, 42, null))
            f.session.stop(); f.session.detachNotifications()
            val before = f.phases.toList()
            f.completion.drain(); f.main.drain()
            assertEquals(before, f.phases)
            assertFalse(f.phases.contains(EmbeddedSessionPhase.ACTIVE))
            assertDetachedStartTask(startLane.tasks.single(), f.session)
        } finally { f.close() }
    }
}

internal class StartBoundaryFixture(startLane: WorkAdmission) : AutoCloseable {
    val main = ManualLane()
    val worker = ManualLane()
    val verifier = ManualLane()
    val completion = ManualLane()
    val transportLane = ManualLane()
    val notifications = ManualLane()
    val finalization = ManualLane()
    val transport = FakeConnectionTransport()
    val manager = EmbeddedAppServiceConnectionManager(transport, transportLane, notifications, finalization)
    val snapshots = mutableListOf<EmbeddedSessionSnapshot>()
    val phases get() = snapshots.map { it.phase }
    val session = EmbeddedAppSession(
        EmbeddedAppTarget("com.example.app", "com.example.app.Main", EmbeddedAppGeometry(900, 675, 320)),
        manager, main, worker, verifier, { snapshots += it }, {},
        startLane = startLane, startCompletionExecutor = completion,
    )
    fun ready(service: IEmbeddedAppService = fakeStartService()) {
        session.connect(); transportLane.drain(); transport.connected(service)
        notifications.drain(); main.drain(); verifier.drain(); main.drain()
        assertEquals(EmbeddedSessionPhase.READY, phases.last())
    }
    override fun close() { session.close(); worker.drain(); main.drain(); session.detachNotifications() }
}

internal class ValidRawSurface : Surface(null as SurfaceTexture?) {
    override fun isValid() = true
}
internal fun validRawSurface(): Surface = allocateAndroid(ValidRawSurface::class.java)

internal fun fakeStartService(
    start: () -> Bundle? = { throw IllegalStateException("Start failed") },
    stop: () -> Bundle? = { null },
): IEmbeddedAppService {
    lateinit var service: IEmbeddedAppService
    val binder = Proxy.newProxyInstance(IBinder::class.java.classLoader, arrayOf(IBinder::class.java)) { _, method, _ ->
        when (method.name) { "queryLocalInterface" -> service; "isBinderAlive" -> true; else -> null }
    } as IBinder
    service = Proxy.newProxyInstance(IEmbeddedAppService::class.java.classLoader,
        arrayOf(IEmbeddedAppService::class.java)) { _, method, _ ->
        when (method.name) { "getUid" -> 2000; "asBinder" -> binder; "startSession" -> start(); "stopSession" -> stop(); else -> null }
    } as IEmbeddedAppService
    return service
}

internal fun readField(owner: Any, name: String): Any? =
    owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)

internal fun assertDetachedStartTask(task: Runnable, session: EmbeddedAppSession) {
    val fields = task.javaClass.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }
    assertFalse(fields.any { it.isSynthetic || it.type == EmbeddedAppSession::class.java ||
        it.type == EmbeddedAppServiceLease::class.java || it.type == EmbeddedAppServiceConnectionManager::class.java })
    val destination = readField(task, "destination")!!
    assertNull((readField(destination, "consumer") as AtomicReference<*>).get())
    fields.forEach { it.isAccessible = true; assertNotSame(session, it.get(task)) }
    assertTrue(fields.all { it.type.isPrimitive || it.type == String::class.java ||
        it.type == Surface::class.java || it.type == IEmbeddedAppService::class.java ||
        it.type == DetachableMailbox::class.java })
}
