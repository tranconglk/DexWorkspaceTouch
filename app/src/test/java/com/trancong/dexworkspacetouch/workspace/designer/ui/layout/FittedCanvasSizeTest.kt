package com.trancong.dexworkspacetouch.workspace.designer.ui.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FittedCanvasSizeTest {
    @Test
    fun `exact 1920 by 1200 remains unchanged`() {
        assertEquals(FittedCanvasSize(1920f, 1200f), fitSize(1920f, 1200f, RATIO))
    }

    @Test
    fun `1920 by 1080 is limited by height`() {
        assertEquals(FittedCanvasSize(1728f, 1080f), fitSize(1920f, 1080f, RATIO))
    }

    @Test
    fun `1038 by 740 is limited by width`() {
        val result = fitSize(1038f, 740f, RATIO)

        assertEquals(1038f, result.width, TOLERANCE)
        assertEquals(648.75f, result.height, TOLERANCE)
    }

    @Test
    fun `tall narrow area is limited by width`() {
        assertEquals(FittedCanvasSize(400f, 250f), fitSize(400f, 1200f, RATIO))
    }

    @Test
    fun `wide short area is limited by height`() {
        assertEquals(FittedCanvasSize(480f, 300f), fitSize(1600f, 300f, RATIO))
    }

    @Test
    fun `result stays inside available bounds with target ratio`() {
        val result = fitSize(777f, 333f, RATIO)

        assertTrue(result.width <= 777f)
        assertTrue(result.height <= 333f)
        assertEquals(RATIO, result.width / result.height, TOLERANCE)
    }

    @Test
    fun `invalid dimensions and ratio are rejected`() {
        listOf(
            Triple(0f, 100f, RATIO),
            Triple(-1f, 100f, RATIO),
            Triple(100f, 0f, RATIO),
            Triple(100f, -1f, RATIO),
            Triple(100f, 100f, 0f),
            Triple(Float.NaN, 100f, RATIO),
        ).forEach { (width, height, ratio) ->
            runCatching { fitSize(width, height, ratio) }
                .onSuccess { throw AssertionError("Expected invalid input to be rejected") }
        }
    }

    private companion object {
        const val RATIO = 16f / 10f
        const val TOLERANCE = 0.0001f
    }
}
