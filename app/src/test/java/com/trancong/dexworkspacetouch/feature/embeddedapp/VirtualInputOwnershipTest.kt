package com.trancong.dexworkspacetouch.feature.embeddedapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VirtualInputOwnershipTest {
    @Test fun differentSessionsProduceDifferentBoundedNames() {
        val a = virtualInputDeviceName("00000000-0000-0000-0000-000000000001")
        val b = virtualInputDeviceName("00000000-0000-0000-0000-000000000002")
        assertNotEquals(a, b)
        assertTrue(a.startsWith("DWT-VT-"))
        assertTrue(a.length <= 40)
    }

    @Test fun nameDependsOnlyOnOpaqueSessionIdentity() {
        val id = "00000000-0000-0000-0000-000000000003"
        val waze = virtualInputDeviceName(id)
        val calculator = virtualInputDeviceName(id)
        assertEquals(waze, calculator)
        assertFalse(waze.contains("waze", ignoreCase = true))
        assertFalse(waze.contains("calculator", ignoreCase = true))
    }

    @Test fun differentAppsAndSameComponentBothReceiveDistinctNamesBySession() {
        assertNotEquals(virtualInputDeviceName("session-a"), virtualInputDeviceName("session-b"))
        assertNotEquals(virtualInputDeviceName("same-target-1"), virtualInputDeviceName("same-target-2"))
    }

    @Test fun failedCreateWithoutOwnedDescriptorIsNotCreatedAndNotLive() {
        val state = VirtualInputOwnership("DWT-VT-b")
        state.creationAttempted()
        state.creationFailed(setOf("DWT-VT-a"))
        assertEquals(VirtualInputOwnershipPhase.NOT_CREATED, state.phase)
        assertFalse(state.shouldCloseHandle)
        assertFalse(state.isPotentiallyLive)
    }

    @Test fun failedCreateWithExpectedDescriptorRemainsUncertainAndLive() {
        val state = VirtualInputOwnership("DWT-VT-b")
        state.creationAttempted()
        state.creationFailed(setOf("DWT-VT-a", "DWT-VT-b"))
        assertEquals(VirtualInputOwnershipPhase.UNCERTAIN, state.phase)
        assertTrue(state.isPotentiallyLive)
    }

    @Test fun successfulHandleClosesExactlyOnce() {
        val state = VirtualInputOwnership("DWT-VT-b")
        state.creationAttempted(); state.handleReturned()
        assertTrue(state.beginClose())
        state.closeCompleted(descriptorStillPresent = false)
        assertFalse(state.beginClose())
        assertEquals(VirtualInputOwnershipPhase.CLOSED, state.phase)
        assertFalse(state.isPotentiallyLive)
    }
}
