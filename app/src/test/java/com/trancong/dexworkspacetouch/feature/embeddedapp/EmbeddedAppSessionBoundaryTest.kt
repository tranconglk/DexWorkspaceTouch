package com.trancong.dexworkspacetouch.feature.embeddedapp

import android.view.Surface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class EmbeddedAppSessionBoundaryTest {
    @Test fun wrongUidMustNeverPublishReady() = withFixture { f ->
        f.connect(fakeUidService { 1000 })
        f.verifier.drain(); f.main.drain()
        assertFalse(f.phases.contains(EmbeddedSessionPhase.READY))
        assertEquals("UNEXPECTED_SERVICE_UID", f.snapshots.last().failure?.code)
    }

    @Test fun uidErrorMustNeverPublishReady() = withFixture { f ->
        f.connect(fakeUidService { throw IllegalStateException("UID error") })
        f.verifier.drain(); f.main.drain()
        assertFalse(f.phases.contains(EmbeddedSessionPhase.READY))
        assertEquals("UID_VERIFICATION_FAILED", f.snapshots.last().failure?.code)
    }

    @Test fun readyObserverSeesUsableRemoteOnlyAfterUidCompletion() = withFixture { f ->
        f.connect()
        assertEquals(listOf(EmbeddedSessionPhase.CONNECTING), f.phases)
        f.verifier.drain(); f.main.drain()
        assertEquals(EmbeddedSessionPhase.READY, f.phases.last())
        f.session.startSession(allocateAndroid(Surface::class.java))
        f.main.drain()
        assertEquals("SURFACE_INVALID", f.snapshots.last().failure?.code)
    }

    @Test fun missingRemoteStartPublishesOneStructuredFailure() = withFixture { f ->
        f.connect(); f.verifier.drain(); f.main.drain()
        val field = EmbeddedAppSession::class.java.getDeclaredField("remote").apply { isAccessible = true }
        field.set(f.session, null)
        f.session.startSession(allocateAndroid(Surface::class.java))
        f.session.startSession(allocateAndroid(Surface::class.java))
        f.main.drain()
        assertEquals("REMOTE_UNAVAILABLE", f.snapshots.last().failure?.code)
        assertEquals(1, f.phases.count { it == EmbeddedSessionPhase.FAILED })
    }

    @Test fun lateUidAfterStopAndCloseIsDropped() = withFixture { f ->
        f.connect()
        f.session.stop(); f.session.close()
        f.worker.drain(); f.main.drain()
        val terminal = f.snapshots.last()
        f.verifier.drain(); f.main.drain()
        assertEquals(EmbeddedSessionPhase.STOPPED, terminal.phase)
        assertEquals(terminal, f.snapshots.last())
        assertFalse(f.phases.contains(EmbeddedSessionPhase.READY))
    }

    @Test fun sameBinderReplacementGenerationDropsOldUid() = withFixture { f ->
        val service = fakeUidService()
        f.connect(service)
        f.transport.connected(service)
        f.notifications.drain(); f.main.drain()
        val old = f.verifier.tasks.poll()!!
        old.run(); f.main.drain()
        assertFalse(f.phases.contains(EmbeddedSessionPhase.READY))
        f.verifier.drain(); f.main.drain()
        assertEquals(1, f.phases.count { it == EmbeddedSessionPhase.READY })
    }

    @Test fun disconnectBeforeUidCompletionNeverPublishesReady() = withFixture { f ->
        f.connect()
        f.transport.disconnected()
        f.verifier.drain(); f.notifications.drain(); f.main.drain()
        assertFalse(f.phases.contains(EmbeddedSessionPhase.READY))
        assertEquals(EmbeddedSessionPhase.REMOTE_DIED, f.phases.last())
    }

    @Test fun connectReturnsAndCleanupCompletesWhileUidIsBlocked() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val lane = BoundedExecutionLane("test-hung-uid", 1, 0)
        val f = Fixture(lane)
        try {
            f.connect(fakeUidService { entered.countDown(); release.await(); 2000 })
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            f.session.stop(); f.session.close()
            f.worker.drain(); f.main.drain()
            assertEquals(1L, release.count)
            assertEquals(EmbeddedSessionPhase.STOPPED, f.phases.last())
            assertFalse(f.phases.contains(EmbeddedSessionPhase.READY))
        } finally {
            release.countDown()
            f.close()
        }
    }

    @Test fun saturatedVerifierFailsClosedWithoutCallingUidOnCaller() {
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        val lane = BoundedExecutionLane("test-saturated-uid", 2, 0)
        repeat(2) { assertTrue(lane.tryExecute(Runnable { entered.countDown(); release.await() })) }
        val f = Fixture(lane)
        var calls = 0
        try {
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            f.connect(fakeUidService { calls++; 2000 })
            f.main.drain()
            assertEquals(0, calls)
            assertEquals("UID_CAPACITY_EXHAUSTED", f.snapshots.last().failure?.code)
            assertFalse(f.phases.contains(EmbeddedSessionPhase.READY))
        } finally { release.countDown(); f.close() }
    }

    @Test fun detachedVerifierTaskHasNoSessionOrCallbackReference() = withFixture { f ->
        f.connect()
        val task = f.verifier.tasks.single()
        f.session.detachNotifications()
        // Inspect actual retained object graph, including the mailbox's AtomicReference.
        val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
        fun inspect(value: Any?) {
            if (value == null || !visited.add(value)) return
            assertNotSame(f.session, value)
            assertNotSame(f.observer, value)
            when (value) {
                is java.util.concurrent.atomic.AtomicReference<*> -> inspect(value.get())
                is Runnable, is DetachableMailbox<*> -> value.javaClass.declaredFields
                    .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
                    .forEach { it.isAccessible = true; inspect(it.get(value)) }
            }
        }
        inspect(task)
        task.run(); f.main.drain()
        assertFalse(f.phases.contains(EmbeddedSessionPhase.READY))
    }

    @Test fun connectionAdmissionRefusalIsReportedAsValueInsteadOfThrowing() = withFixture { f ->
        f.connect()
        f.transport.onUnbind = { throw IllegalStateException("Uncertain removal") }
        f.session.close(); f.worker.drain(); f.finalization.drain(); f.main.drain()
        val values = mutableListOf<EmbeddedSessionSnapshot>()
        val next = EmbeddedAppSession(
            target = EmbeddedAppTarget("com.example.next", "com.example.next.Main", EmbeddedAppGeometry(900, 675, 320)),
            manager = f.manager, main = f.main, worker = ManualLane(), verifier = f.verifier,
            lifecycleChanged = { values += it }, changed = {},
        )
        assertEquals(null, runCatching { next.connect() }.exceptionOrNull())
        f.main.drain()
        assertEquals("CONNECTION_ADMISSION_REJECTED", values.last().failure?.code)
    }

    private fun withFixture(block: (Fixture) -> Unit) {
        val f = Fixture()
        try { block(f) } finally { f.close() }
    }

    private class Fixture(verifierLane: WorkAdmission? = null) : AutoCloseable {
        val main = ManualLane()
        val worker = ManualLane()
        val notifications = ManualLane()
        val connection = ManualLane()
        val finalization = ManualLane()
        val verifier = ManualLane()
        val transport = FakeConnectionTransport()
        val manager = EmbeddedAppServiceConnectionManager(transport, connection, notifications, finalization)
        val snapshots = mutableListOf<EmbeddedSessionSnapshot>()
        val phases get() = snapshots.map { it.phase }
        val observer: (EmbeddedSessionSnapshot) -> Unit = { snapshots += it }
        val session = EmbeddedAppSession(
            target = EmbeddedAppTarget("com.example.app", "com.example.app.Main", EmbeddedAppGeometry(900, 675, 320)),
            manager = manager, main = main, worker = worker, verifier = verifierLane ?: verifier,
            lifecycleChanged = observer, changed = {},
        )
        fun connect(service: com.trancong.dexworkspacetouch.feature.embeddedapp.remote.IEmbeddedAppService = fakeUidService()) {
            session.connect()
            connection.drain()
            transport.connected(service)
            notifications.drain(); main.drain()
        }
        override fun close() {
            session.close(); worker.drain(); main.drain(); session.detachNotifications()
        }
    }
}
