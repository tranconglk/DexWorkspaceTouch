package com.trancong.dexworkspacetouch.platform.launch.bounds

import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceGutterGeometryTest {
    @Test
    fun `S23 halves retain outer frame and four pixel gutter`() {
        val bounds = map(DisplayWorkArea(1920, 1144), halves)
        assertEquals(listOf(PixelBounds(8, 8, 958, 1136), PixelBounds(962, 8, 1912, 1136)), bounds)
        assertEquals(4, bounds[1].left - bounds[0].right)
        assertEquals(1920, bounds[0].right + bounds[1].left)
    }

    @Test
    fun `S22 quarters and half retain both four pixel gutters`() {
        val bounds = map(DisplayWorkArea(1920, 1024), listOf(
            NormalizedBounds(0f, 0f, 0.25f, 1f),
            NormalizedBounds(0.25f, 0f, 0.5f, 1f),
            NormalizedBounds(0.5f, 0f, 1f, 1f),
        ))
        assertEquals(listOf(
            PixelBounds(8, 8, 478, 1016), PixelBounds(482, 8, 958, 1016),
            PixelBounds(962, 8, 1912, 1016),
        ), bounds)
        assertEquals(listOf(4, 4), bounds.zipWithNext { a, b -> b.left - a.right })
    }

    @Test
    fun `two by two has centered gutters along both axes`() {
        assertEquals(listOf(
            PixelBounds(8, 8, 958, 570), PixelBounds(962, 8, 1912, 570),
            PixelBounds(8, 574, 958, 1136), PixelBounds(962, 574, 1912, 1136),
        ), map(DisplayWorkArea(1920, 1144), grid))
    }

    @Test
    fun `single full canvas retains eight pixel frame`() {
        assertEquals(listOf(PixelBounds(8, 8, 1912, 1136)),
            map(DisplayWorkArea(1920, 1144), listOf(NormalizedBounds.FullCanvas)))
    }

    @Test
    fun `unequal T junction preserves normalized dividers`() {
        assertEquals(listOf(
            PixelBounds(8, 8, 298, 792), PixelBounds(302, 8, 992, 318),
            PixelBounds(302, 322, 992, 792),
        ), map(DisplayWorkArea(1000, 800), listOf(
            NormalizedBounds(0f, 0f, 0.3f, 1f),
            NormalizedBounds(0.3f, 0f, 1f, 0.4f),
            NormalizedBounds(0.3f, 0.4f, 1f, 1f),
        )))
    }

    @Test
    fun `odd dimensions round shared dividers deterministically`() {
        val area = DisplayWorkArea(101, 99)
        val expected = listOf(
            PixelBounds(8, 8, 49, 48), PixelBounds(53, 8, 93, 48),
            PixelBounds(8, 52, 49, 91), PixelBounds(53, 52, 93, 91),
        )
        repeat(3) { assertEquals(expected, map(area, grid)) }
    }

    @Test
    fun `thirds retain centered rounding and gutters`() {
        assertEquals(listOf(PixelBounds(8, 8, 32, 91), PixelBounds(36, 8, 65, 91),
            PixelBounds(69, 8, 93, 91)), map(DisplayWorkArea(101, 99), listOf(
            NormalizedBounds(0f, 0f, 1f / 3f, 1f),
            NormalizedBounds(1f / 3f, 0f, 2f / 3f, 1f),
            NormalizedBounds(2f / 3f, 0f, 1f, 1f),
        )))
    }

    @Test
    fun `outer frame is relative to usable rectangle with insets`() {
        assertEquals(listOf(PixelBounds(20, 26, 516, 712), PixelBounds(520, 26, 1016, 712)),
            map(DisplayWorkArea(1038, 740, 12, 18, 14, 20), halves))
    }

    @Test
    fun `interior cell uses gutter inset on every side`() {
        assertEquals(listOf(PixelBounds(252, 202, 748, 598)), map(DisplayWorkArea(1000, 800),
            listOf(NormalizedBounds(0.25f, 0.25f, 0.75f, 0.75f))))
    }

    @Test
    fun `cells too small for frame or gutter fail instead of inverting`() {
        val calculator = LaunchBoundsCalculator(8)
        for ((area, cell) in listOf(
            DisplayWorkArea(16, 16) to NormalizedBounds.FullCanvas,
            DisplayWorkArea(100, 100) to NormalizedBounds(0.5f, 0.5f, 0.54f, 0.6f),
            DisplayWorkArea(100, 100) to NormalizedBounds(0f, 0f, 0.1f, 1f),
        )) {
            assertEquals(BoundsCalculationResult.Failure(BoundsCalculationFailureReason.INSUFFICIENT_SPACE),
                calculator.calculate(cell, area))
        }
    }

    @Test
    fun `one pixel interior region remains valid when gutter fits`() {
        assertEquals(listOf(PixelBounds(52, 52, 53, 58)), map(DisplayWorkArea(100, 100),
            listOf(NormalizedBounds(0.5f, 0.5f, 0.55f, 0.6f))))
    }

    @Test
    fun `accepted disjoint grid never overlaps or leaves usable area`() {
        for (width in listOf(101, 1920, 1921)) {
            for (height in listOf(99, 1024, 1145)) {
                val area = DisplayWorkArea(width, height)
                val bounds = map(area, grid)
                bounds.forEach { assertTrue(LaunchBoundsSanity.isWithinWorkArea(it, area)) }
                bounds.forEachIndexed { index, a ->
                    bounds.drop(index + 1).forEach { b ->
                        assertFalse(a.left < b.right && b.left < a.right &&
                            a.top < b.bottom && b.top < a.bottom)
                    }
                }
                assertEquals(4, bounds[1].left - bounds[0].right)
                assertEquals(4, bounds[2].top - bounds[0].bottom)
            }
        }
    }

    @Test
    fun `product frame stays eight physical pixels at every density`() {
        for (density in listOf(1f, 2f, 2.625f)) {
            assertEquals(PixelBounds(8, 8, 1912, 1136),
                (LaunchBoundsCalculator(launchMarginPx(density)).calculate(
                    NormalizedBounds.FullCanvas, DisplayWorkArea(1920, 1144)
                ) as BoundsCalculationResult.Success).bounds)
        }
    }

    private fun map(area: DisplayWorkArea, cells: List<NormalizedBounds>) = cells.map {
        (LaunchBoundsCalculator(8).calculate(it, area) as BoundsCalculationResult.Success).bounds
    }

    private val halves = listOf(NormalizedBounds(0f, 0f, 0.5f, 1f), NormalizedBounds(0.5f, 0f, 1f, 1f))
    private val grid = listOf(
        NormalizedBounds(0f, 0f, 0.5f, 0.5f), NormalizedBounds(0.5f, 0f, 1f, 0.5f),
        NormalizedBounds(0f, 0.5f, 0.5f, 1f), NormalizedBounds(0.5f, 0.5f, 1f, 1f),
    )
}
