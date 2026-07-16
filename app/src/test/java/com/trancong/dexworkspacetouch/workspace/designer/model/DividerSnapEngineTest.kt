package com.trancong.dexworkspacetouch.workspace.designer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DividerSnapEngineTest {
    @Test
    fun `49 percent snaps to 50 percent`() {
        val result = snapRatio(0.49f, SNAP_POINTS, 0.03f)

        assertEquals(0.5f, result.ratio, TOLERANCE)
        assertTrue(result.snapped)
        assertEquals(0.5f, result.snapPoint)
    }

    @Test
    fun `47 percent stays raw outside threshold`() {
        val result = snapRatio(0.47f, SNAP_POINTS, 0.02f)

        assertEquals(0.47f, result.ratio, TOLERANCE)
        assertFalse(result.snapped)
        assertEquals(null, result.snapPoint)
    }

    @Test
    fun `ratio near one third snaps correctly`() {
        assertEquals(1f / 3f, snapRatio(0.34f, SNAP_POINTS, 0.03f).ratio, TOLERANCE)
    }

    @Test
    fun `ratio near two thirds snaps correctly`() {
        assertEquals(2f / 3f, snapRatio(0.65f, SNAP_POINTS, 0.03f).ratio, TOLERANCE)
    }

    @Test
    fun `equal distance chooses smaller snap point`() {
        val result = snapRatio(0.5f, listOf(0.6f, 0.4f), 0.11f)

        assertEquals(0.4f, result.ratio, TOLERANCE)
        assertEquals(0.4f, result.snapPoint)
    }

    @Test
    fun `zero threshold is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            snapRatio(0.5f, SNAP_POINTS, 0f)
        }
    }

    @Test
    fun `snap point outside normalized range is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            snapRatio(0.5f, listOf(-0.1f, 0.5f), 0.03f)
        }
    }

    @Test
    fun `result is clamped to editor limits`() {
        assertEquals(0.2f, snapRatio(-1f, emptyList(), 0.03f).ratio, TOLERANCE)
        assertEquals(0.8f, snapRatio(2f, emptyList(), 0.03f).ratio, TOLERANCE)
    }

    private companion object {
        val SNAP_POINTS = listOf(0.25f, 1f / 3f, 0.5f, 2f / 3f, 0.75f)
        const val TOLERANCE = 0.0001f
    }
}
