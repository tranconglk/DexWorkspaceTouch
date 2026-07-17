package com.trancong.dexworkspacetouch.platform.launch.bounds

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayWorkAreaTest {
    @Test
    fun `usable dimensions and edges account for all insets`() {
        val area = DisplayWorkArea(
            widthPx = 1920,
            heightPx = 1200,
            insetLeftPx = 10,
            insetTopPx = 20,
            insetRightPx = 30,
            insetBottomPx = 40,
        )

        assertEquals(10, area.originX)
        assertEquals(20, area.originY)
        assertEquals(1880, area.usableWidth)
        assertEquals(1140, area.usableHeight)
        assertEquals(1890, area.usableRight)
        assertEquals(1160, area.usableBottom)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non positive display size is rejected`() {
        DisplayWorkArea(widthPx = 0, heightPx = 1200)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative inset is rejected`() {
        DisplayWorkArea(widthPx = 1920, heightPx = 1200, insetLeftPx = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `insets consuming entire width are rejected`() {
        DisplayWorkArea(
            widthPx = 100,
            heightPx = 100,
            insetLeftPx = 40,
            insetRightPx = 60,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `insets consuming entire height are rejected`() {
        DisplayWorkArea(
            widthPx = 100,
            heightPx = 100,
            insetTopPx = 50,
            insetBottomPx = 50,
        )
    }
}
