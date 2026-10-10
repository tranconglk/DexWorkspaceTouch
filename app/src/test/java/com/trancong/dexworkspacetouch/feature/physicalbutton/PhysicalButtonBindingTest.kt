package com.trancong.dexworkspacetouch.feature.physicalbutton

import org.junit.Assert.assertEquals
import org.junit.Test

class PhysicalButtonBindingTest {
    private val handler = PhysicalButtonKeyHandler()
    private val down = PhysicalButtonKeyEvent(24, 0, 0, 7, 100L, true, "remote-descriptor")

    private fun bind(event: PhysicalButtonKeyEvent = down) =
        handler.handle(event, enabled = false, bindingRequested = true)

    @Test fun bindingDown_capturesEvenWhenFeatureDisabled() {
        assertEquals(PhysicalButtonKeyDecision.BIND_REMOTE, bind())
    }

    @Test fun bindingPress_consumesEntirePairAndOnlyNextPressTriggers() {
        assertEquals(PhysicalButtonKeyDecision.BIND_REMOTE, bind())
        assertEquals(PhysicalButtonKeyDecision.CONSUME,
            handler.handle(down.copy(repeatCount = 1), true, "remote-descriptor"))
        assertEquals(PhysicalButtonKeyDecision.CONSUME, handler.handle(down, true, "remote-descriptor"))
        assertEquals(PhysicalButtonKeyDecision.CONSUME,
            handler.handle(down.copy(action = 1), true, "remote-descriptor"))
        assertEquals(PhysicalButtonKeyDecision.TRIGGER_WORKSPACE,
            handler.handle(down.copy(downTime = 200), true, "remote-descriptor"))
    }

    @Test fun binding_internalKeyPassesThrough() {
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, bind(down.copy(isExternal = false)))
    }

    @Test fun binding_virtualKeyPassesThrough() {
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, bind(down.copy(isVirtual = true)))
    }

    @Test fun binding_otherKeyPassesThrough() {
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, bind(down.copy(keyCode = 25)))
    }

    @Test fun binding_orphanUpAndRepeatPassThrough() {
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, bind(down.copy(action = 1)))
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, bind(down.copy(repeatCount = 1)))
    }

    @Test fun binding_missingAndBlankDescriptorsPassThrough() {
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, bind(down.copy(deviceDescriptor = null)))
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, bind(down.copy(deviceDescriptor = "  ")))
    }

    @Test fun rebinding_hasPriorityOverExistingWorkspaceTrigger() {
        assertEquals(PhysicalButtonKeyDecision.BIND_REMOTE,
            handler.handle(down, true, "remote-descriptor", bindingRequested = true))
    }

    @Test fun cancellingAfterDown_finishesConsumedPairWithoutTrigger() {
        bind()
        assertEquals(PhysicalButtonKeyDecision.CONSUME, handler.handle(down.copy(action = 1), false))
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handler.handle(down.copy(downTime = 200), true))
    }

    @Test fun anotherRemoteDuringBindingPress_passesThroughWhenNotLinked() {
        bind()
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH,
            handler.handle(down.copy(deviceId = 8, deviceDescriptor = "other-remote"), true, "remote-descriptor"))
    }
}
