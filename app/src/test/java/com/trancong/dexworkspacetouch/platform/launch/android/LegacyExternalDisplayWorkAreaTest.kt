package com.trancong.dexworkspacetouch.platform.launch.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyExternalDisplayWorkAreaTest {
    @Test
    fun compatibilityScaledMetricsAreNotTreatedAsPhysicalDisplayCoordinates() {
        assertFalse(legacyMetricsShareCoordinateSpace(806, 1920))
    }

    @Test
    fun normalSystemDecorDifferenceKeepsMetricsInTheSameCoordinateSpace() {
        assertTrue(legacyMetricsShareCoordinateSpace(1920, 1920))
        assertTrue(legacyMetricsShareCoordinateSpace(1800, 1920))
    }

    @Test
    fun invalidPhysicalWidthIsRejected() {
        assertFalse(legacyMetricsShareCoordinateSpace(0, 0))
    }
}
