package com.trancong.dexworkspacetouch.feature.embeddeddual

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedDualAppControllerTest {
    @Test fun stoppingBDoesNotChangeA() {
        val controller = EmbeddedDualAppController()
        controller.updateA(true); controller.updateB(true); controller.updateB(false)
        assertTrue(controller.state.aActive)
        assertFalse(controller.state.bActive)
    }

    @Test fun disposalIsIdempotent() {
        val controller = EmbeddedDualAppController()
        assertTrue(controller.dispose())
        assertFalse(controller.dispose())
    }
}
