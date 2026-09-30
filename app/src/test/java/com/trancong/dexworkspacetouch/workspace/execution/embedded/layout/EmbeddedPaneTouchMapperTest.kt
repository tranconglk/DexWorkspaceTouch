package com.trancong.dexworkspacetouch.workspace.execution.embedded.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedPaneTouchMapperTest {
    private val mapper = EmbeddedPaneTouchMapper()

    @Test fun mapsCornersAndCenterWithIndependentAxes() {
        assertEquals(PaneTouchMappingResult.Mapped(0f, 0f), mapper.map(0f, 0f, 600, 300, 900, 675))
        assertEquals(PaneTouchMappingResult.Mapped(450f, 337.5f), mapper.map(300f, 150f, 600, 300, 900, 675))
        assertEquals(PaneTouchMappingResult.Mapped(899f, 674f), mapper.map(600f, 300f, 600, 300, 900, 675))
    }

    @Test fun outsidePointsClampAndAspectDoesNotMatter() {
        assertEquals(PaneTouchMappingResult.Mapped(0f, 674f), mapper.map(-1f, 301f, 600, 300, 900, 675))
        assertEquals(PaneTouchMappingResult.Mapped(450f, 337.5f), mapper.map(600f, 50f, 1200, 100, 900, 675))
        assertEquals(PaneTouchMappingResult.Mapped(450f, 337.5f), mapper.map(50f, 600f, 100, 1200, 900, 675))
    }

    @Test fun invalidHostOrNonfiniteLocalRejects() {
        assertTrue(mapper.map(0f, 0f, 0, 100, 900, 675) is PaneTouchMappingResult.Rejected)
        assertTrue(mapper.map(Float.NaN, 0f, 100, 100, 900, 675) is PaneTouchMappingResult.Rejected)
        assertTrue(mapper.map(0f, Float.POSITIVE_INFINITY, 100, 100, 900, 675) is PaneTouchMappingResult.Rejected)
    }
}
