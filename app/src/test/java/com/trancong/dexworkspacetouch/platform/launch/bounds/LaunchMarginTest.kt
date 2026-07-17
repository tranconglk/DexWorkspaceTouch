package com.trancong.dexworkspacetouch.platform.launch.bounds

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchMarginTest {
    @Test
    fun `default margin at density one is eight pixels`() {
        assertEquals(8, launchMarginPx(density = 1f))
    }

    @Test
    fun `default margin at density two is sixteen pixels`() {
        assertEquals(16, launchMarginPx(density = 2f))
    }

    @Test
    fun `fractional display density uses deterministic roundToInt`() {
        assertEquals(21, launchMarginPx(density = 2.625f))
    }

    @Test
    fun `zero configured margin stays zero`() {
        assertEquals(
            0,
            launchMarginPx(
                density = 2.625f,
                config = LaunchBoundsConfig(marginDp = 0f),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero density is rejected`() {
        launchMarginPx(density = 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non finite density is rejected`() {
        launchMarginPx(density = Float.NaN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative configured margin is rejected`() {
        LaunchBoundsConfig(marginDp = -1f)
    }
}
