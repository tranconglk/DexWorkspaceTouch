package com.trancong.dexworkspacetouch.workspace.designer.ui

import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceCanvasPlacementTest {
    @Test fun fullCanvasUsesAllAvailableSpace() {
        assertEquals(ComposePlacement(0, 0, 1920, 1200), placement(NormalizedBounds.FullCanvas, 1920, 1200))
    }

    @Test fun halfWidthUsesHalfTheCanvas() {
        assertEquals(ComposePlacement(0, 0, 960, 1200), placement(NormalizedBounds(0f, 0f, 0.5f, 1f), 1920, 1200))
        assertEquals(ComposePlacement(960, 0, 960, 1200), placement(NormalizedBounds(0.5f, 0f, 1f, 1f), 1920, 1200))
    }

    @Test fun topAndBottomShareTheCanvas() {
        assertEquals(ComposePlacement(0, 0, 1920, 600), placement(NormalizedBounds(0f, 0f, 1f, 0.5f), 1920, 1200))
        assertEquals(ComposePlacement(0, 600, 1920, 600), placement(NormalizedBounds(0f, 0.5f, 1f, 1f), 1920, 1200))
    }

    @Test fun commonDesktopSizeIsExact() {
        assertEquals(ComposePlacement(480, 300, 960, 600), placement(NormalizedBounds(0.25f, 0.25f, 0.75f, 0.75f), 1920, 1200))
    }

    @Test fun unevenCanvasSizeRoundsSharedBoundaryConsistently() {
        val left = placement(NormalizedBounds(0f, 0f, 0.5f, 1f), 1038, 740)
        val right = placement(NormalizedBounds(0.5f, 0f, 1f, 1f), 1038, 740)
        assertEquals(left.x + left.width, right.x)
        assertEquals(1038, right.x + right.width)
    }

    @Test fun placementIsNeverNegative() {
        val result = placement(NormalizedBounds(0f, 0f, 0.001f, 0.001f), 1038, 740)
        assertTrue(result.x >= 0 && result.y >= 0 && result.width > 0 && result.height > 0)
    }

    @Test fun placementNeverExceedsCanvas() {
        val result = placement(NormalizedBounds(0.999f, 0.999f, 1f, 1f), 1038, 740)
        assertTrue(result.x + result.width <= 1038)
        assertTrue(result.y + result.height <= 740)
    }

    private fun placement(bounds: NormalizedBounds, width: Int, height: Int): ComposePlacement =
        bounds.toComposePlacement(width.toFloat(), height.toFloat())
}
