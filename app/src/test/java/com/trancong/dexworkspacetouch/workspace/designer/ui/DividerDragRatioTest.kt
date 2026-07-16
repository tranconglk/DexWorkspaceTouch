package com.trancong.dexworkspacetouch.workspace.designer.ui

import com.trancong.dexworkspacetouch.workspace.designer.model.SplitDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DividerDragRatioTest {
    @Test
    fun `vertical divider uses pointer x`() {
        assertEquals(0.6f, ratio(SplitDirection.VERTICAL, x = 600f, y = 10f), TOLERANCE)
    }

    @Test
    fun `horizontal divider uses pointer y`() {
        assertEquals(0.3f, ratio(SplitDirection.HORIZONTAL, x = 10f, y = 300f), TOLERANCE)
    }

    @Test
    fun `ratio is relative to partial parent span`() {
        val result = pointerToDividerRatio(
            direction = SplitDirection.VERTICAL,
            pointerX = 500f,
            pointerY = 0f,
            canvasWidth = 1000f,
            canvasHeight = 1000f,
            parentStart = 0.25f,
            parentEnd = 0.75f,
            minRatio = 0.2f,
            maxRatio = 0.8f,
        )

        assertEquals(0.5f, result, TOLERANCE)
    }

    @Test
    fun `pointer before parent start clamps to minimum`() {
        assertEquals(0.2f, ratio(SplitDirection.VERTICAL, x = -100f, y = 0f), TOLERANCE)
    }

    @Test
    fun `pointer after parent end clamps to maximum`() {
        assertEquals(0.8f, ratio(SplitDirection.HORIZONTAL, x = 0f, y = 2000f), TOLERANCE)
    }

    @Test
    fun `invalid parent span is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            pointerToDividerRatio(
                direction = SplitDirection.VERTICAL,
                pointerX = 500f,
                pointerY = 0f,
                canvasWidth = 1000f,
                canvasHeight = 1000f,
                parentStart = 0.5f,
                parentEnd = 0.5f,
                minRatio = 0.2f,
                maxRatio = 0.8f,
            )
        }
    }

    private fun ratio(direction: SplitDirection, x: Float, y: Float) = pointerToDividerRatio(
        direction = direction,
        pointerX = x,
        pointerY = y,
        canvasWidth = 1000f,
        canvasHeight = 1000f,
        parentStart = 0f,
        parentEnd = 1f,
        minRatio = 0.2f,
        maxRatio = 0.8f,
    )

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
