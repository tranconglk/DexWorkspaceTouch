package com.trancong.dexworkspacetouch.feature.embeddedapp

import com.trancong.dexworkspacetouch.feature.embeddedapp.remote.IEmbeddedAppService
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import sun.misc.Unsafe

class EmbeddedAppServiceConnectionManagerTest {
    private val empty = EmbeddedAppServiceState(0, 0, 0, 0)
    @Test fun alreadyBoundServiceNotifiesOnlyAfterCallerOwnsLease() {
        val manager = allocate(EmbeddedAppServiceConnectionManager::class.java)
        val bookkeeper = ConnectionLeaseBookkeeper().apply { acquire("x") }
        val service = Proxy.newProxyInstance(
            IEmbeddedAppService::class.java.classLoader,
            arrayOf(IEmbeddedAppService::class.java),
        ) { _, _, _ -> null } as IEmbeddedAppService
        var callbackCount = 0
        lateinit var lease: EmbeddedAppServiceLease
        val listener = object : EmbeddedAppServiceListener {
            override fun onServiceReady(ready: IEmbeddedAppService) {
                assertSame(service, ready)
                assertEquals("x", lease.sessionId)
                callbackCount++
            }
            override fun onServiceDisconnected() = Unit
        }
        set(manager, "bookkeeper", bookkeeper)
        set(manager, "listeners", linkedMapOf("x" to listener))
        set(manager, "remote", service)

        lease = EmbeddedAppServiceLease(manager, "x")
        lease.connect()

        assertEquals(1, callbackCount)
    }
    @Test fun connectAcquiresBeforeSharedBindAndNeverStartedCloseReleases() {
        val state = ConnectionLeaseBookkeeper()
        assertTrue(state.acquire("a"))
        assertTrue(state.mayBind("a"))
        assertEquals(1, state.leaseCount)
        assertTrue(state.release("a"))
        assertEquals(0, state.leaseCount)
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
