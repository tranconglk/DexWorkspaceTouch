package com.trancong.dexworkspacetouch.feature.embeddedapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStopCoordinatorTest {
    @Test fun surfaceStopThenCloseJoinsOneStopBeforeRelease() {
        val coordinator = SessionStopCoordinator()
        assertTrue(coordinator.requestStop())
        coordinator.requestClose()
        assertFalse(coordinator.mayReleaseLease)
        assertFalse(coordinator.requestStop())
        coordinator.stopCompleted()
        assertTrue(coordinator.mayReleaseLease)
        assertEquals(1, coordinator.logicalStopCount)
    }
}
