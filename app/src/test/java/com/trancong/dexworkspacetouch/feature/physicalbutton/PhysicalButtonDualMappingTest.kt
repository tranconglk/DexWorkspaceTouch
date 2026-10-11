package com.trancong.dexworkspacetouch.feature.physicalbutton

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhysicalButtonDualMappingTest {
    private val handler = PhysicalButtonKeyHandler()
    private val up = PhysicalButtonKeyEvent(24, 0, 0, 7, 100L, true, "remote-descriptor")
    private val down = up.copy(keyCode = 25)
    private val configuration = PhysicalButtonConfiguration(true, "up-id", "down-id", "remote-descriptor", "AB Shutter3")
    private val existing = setOf("up-id", "down-id")

    @Test fun bothButtons_resolveIndependentWorkspaceIds() {
        assertEquals("up-id", configuration.workspaceForKey(24))
        assertEquals("down-id", configuration.workspaceForKey(25))
        assertNull(configuration.workspaceForKey(85))
        assertEquals(PhysicalButtonKeyDecision.TRIGGER_WORKSPACE, handler.handle(up, configuration, existing))
        assertEquals(PhysicalButtonKeyDecision.TRIGGER_WORKSPACE, handler.handle(down, configuration, existing))
    }

    @Test fun unconfiguredButton_passesEntirePressWithoutOtherButtonFallback() {
        for (keyCode in listOf(24, 25)) {
            val localHandler = PhysicalButtonKeyHandler()
            val local = if (keyCode == 24) configuration.copy(volumeUpWorkspaceId = null)
                else configuration.copy(volumeDownWorkspaceId = null)
            val event = up.copy(keyCode = keyCode)
            for (part in listOf(event, event.copy(repeatCount = 1), event.copy(action = 1))) {
                assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, localHandler.handle(part, local, existing))
            }
            assertEquals(PhysicalButtonKeyDecision.TRIGGER_WORKSPACE,
                localHandler.handle(up.copy(keyCode = if (keyCode == 24) 25 else 24), local, existing))
        }
    }

    @Test fun deletedWorkspace_passesEntirePressWithoutFallback() {
        for (keyCode in listOf(24, 25)) {
            val event = up.copy(keyCode = keyCode)
            val available = if (keyCode == 24) setOf("down-id") else setOf("up-id")
            for (part in listOf(event, event.copy(repeatCount = 1), event.copy(action = 1))) {
                assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handler.handle(part, configuration, available))
            }
        }
    }

    @Test fun unavailableWorkspaceList_passesThroughUntilLoaded() {
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handler.handle(up, configuration, emptySet()))
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handler.handle(down, configuration, emptySet()))
    }

    @Test fun removingMappingDuringConsumedPress_finishesPairThenPassesNextPress() {
        assertEquals(PhysicalButtonKeyDecision.TRIGGER_WORKSPACE, handler.handle(down, configuration, existing))
        val cleared = configuration.copy(volumeDownWorkspaceId = null)
        assertEquals(PhysicalButtonKeyDecision.CONSUME, handler.handle(down.copy(repeatCount = 1), cleared, existing))
        assertEquals(PhysicalButtonKeyDecision.CONSUME, handler.handle(down.copy(action = 1), cleared, existing))
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handler.handle(down.copy(downTime = 200L), cleared, existing))
    }

    @Test fun bothButtons_enforceAllIdentityAndPressGates() {
        for (event in listOf(up, down)) {
            for (rejected in listOf(
                event.copy(isExternal = false), event.copy(isVirtual = true), event.copy(deviceDescriptor = null),
                event.copy(deviceDescriptor = "remote-descriptor "), event.copy(deviceDescriptor = "other-remote"),
                event.copy(action = 1), event.copy(repeatCount = 1),
            )) {
                assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handler.handle(rejected, configuration, existing))
            }
            assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH,
                handler.handle(event, configuration.copy(enabled = false), existing))
            assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH,
                handler.handle(event, configuration.copy(deviceDescriptor = null), existing))
        }
    }
}
