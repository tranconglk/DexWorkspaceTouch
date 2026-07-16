package com.trancong.dexworkspacetouch.workspace.designer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NormalizedBoundsTest {
    @Test fun fullCanvasIsCorrect() {
        assertEquals(NormalizedBounds(0f, 0f, 1f, 1f), NormalizedBounds.FullCanvas)
    }

    @Test fun widthAndHeightAreCalculated() {
        val bounds = NormalizedBounds(0.1f, 0.2f, 0.7f, 0.8f)
        assertEquals(0.6f, bounds.width, 0.0001f)
        assertEquals(0.6f, bounds.height, 0.0001f)
    }

    @Test fun invalidBoundsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { NormalizedBounds(-0.1f, 0f, 1f, 1f) }
        assertThrows(IllegalArgumentException::class.java) { NormalizedBounds(0f, 0f, 1.1f, 1f) }
        assertThrows(IllegalArgumentException::class.java) { NormalizedBounds(0.5f, 0f, 0.5f, 1f) }
        assertThrows(IllegalArgumentException::class.java) { NormalizedBounds(0f, 0.5f, 1f, 0.5f) }
    }
}
