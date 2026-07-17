package com.trancong.dexworkspacetouch.platform.launch.bounds

import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchBoundsCalculatorTest {
    @Test
    fun `full canvas maps to 1920 by 1200`() {
        assertEquals(
            PixelBounds(0, 0, 1920, 1200),
            calculate(NormalizedBounds.FullCanvas, DisplayWorkArea(1920, 1200)),
        )
    }

    @Test
    fun `bottom taskbar is excluded from 1920 by 1200`() {
        assertEquals(
            PixelBounds(0, 0, 1920, 1152),
            calculate(
                NormalizedBounds.FullCanvas,
                DisplayWorkArea(1920, 1200, insetBottomPx = 48),
            ),
        )
    }

    @Test
    fun `full canvas maps to 1920 by 1080`() {
        assertEquals(
            PixelBounds(0, 0, 1920, 1080),
            calculate(NormalizedBounds.FullCanvas, DisplayWorkArea(1920, 1080)),
        )
    }

    @Test
    fun `all four insets define the usable rectangle`() {
        val area = DisplayWorkArea(1920, 1200, 10, 20, 30, 40)

        assertEquals(
            PixelBounds(10, 20, 1890, 1160),
            calculate(NormalizedBounds.FullCanvas, area),
        )
    }

    @Test
    fun `left and right halves share deterministic rounded edge`() {
        val area = DisplayWorkArea(101, 99)

        assertEquals(
            PixelBounds(0, 0, 51, 99),
            calculate(NormalizedBounds(0f, 0f, 0.5f, 1f), area),
        )
        assertEquals(
            PixelBounds(51, 0, 101, 99),
            calculate(NormalizedBounds(0.5f, 0f, 1f, 1f), area),
        )
    }

    @Test
    fun `top and bottom halves share deterministic rounded edge`() {
        val area = DisplayWorkArea(100, 101)

        assertEquals(
            PixelBounds(0, 0, 100, 51),
            calculate(NormalizedBounds(0f, 0f, 1f, 0.5f), area),
        )
        assertEquals(
            PixelBounds(0, 51, 100, 101),
            calculate(NormalizedBounds(0f, 0.5f, 1f, 1f), area),
        )
    }

    @Test
    fun `one third and two thirds follow roundToInt`() {
        val area = DisplayWorkArea(101, 99)

        assertEquals(
            PixelBounds(0, 0, 34, 99),
            calculate(NormalizedBounds(0f, 0f, 1f / 3f, 1f), area),
        )
        assertEquals(
            PixelBounds(34, 0, 67, 99),
            calculate(NormalizedBounds(1f / 3f, 0f, 2f / 3f, 1f), area),
        )
        assertEquals(
            PixelBounds(67, 0, 101, 99),
            calculate(NormalizedBounds(2f / 3f, 0f, 1f, 1f), area),
        )
    }

    @Test
    fun `nested bounds use usable dimensions and inset origin`() {
        val area = DisplayWorkArea(1000, 800, 20, 30, 40, 50)

        assertEquals(
            PixelBounds(255, 210, 725, 570),
            calculate(NormalizedBounds(0.25f, 0.25f, 0.75f, 0.75f), area),
        )
    }

    @Test
    fun `pixel margin is applied inward on every edge`() {
        assertEquals(
            PixelBounds(8, 8, 1912, 1192),
            calculate(
                NormalizedBounds.FullCanvas,
                DisplayWorkArea(1920, 1200),
                marginPx = 8,
            ),
        )
    }

    @Test
    fun `bounds touching normalized zero and one remain inside usable area`() {
        val area = DisplayWorkArea(1038, 740, 12, 18, 14, 20)
        val bounds = calculate(NormalizedBounds.FullCanvas, area, marginPx = 4)

        assertEquals(PixelBounds(16, 22, 1020, 716), bounds)
        assertInside(bounds, area)
    }

    @Test
    fun `small work area still produces one pixel bounds when possible`() {
        assertEquals(
            PixelBounds(1, 1, 2, 2),
            calculate(
                NormalizedBounds.FullCanvas,
                DisplayWorkArea(3, 3),
                marginPx = 1,
            ),
        )
    }

    @Test
    fun `oversized margin returns typed failure`() {
        val result = LaunchBoundsCalculator(marginPx = 2).calculate(
            NormalizedBounds.FullCanvas,
            DisplayWorkArea(3, 3),
        )

        assertEquals(
            BoundsCalculationResult.Failure(BoundsCalculationFailureReason.INSUFFICIENT_SPACE),
            result,
        )
    }

    @Test
    fun `rounding that collapses a tiny valid region returns typed failure`() {
        val result = LaunchBoundsCalculator(marginPx = 0).calculate(
            NormalizedBounds(0f, 0f, 0.01f, 1f),
            DisplayWorkArea(10, 10),
        )

        assertTrue(result is BoundsCalculationResult.Failure)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative margin is rejected as invalid configuration`() {
        LaunchBoundsCalculator(marginPx = -1)
    }

    private fun calculate(
        bounds: NormalizedBounds,
        area: DisplayWorkArea,
        marginPx: Int = 0,
    ): PixelBounds {
        val result = LaunchBoundsCalculator(marginPx).calculate(bounds, area)
        return (result as BoundsCalculationResult.Success).bounds
    }

    private fun assertInside(bounds: PixelBounds, area: DisplayWorkArea) {
        assertTrue(bounds.left >= area.originX)
        assertTrue(bounds.top >= area.originY)
        assertTrue(bounds.right <= area.usableRight)
        assertTrue(bounds.bottom <= area.usableBottom)
        assertTrue(bounds.width > 0)
        assertTrue(bounds.height > 0)
    }
}
