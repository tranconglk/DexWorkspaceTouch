package com.trancong.dexworkspacetouch.feature.embeddedapp

import com.trancong.dexworkspacetouch.feature.embeddedapp.remote.IEmbeddedAppService
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import sun.misc.Unsafe

class EmbeddedAppServiceConnectionManagerTest {
    private val empty = EmbeddedAppServiceState(0, 0, 0, 0)

    @Test fun exactStopCompletionAndDuplicateCannotFinishAnOverlappingStart() {
        var removals = 0
        val state = FinalRemovalCoordinator({ empty }, { removals++ })
        state.acquire("pending")
        val start = state.operationStarted("pending")
        val stop = state.operationStarted("pending")
        assertFalse(state.operationFinished("pending", terminal = true, operation = stop))
        assertFalse(state.operationFinished("pending", terminal = true, operation = stop))
        assertFalse(state.operationFinished("wrong-session", terminal = true, operation = start))
        assertFalse(state.reconcileAbsent("pending", true, true, empty))
        state.release("pending")
        assertEquals(0, removals)
    }

    @Test fun completingStopCannotErasePendingStartOrAuthorizeAbsence() {
        var removals = 0
        val state = FinalRemovalCoordinator({ empty }, { removals++ })
        state.acquire("pending")
        state.operationStarted("pending") // Start A remains pending.
        state.operationStarted("pending") // Stop B overlaps A.
        state.operationFinished("pending", terminal = true)
        assertFalse(state.reconcileAbsent("pending", true, true, empty))
        state.release("pending")
        state.maybeFinalizeServiceRemoval()
        assertEquals(0, removals)
    }
    @Test fun alreadyBoundServiceNotifiesOnlyAfterCallerOwnsLease() {
        val transport = FakeConnectionTransport()
        val lane = ManualLane()
        val manager = EmbeddedAppServiceConnectionManager(transport, lane, lane, lane)
        val service = fakeUidService()
        var callbackCount = 0
        lateinit var lease: EmbeddedAppServiceLease
        val listener = object : EmbeddedAppServiceListener {
            override fun onServiceReady(ready: IEmbeddedAppService) {
                assertFalse("Listener must run outside manager monitor", Thread.holdsLock(manager))
                assertSame(service, ready)
                assertEquals("x", lease.sessionId)
                callbackCount++
            }
            override fun onServiceDisconnected() = Unit
        }
        lease = manager.acquire(EmbeddedAppSessionId("x"), listener)
        lease.connect()
        lane.drain()
        transport.connected(service)
        lane.drain()
        assertEquals(1, callbackCount)
        lease.connect()
        lane.drain()
        assertEquals(2, callbackCount)
    }
    @Test fun connectAcquiresBeforeSharedBindAndNeverStartedCloseReleases() {
        val state = ConnectionLeaseBookkeeper()
        assertTrue(state.acquire("a"))
        assertTrue(state.mayBind("a"))
        assertEquals(1, state.leaseCount)
        assertTrue(state.release("a"))
        assertEquals(0, state.leaseCount)
    }

    @Test fun finalRemovalQueryAndUnbindRunOutsideCoordinatorMonitor() {
        lateinit var state: FinalRemovalCoordinator
        var queryLocked = true
        var removalLocked = true
        state = FinalRemovalCoordinator({
            queryLocked = Thread.holdsLock(state)
            empty
        }, {
            removalLocked = Thread.holdsLock(state)
        })
        state.acquire("a")
        state.release("a")
        assertFalse(queryLocked)
        assertFalse(removalLocked)
    }

    @Test fun listenerCanReenterAndAcquireAnotherLease() {
        val lane = ManualLane()
        val transport = FakeConnectionTransport()
        val manager = EmbeddedAppServiceConnectionManager(transport, lane, lane, lane)
        var reentered = false
        val lease = manager.acquire(EmbeddedAppSessionId("a"), object : EmbeddedAppServiceListener {
            override fun onServiceReady(service: IEmbeddedAppService) {
                assertFalse(Thread.holdsLock(manager))
                manager.acquire(EmbeddedAppSessionId("b"), object : EmbeddedAppServiceListener {})
                reentered = true
            }
        })
        lease.connect(); lane.drain(); transport.connected(fakeUidService()); lane.drain()
        assertTrue(reentered)
    }

    @Test fun queuedOldReadyAfterReplacementOrReleaseIsDropped() {
        val transportLane = ManualLane()
        val notifications = ManualLane()
        val transport = FakeConnectionTransport()
        val manager = EmbeddedAppServiceConnectionManager(transport, transportLane, notifications, ManualLane())
        var ready = 0
        val lease = manager.acquire(EmbeddedAppSessionId("a"), object : EmbeddedAppServiceListener {
            override fun onServiceReady(service: IEmbeddedAppService) { ready++ }
        })
        lease.connect(); transportLane.drain()
        val sameBinder = fakeUidService()
        transport.connected(sameBinder); transport.connected(sameBinder)
        notifications.drain()
        assertEquals(1, ready)
        lease.connect(); lease.close()
        notifications.drain()
        assertEquals(1, ready)
    }

    @Test fun hungListenerCannotBlockReleaseOrDisconnectBookkeeping() {
        val lane = ManualLane()
        val transport = FakeConnectionTransport()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val notified = ManualLane()
        val manager = EmbeddedAppServiceConnectionManager(transport, lane, notified, ManualLane())
        val service = fakeUidService()
        val lease = manager.acquire(EmbeddedAppSessionId("a"), object : EmbeddedAppServiceListener {
            override fun onServiceReady(service: IEmbeddedAppService) { entered.countDown(); release.await() }
        })
        lease.connect(); lane.drain(); transport.connected(service)
        val callback = Thread { notified.drain() }.apply { start() }
        try {
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            transport.disconnected()
            assertEquals(null, manager.generationFor(service))
            lease.close()
            val replacement = manager.acquire(EmbeddedAppSessionId("a"), object : EmbeddedAppServiceListener {})
            assertTrue(replacement.registration != lease.registration)
        } finally { release.countDown(); callback.join(2000) }
    }

    @Test fun managerQueryAndFinalUnbindAreOutsideLocksAndRemovalReservesAdmission() {
        val lane = ManualLane()
        val finalization = ManualLane()
        val transport = FakeConnectionTransport()
        val manager = EmbeddedAppServiceConnectionManager(transport, lane, lane, finalization)
        var queryLocked = true
        var unbindLocked = true
        var rejected = false
        transport.onQuery = { queryLocked = Thread.holdsLock(manager); empty }
        transport.onUnbind = {
            unbindLocked = Thread.holdsLock(manager)
            rejected = runCatching {
                manager.acquire(EmbeddedAppSessionId("new"), object : EmbeddedAppServiceListener {})
            }.isFailure
        }
        val lease = manager.acquire(EmbeddedAppSessionId("a"), object : EmbeddedAppServiceListener {})
        lease.connect(); lane.drain(); transport.connected(fakeUidService()); lane.drain()
        lease.close()
        assertEquals(0, transport.removals)
        finalization.drain()
        assertFalse(queryLocked); assertFalse(unbindLocked); assertTrue(rejected)
        assertEquals(1, transport.removals)
    }

    @Test fun saturatedTransportProducesStructuredFailureWithoutRunningTransport() {
        val notifications = ManualLane()
        val transport = FakeConnectionTransport()
        val manager = EmbeddedAppServiceConnectionManager(transport, WorkAdmission { false }, notifications, ManualLane())
        val events = mutableListOf<EmbeddedConnectionEvent>()
        val lease = manager.acquire(EmbeddedAppSessionId("a"), object : EmbeddedAppServiceListener {
            override fun onConnectionEvent(event: EmbeddedConnectionEvent) { events += event }
        })
        lease.connect(); notifications.drain()
        assertEquals(0, transport.bindings.size)
        assertEquals("CONNECTION_CAPACITY_EXHAUSTED", (events.single() as EmbeddedConnectionEvent.Failed).code)
    }

    @Test fun lateBindAfterLeaseReleaseCanFinalizeWithoutRevivingListener() {
        val lane = ManualLane()
        val finalization = ManualLane()
        val transport = FakeConnectionTransport()
        val manager = EmbeddedAppServiceConnectionManager(transport, lane, lane, finalization)
        var ready = 0
        val lease = manager.acquire(EmbeddedAppSessionId("a"), object : EmbeddedAppServiceListener {
            override fun onServiceReady(service: IEmbeddedAppService) { ready++ }
        })
        lease.connect(); lane.drain()
        lease.close(); finalization.drain()
        assertEquals(0, transport.removals)
        transport.connected(fakeUidService()); lane.drain(); finalization.drain()
        assertEquals(0, ready)
        assertEquals(1, transport.removals)
    }

    @Test fun realManagerStaleQueryDoesNotRemoveNewLeaseEvenWithSameBinderReplacement() {
        val lane = ManualLane()
        val finalization = ManualLane()
        val transport = FakeConnectionTransport()
        val manager = EmbeddedAppServiceConnectionManager(transport, lane, lane, finalization)
        val lease = manager.acquire(EmbeddedAppSessionId("a"), object : EmbeddedAppServiceListener {})
        val service = fakeUidService()
        lease.connect(); lane.drain(); transport.connected(service); lane.drain()
        lease.close()
        transport.onQuery = {
            assertFalse(Thread.holdsLock(manager))
            manager.acquire(EmbeddedAppSessionId("b"), object : EmbeddedAppServiceListener {})
            transport.connected(service)
            empty
        }
        finalization.drain()
        assertEquals(0, transport.removals)
        assertTrue(manager.generationFor(service) != null)
    }

    @Test fun failedUnbindKeepsAdmissionFailClosed() {
        val lane = ManualLane()
        val finalization = ManualLane()
        val transport = FakeConnectionTransport()
        val manager = EmbeddedAppServiceConnectionManager(transport, lane, lane, finalization)
        val lease = manager.acquire(EmbeddedAppSessionId("a"), object : EmbeddedAppServiceListener {})
        lease.connect(); lane.drain(); transport.connected(fakeUidService()); lane.drain()
        transport.onUnbind = { throw IllegalStateException("Uncertain unbind") }
        lease.close(); finalization.drain()
        assertTrue(runCatching {
            manager.acquire(EmbeddedAppSessionId("b"), object : EmbeddedAppServiceListener {})
        }.isFailure)
        assertEquals(0, transport.removals)
    }

    @Test fun scopedReconciliationQueriesOutsideManagerLockAndRejectsInventoryChange() {
        val lane = ManualLane()
        val finalization = ManualLane()
        val transport = FakeConnectionTransport()
        val manager = EmbeddedAppServiceConnectionManager(transport, lane, lane, finalization)
        val lease = manager.acquire(EmbeddedAppSessionId("a"), object : EmbeddedAppServiceListener {})
        val service = fakeUidService()
        lease.connect(); lane.drain(); transport.connected(service); lane.drain()
        lease.operationStarted(); lease.operationFinished(false)
        var absentLocked = true
        var queryLocked = true
        lateinit var newer: EmbeddedAppServiceLease
        transport.onAbsent = { absentLocked = Thread.holdsLock(manager); true }
        transport.onQuery = {
            queryLocked = Thread.holdsLock(manager)
            newer = manager.acquire(EmbeddedAppSessionId("b"), object : EmbeddedAppServiceListener {})
            empty
        }
        assertFalse(manager.reconcileAbsentSession(lease, service, manager.generationFor(service)))
        assertFalse(absentLocked); assertFalse(queryLocked)
        lease.close(); newer.close(); finalization.drain()
        assertEquals(0, transport.removals)
    }

    @Test fun staleEmptyQueryCannotRemoveNewLease() {
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        var removals = 0
        val state = FinalRemovalCoordinator({
            entered.countDown()
            finish.await(2, TimeUnit.SECONDS)
            empty
        }, { removals++ })
        state.acquire("a")
        val query = Thread { state.release("a") }.apply { start() }
        try {
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            val admitted = CountDownLatch(1)
            val acquire = Thread { state.acquire("b"); admitted.countDown() }.apply { start() }
            assertTrue("Acquire must not wait for remote query", admitted.await(300, TimeUnit.MILLISECONDS))
            finish.countDown()
            query.join(1000)
            acquire.join(1000)
            assertEquals(0, removals)
        } finally {
            finish.countDown()
            query.join(3000)
        }
    }

    @Test fun inventoryChangeDuringQuerySchedulesFreshEvidenceWhenInventoryIsEmptyAgain() {
        var queries = 0
        var removals = 0
        lateinit var state: FinalRemovalCoordinator
        state = FinalRemovalCoordinator({
            queries++
            if (queries == 1) { state.acquire("b"); state.release("b") }
            empty
        }, { removals++ })
        state.acquire("a"); state.release("a")
        assertEquals(2, queries)
        assertEquals(1, removals)
    }

    @Test fun twoConnectedSessionsUseTwoLeasesAndClosingBRetainsA() {
        val state = ConnectionLeaseBookkeeper()
        state.acquire("a"); state.acquire("b")
        assertEquals(2, state.leaseCount)
        assertFalse(state.release("b"))
        assertEquals(1, state.leaseCount)
        assertTrue(state.mayBind("a"))
    }

    @Test fun duplicateAcquireDoesNotCreateAnotherLease() {
        val state = ConnectionLeaseBookkeeper()
        assertTrue(state.acquire("a"))
        assertFalse(state.acquire("a"))
        assertEquals(1, state.leaseCount)
    }

    @Test fun zeroLeasesWhileStopInFlightDoesNotRemoveThenCompletionDoesOnce() {
        var queries = 0; var removals = 0
        val state = FinalRemovalCoordinator({ queries++; empty }, { removals++ })
        state.acquire("a"); state.operationStarted("a"); state.release("a")
        assertEquals(0, queries); assertEquals(0, removals)
        state.operationFinished("a", terminal = true)
        assertEquals(1, queries); assertEquals(1, removals)
        state.operationFinished("a", terminal = true); state.maybeFinalizeServiceRemoval()
        assertEquals(1, removals)
    }

    @Test fun nonEmptyOrFailedAuthoritativeStateRetainsService() {
        var removals = 0
        val live = FinalRemovalCoordinator({ EmbeddedAppServiceState(0, 0, 0, 1) }, { removals++ })
        live.acquire("a"); live.release("a")
        val failed = FinalRemovalCoordinator({ null }, { removals++ })
        failed.acquire("b"); failed.release("b")
        assertEquals(0, removals)
    }

    @Test fun connectedNeverStartedCanFinalizeButBReleaseCannotRemoveWhileAExists() {
        var removals = 0
        val state = FinalRemovalCoordinator({ empty }, { removals++ })
        state.acquire("a"); state.acquire("b"); state.release("b")
        assertEquals(0, removals)
        state.release("a")
        assertEquals(1, removals)
    }

    @Test fun completedRemovalAllowsANewConnectionCycle() {
        var removals = 0
        val state = FinalRemovalCoordinator({ empty }, { removals++ })
        assertTrue(state.acquire("a"))
        state.release("a")
        assertEquals(1, removals)

        assertTrue(state.acquire("b"))
        assertEquals(1, state.leaseCount)
        state.release("b")
        assertEquals(2, removals)
    }

    @Test fun verifiedAbsentSessionCanClearNonTerminalAndFinalizeAfterRelease() {
        var removals = 0
        val state = FinalRemovalCoordinator({ empty }, { removals++ })
        state.acquire("x")
        state.operationStarted("x")
        state.operationFinished("x", terminal = false)

        assertTrue(state.reconcileAbsent("x", sameService = true, sessionAbsent = true, remoteState = empty))
        assertEquals(0, removals)
        state.release("x")
        state.maybeFinalizeServiceRemoval()

        assertEquals(1, removals)
    }

    @Test fun unknownSessionAloneAndFailedAggregateRemainNonTerminal() {
        var removals = 0
        val state = FinalRemovalCoordinator({ empty }, { removals++ })
        state.acquire("x"); state.operationStarted("x")
        state.operationFinished("x", terminal = false); state.release("x")

        assertFalse(state.reconcileAbsent("x", true, false, empty))
        assertFalse(state.reconcileAbsent("x", true, true, null))
        assertEquals(0, removals)
    }

    @Test fun changedGenerationOrNonEmptyAggregateCannotReconcile() {
        var removals = 0
        val state = FinalRemovalCoordinator({ empty }, { removals++ })
        state.acquire("x"); state.operationStarted("x")
        state.operationFinished("x", terminal = false); state.release("x")

        assertFalse(state.reconcileAbsent("x", false, true, empty))
        assertFalse(state.reconcileAbsent("x", true, true, EmbeddedAppServiceState(1, 0, 0, 1)))
        assertEquals(0, removals)
    }

    @Test fun inFlightOperationOrExternalSessionPreventsRemoval() {
        var removals = 0
        val state = FinalRemovalCoordinator({ EmbeddedAppServiceState(1, 0, 0, 1) }, { removals++ })
        state.acquire("x"); state.acquire("y")
        state.operationStarted("x"); state.operationFinished("x", terminal = false)
        state.operationStarted("y")
        assertFalse(state.reconcileAbsent("x", true, true, empty))
        state.operationFinished("y", terminal = true)
        assertTrue(state.reconcileAbsent("x", true, true, empty))
        state.release("x"); state.release("y")
        assertEquals(0, removals)
    }

    @Test fun repeatedReconciliationIsIdempotent() {
        var removals = 0
        val state = FinalRemovalCoordinator({ empty }, { removals++ })
        state.acquire("x"); state.operationStarted("x")
        state.operationFinished("x", terminal = false); state.release("x")
        assertTrue(state.reconcileAbsent("x", true, true, empty))
        assertFalse(state.reconcileAbsent("x", true, true, empty))
        state.maybeFinalizeServiceRemoval()
        assertEquals(1, removals)
    }

    private fun <T> allocate(type: Class<T>): T {
        val f = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        return (f.get(null) as Unsafe).allocateInstance(type) as T
    }

    private fun set(target: Any, name: String, value: Any) {
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }
}
