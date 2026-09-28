package com.trancong.dexworkspacetouch.feature.embeddedapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionConnectionCoordinatorTest {
    @Test fun synchronousReadyDuringAcquireWinsOverConnecting() {
        val state = SessionConnectionCoordinator()
        assertTrue(state.beginConnect())
        state.serviceReady()
        assertEquals(SessionConnectionPhase.READY, state.phase)
        assertEquals(listOf(SessionConnectionPhase.CONNECTING, SessionConnectionPhase.READY), state.history)
    }

    @Test fun historyNeverRegressesFromReadyToConnectingInOneAttempt() {
        val state = SessionConnectionCoordinator()
        state.beginConnect(); state.serviceReady(); state.beginConnect()
        assertFalse(state.history.zipWithNext().any {
            it.first == SessionConnectionPhase.READY && it.second == SessionConnectionPhase.CONNECTING
        })
    }

    @Test fun unavailableBinderTransitionsConnectingThenLaterReady() {
        val state = SessionConnectionCoordinator()
        state.beginConnect()
        assertEquals(SessionConnectionPhase.CONNECTING, state.phase)
        state.serviceReady()
        assertEquals(SessionConnectionPhase.READY, state.phase)
    }

    @Test fun twoSessionsCanShareReadyBinderWithoutRegressingEither() {
        var binds = 0
        val a = SessionConnectionCoordinator()
        val b = SessionConnectionCoordinator()
        if (a.beginConnect()) binds++
        a.serviceReady()
        b.beginConnect()
        b.serviceReady()
        assertEquals(SessionConnectionPhase.READY, a.phase)
        assertEquals(SessionConnectionPhase.READY, b.phase)
        assertEquals(1, binds)
    }

    @Test fun repeatedConnectWhileReadyIsIdempotent() {
        val state = SessionConnectionCoordinator()
        state.beginConnect(); state.serviceReady()
        assertFalse(state.beginConnect())
        assertEquals(SessionConnectionPhase.READY, state.phase)
    }

    @Test fun synchronousCallbackIsRetainedAndBinderDeathIsFailClosed() {
        val state = SessionConnectionCoordinator()
        state.beginConnect(); state.serviceReady()
        state.serviceDied()
        assertEquals(SessionConnectionPhase.REMOTE_DIED, state.phase)
        assertFalse(state.beginConnect())
    }
}
