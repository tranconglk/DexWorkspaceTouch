package com.trancong.dexworkspacetouch.feature.embeddedapp

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedAppModelsTest {
    private val geometry = EmbeddedAppGeometry(900, 675, 320)

    @Test fun mapsCenterIntoConfiguredGeometry() {
        assertEquals(VdmPoint(450f, 337.5f), mapPoint(160f, 120f, 320, 240, geometry))
    }

    @Test fun clampsEveryDisplayEdge() {
        assertEquals(VdmPoint(0f, 0f), mapPoint(-10f, -10f, 320, 240, geometry))
        assertEquals(VdmPoint(900f, 675f), mapPoint(330f, 250f, 320, 240, geometry))
    }

    @Test fun mapsDownMoveUpCancel() {
        assertEquals(0, virtualAction(MotionEvent.ACTION_DOWN))
        assertEquals(2, virtualAction(MotionEvent.ACTION_MOVE))
        assertEquals(1, virtualAction(MotionEvent.ACTION_UP))
        assertEquals(3, virtualAction(MotionEvent.ACTION_CANCEL))
    }

    @Test fun downUsesPressure255() {
        assertEquals(255f, pressureFor(MotionEvent.ACTION_DOWN, 0f))
        assertEquals(0f, pressureFor(MotionEvent.ACTION_UP, 0f))
    }

    @Test fun cancelUsesPalmAndOtherActionsUseFinger() {
        assertEquals(MotionEvent.TOOL_TYPE_FINGER, virtualToolType(MotionEvent.ACTION_DOWN))
        assertEquals(MotionEvent.TOOL_TYPE_FINGER, virtualToolType(MotionEvent.ACTION_MOVE))
        assertEquals(MotionEvent.TOOL_TYPE_FINGER, virtualToolType(MotionEvent.ACTION_UP))
        assertEquals(VIRTUAL_TOOL_TYPE_PALM, virtualToolType(MotionEvent.ACTION_CANCEL))
    }

    @Test fun cleanupAdvancesInRequiredOrder() {
        val progress = CleanupProgress()
        CleanupStep.entries.forEach { step ->
            assertEquals(step, progress.next())
            assertTrue(progress.complete(step))
        }
        assertNull(progress.next())
        assertTrue(progress.finished)
    }

    @Test fun cleanupCompletionIsIdempotent() {
        val progress = CleanupProgress()
        assertTrue(progress.complete(CleanupStep.INPUT))
        assertFalse(progress.complete(CleanupStep.INPUT))
        assertEquals(CleanupStep.TASK, progress.next())
    }

    @Test fun targetContainsOnlyExecutionIdentityAndDisplayGeometry() {
        val target = EmbeddedAppTarget("com.waze", "com.waze.FreeMapAppActivity", geometry)
        assertEquals("com.waze", target.packageName)
        assertEquals("com.waze.FreeMapAppActivity", target.componentName)
        assertEquals(geometry, target.geometry)
    }
}
