package com.trancong.dexworkspacetouch.feature.embeddedwaze
import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
class EmbeddedWazeModelsTest{
@Test fun mapsSurfaceCoordinatesToFixedVdmSpace(){assertEquals(VdmPoint(450f,337.5f),mapPoint(160f,120f,320,240))}
@Test fun mapsSupportedSingleFingerActions(){assertEquals(0,virtualAction(MotionEvent.ACTION_DOWN));assertEquals(2,virtualAction(MotionEvent.ACTION_MOVE));assertEquals(1,virtualAction(MotionEvent.ACTION_UP));assertEquals(3,virtualAction(MotionEvent.ACTION_CANCEL))}
@Test fun cancelUsesPalmAsRequiredByVirtualTouchEventContract(){assertEquals(MotionEvent.TOOL_TYPE_FINGER,virtualToolType(MotionEvent.ACTION_DOWN));assertEquals(MotionEvent.TOOL_TYPE_FINGER,virtualToolType(MotionEvent.ACTION_MOVE));assertEquals(MotionEvent.TOOL_TYPE_FINGER,virtualToolType(MotionEvent.ACTION_UP));assertEquals(VIRTUAL_TOOL_TYPE_PALM,virtualToolType(MotionEvent.ACTION_CANCEL))}
@Test fun downAlwaysUsesPositiveProvenPressure(){assertEquals(255f,pressureFor(MotionEvent.ACTION_DOWN,0f));assertEquals(0f,pressureFor(MotionEvent.ACTION_UP,0f))}
@Test fun cleanupIsOrdered(){val s=CleanupSequence();CleanupStep.entries.forEach{assertEquals(it,s.next());s.complete(it)};assertTrue(s.finished);assertNull(s.next())}}
