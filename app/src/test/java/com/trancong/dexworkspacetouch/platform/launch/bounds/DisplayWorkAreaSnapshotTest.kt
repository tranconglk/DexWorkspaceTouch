package com.trancong.dexworkspacetouch.platform.launch.bounds

import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayWorkAreaSnapshotTest {
    private val workArea = DisplayWorkArea(
        widthPx = 1920,
        heightPx = 1200,
        insetLeftPx = 10,
        insetTopPx = 20,
        insetRightPx = 30,
        insetBottomPx = 40,
    )

    @Test
    fun `valid snapshot retains diagnostics`() {
        val snapshot = snapshot()

        assertTrue(snapshot.diagnosticMessage().contains("displayId=2"))
        assertTrue(snapshot.diagnosticMessage().contains("insets=[10,20,30,40]"))
        assertTrue(snapshot.diagnosticMessage().contains("usable=1880x1140"))
        assertTrue(snapshot.diagnosticMessage().contains("hostWindowMode=WINDOWED"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative display id is rejected`() {
        snapshot().copy(displayId = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid density is rejected`() {
        snapshot().copy(density = Float.POSITIVE_INFINITY)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `raw display width must match work area`() {
        snapshot().copy(rawDisplayBounds = DiagnosticPixelBounds(0, 0, 1280, 1200))
    }

    private fun snapshot() = DisplayWorkAreaSnapshot(
        displayId = 2,
        workArea = workArea,
        rawDisplayBounds = DiagnosticPixelBounds(0, 0, 1920, 1200),
        hostWindowBounds = DiagnosticPixelBounds(100, 100, 1100, 800),
        density = 2.625f,
        hostWindowMode = HostWindowMode.WINDOWED,
    )
}
