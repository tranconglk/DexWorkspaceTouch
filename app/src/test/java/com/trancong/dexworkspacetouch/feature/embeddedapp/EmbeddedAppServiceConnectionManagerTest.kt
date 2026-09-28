package com.trancong.dexworkspacetouch.feature.embeddedapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedAppServiceConnectionManagerTest {
    private val empty = EmbeddedAppServiceState(0, 0, 0, 0)
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
}
