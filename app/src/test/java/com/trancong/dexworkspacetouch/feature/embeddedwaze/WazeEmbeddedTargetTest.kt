package com.trancong.dexworkspacetouch.feature.embeddedwaze

import org.junit.Assert.assertEquals
import org.junit.Test

class WazeEmbeddedTargetTest {
    @Test fun suppliesProvenComponentAndGeometry() {
        assertEquals("com.waze", WAZE_EMBEDDED_TARGET.packageName)
        assertEquals("com.waze.FreeMapAppActivity", WAZE_EMBEDDED_TARGET.componentName)
        assertEquals(900, WAZE_EMBEDDED_TARGET.geometry.width)
        assertEquals(675, WAZE_EMBEDDED_TARGET.geometry.height)
        assertEquals(320, WAZE_EMBEDDED_TARGET.geometry.densityDpi)
    }
}
