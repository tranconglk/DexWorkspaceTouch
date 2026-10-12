package com.trancong.dexworkspacetouch.feature.physicalbutton

import org.junit.Assert.assertEquals
import org.junit.Test

class PhysicalButtonKeyHandlerTest {
    private val handler = PhysicalButtonKeyHandler()
    private val down = PhysicalButtonKeyEvent(24, 0, 0, 7, 100L, true, "remote-descriptor")

    @Test fun missingBinding_passesThroughEntirePress() {
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handler.handle(down, true))
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handler.handle(down.copy(repeatCount = 1), true))
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handler.handle(down.copy(action = 1), true))
    }

    @Test fun blankBinding_neverAcceptsBlankDescriptor() {
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH,
            handler.handle(down.copy(deviceDescriptor = ""), true, ""))
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH,
            handler.handle(down.copy(deviceDescriptor = "  "), true, "  "))
    }

    @Test fun reconnectWithNewDeviceId_matchesDescriptor() {
        assertEquals(PhysicalButtonKeyDecision.TRACK_PRESS,
            handler.handle(down.copy(deviceId = 41), true, "remote-descriptor"))
        assertEquals(PhysicalButtonKeyDecision.SHORT_PRESS,
            handler.handle(down.copy(deviceId = 41, action = 1), true, "remote-descriptor"))
    }

    @Test fun reusedDeviceId_withDifferentDescriptor_passesThrough() {
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH,
            handler.handle(down.copy(deviceDescriptor = "other-remote"), true, "remote-descriptor"))
    }

    @Test fun externalVolumeUpDown_triggers() {
        assertEquals(PhysicalButtonKeyDecision.TRACK_PRESS, handler.handle(down, true, "remote-descriptor"))
    }
    @Test fun builtInVolumeUp_passesThrough() {
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handler.handle(down.copy(isExternal = false), true, "remote-descriptor"))
    }
    @Test fun configuredVolumeDown_triggers() {
        assertEquals(PhysicalButtonKeyDecision.TRACK_PRESS, handler.handle(down.copy(keyCode = 25), true, "remote-descriptor"))
    }
    @Test fun overlappingButtons_haveIndependentConsumedPresses() {
        val volumeDown = down.copy(keyCode = 25)
        assertEquals(PhysicalButtonKeyDecision.TRACK_PRESS, handler.handle(down, true, "remote-descriptor"))
        assertEquals(PhysicalButtonKeyDecision.TRACK_PRESS, handler.handle(volumeDown, true, "remote-descriptor"))
        assertEquals(PhysicalButtonKeyDecision.SHORT_PRESS, handler.handle(down.copy(action = 1), true, "remote-descriptor"))
        assertEquals(PhysicalButtonKeyDecision.CONSUME, handler.handle(volumeDown.copy(repeatCount = 1), true, "remote-descriptor"))
        assertEquals(PhysicalButtonKeyDecision.SHORT_PRESS, handler.handle(volumeDown.copy(action = 1), true, "remote-descriptor"))
    }
    @Test fun volumeDownPress_consumesRepeatAndUpOnlyOnce() {
        val volumeDown = down.copy(keyCode = 25)
        assertEquals(PhysicalButtonKeyDecision.TRACK_PRESS, handler.handle(volumeDown, true, "remote-descriptor"))
        assertEquals(PhysicalButtonKeyDecision.CONSUME, handler.handle(volumeDown, true, "remote-descriptor"))
        assertEquals(PhysicalButtonKeyDecision.CONSUME, handler.handle(volumeDown.copy(repeatCount = 1), true, "remote-descriptor"))
        assertEquals(PhysicalButtonKeyDecision.SHORT_PRESS, handler.handle(volumeDown.copy(action = 1), true, "remote-descriptor"))
    }
    @Test fun mediaKey_passesThrough() {
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handler.handle(down.copy(keyCode = 85), true, "remote-descriptor"))
    }
    @Test fun orphanUp_passesThroughWithoutTrigger() {
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handler.handle(down.copy(action = 1), true, "remote-descriptor"))
    }
    @Test fun orphanRepeat_passesThroughWithoutTrigger() {
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handler.handle(down.copy(repeatCount = 1), true, "remote-descriptor"))
    }
    @Test fun disabled_passesThrough() {
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handler.handle(down, false, "remote-descriptor"))
    }
    @Test fun consumedPress_consumesRepeatsAndUpWithoutSecondTrigger() {
        assertEquals(PhysicalButtonKeyDecision.TRACK_PRESS, handler.handle(down, true, "remote-descriptor"))
        assertEquals(PhysicalButtonKeyDecision.CONSUME, handler.handle(down.copy(repeatCount = 1), true, "remote-descriptor"))
        assertEquals(PhysicalButtonKeyDecision.CONSUME, handler.handle(down, true, "remote-descriptor"))
        assertEquals(PhysicalButtonKeyDecision.SHORT_PRESS, handler.handle(down.copy(action = 1), true, "remote-descriptor"))
        assertEquals(PhysicalButtonKeyDecision.TRACK_PRESS, handler.handle(down.copy(downTime = 200), true, "remote-descriptor"))
    }
    @Test fun phoneVolumeUpDuringRemotePress_stillPassesThrough() {
        handler.handle(down, true, "remote-descriptor")
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH,
            handler.handle(down.copy(deviceId = 1, isExternal = false), true, "remote-descriptor"))
    }
    @Test fun descriptorLock_rejectsDifferentOrUnknownDevice() {
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handler.handle(down, true, "other-remote"))
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH,
            handler.handle(down.copy(deviceDescriptor = null), true, "remote-descriptor"))
        assertEquals(PhysicalButtonKeyDecision.TRACK_PRESS, handler.handle(down, true, "remote-descriptor"))
    }
    @Test fun disabledDuringPress_finishesConsumedPairThenPassesNextPress() {
        handler.handle(down, true, "remote-descriptor")
        assertEquals(PhysicalButtonKeyDecision.CONSUME, handler.handle(down.copy(repeatCount = 1), false, "remote-descriptor"))
        assertEquals(PhysicalButtonKeyDecision.CONSUME, handler.handle(down.copy(action = 1), false, "remote-descriptor"))
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handler.handle(down.copy(downTime = 200), false, "remote-descriptor"))
    }
    @Test fun staleRelease_doesNotReleaseNewPress() {
        handler.handle(down, true, "remote-descriptor")
        assertEquals(PhysicalButtonKeyDecision.TRACK_PRESS, handler.handle(down.copy(downTime = 200), true, "remote-descriptor"))
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handler.handle(down.copy(action = 1), true, "remote-descriptor"))
        assertEquals(PhysicalButtonKeyDecision.SHORT_PRESS, handler.handle(down.copy(downTime = 200, action = 1), true, "remote-descriptor"))
    }

    @Test fun virtualVolumeUp_passesThroughEvenWithMatchingDescriptor() {
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH,
            handler.handle(down.copy(isVirtual = true), true, "remote-descriptor"))
    }

    @Test fun descriptorMatch_isExact() {
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handler.handle(down, true, "remote-descriptor "))
    }

    @Test fun reusedIdentity_doesNotConsumeAnotherDeviceRelease() {
        handler.handle(down, true, "remote-descriptor")
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH,
            handler.handle(down.copy(action = 1, deviceDescriptor = "other-remote"), true, "remote-descriptor"))
        assertEquals(PhysicalButtonKeyDecision.SHORT_PRESS,
            handler.handle(down.copy(action = 1), true, "remote-descriptor"))
    }
}
