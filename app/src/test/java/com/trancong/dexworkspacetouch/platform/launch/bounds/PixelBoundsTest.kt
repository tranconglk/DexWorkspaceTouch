package com.trancong.dexworkspacetouch.platform.launch.bounds

import org.junit.Assert.assertEquals
import org.junit.Test

class PixelBoundsTest {
    @Test
    fun `width and height are calculated`() {
        val bounds = PixelBounds(left = 10, top = 20, right = 110, bottom = 70)

        assertEquals(100, bounds.width)
        assertEquals(50, bounds.height)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero width is rejected`() {
        PixelBounds(left = 10, top = 0, right = 10, bottom = 20)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero height is rejected`() {
        PixelBounds(left = 0, top = 20, right = 10, bottom = 20)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative origin is rejected`() {
        PixelBounds(left = -1, top = 0, right = 10, bottom = 20)
    }
}
