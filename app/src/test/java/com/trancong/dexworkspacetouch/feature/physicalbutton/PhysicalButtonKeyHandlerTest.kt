package com.trancong.dexworkspacetouch.feature.physicalbutton

import org.junit.Assert.assertEquals
import org.junit.Test

class PhysicalButtonKeyHandlerTest {
    private val handler = PhysicalButtonKeyHandler()
    private val down = PhysicalButtonKeyEvent(24, 0, 0, 7, 100L, true, "remote-descriptor")

    @Test fun externalVolumeUpDown_triggers() {
        assertEquals(PhysicalButtonKeyDecision.TRIGGER_WORKSPACE, handler.handle(down, true))
    }
    @Test fun builtInVolumeUp_passesThrough() {
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handler.handle(down.copy(isExternal = false), true))
    }
    @Test fun volumeDown_passesThrough() {
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handler.handle(down.copy(keyCode = 25), true))
    }
    @Test fun mediaKey_passesThrough() {
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handler.handle(down.copy(keyCode = 85), true))
    }
    @Test fun orphanUp_passesThroughWithoutTrigger() {
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handler.handle(down.copy(action = 1), true))
    }
    @Test fun orphanRepeat_passesThroughWithoutTrigger() {
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handler.handle(down.copy(repeatCount = 1), true))
    }
    @Test fun disabled_passesThrough() {
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handler.handle(down, false))
    }
    @Test fun consumedPress_consumesRepeatsAndUpWithoutSecondTrigger() {
        assertEquals(PhysicalButtonKeyDecision.TRIGGER_WORKSPACE, handler.handle(down, true))
        assertEquals(PhysicalButtonKeyDecision.CONSUME, handler.handle(down.copy(repeatCount = 1), true))
        assertEquals(PhysicalButtonKeyDecision.CONSUME, handler.handle(down, true))
        assertEquals(PhysicalButtonKeyDecision.CONSUME, handler.handle(down.copy(action = 1), true))
        assertEquals(PhysicalButtonKeyDecision.TRIGGER_WORKSPACE, handler.handle(down.copy(downTime = 200), true))
    }
    @Test fun phoneVolumeUpDuringRemotePress_stillPassesThrough() {
        handler.handle(down, true)
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH,
            handler.handle(down.copy(deviceId = 1, isExternal = false), true))
    }
    @Test fun descriptorLock_rejectsDifferentOrUnknownDevice() {
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handler.handle(down, true, "other-remote"))
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH,
            handler.handle(down.copy(deviceDescriptor = null), true, "remote-descriptor"))
        assertEquals(PhysicalButtonKeyDecision.TRIGGER_WORKSPACE, handler.handle(down, true, "remote-descriptor"))
    }
    @Test fun disabledDuringPress_finishesConsumedPairThenPassesNextPress() {
        handler.handle(down, true)
        assertEquals(PhysicalButtonKeyDecision.CONSUME, handler.handle(down.copy(repeatCount = 1), false))
        assertEquals(PhysicalButtonKeyDecision.CONSUME, handler.handle(down.copy(action = 1), false))
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handler.handle(down.copy(downTime = 200), false))
    }
    @Test fun staleRelease_doesNotReleaseNewPress() {
        handler.handle(down, true)
        assertEquals(PhysicalButtonKeyDecision.TRIGGER_WORKSPACE, handler.handle(down.copy(downTime = 200), true))
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handler.handle(down.copy(action = 1), true))
        assertEquals(PhysicalButtonKeyDecision.CONSUME, handler.handle(down.copy(downTime = 200, action = 1), true))
    }
}
