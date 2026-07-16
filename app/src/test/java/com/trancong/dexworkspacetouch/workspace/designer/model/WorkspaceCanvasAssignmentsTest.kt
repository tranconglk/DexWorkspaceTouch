package com.trancong.dexworkspacetouch.workspace.designer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceCanvasAssignmentsTest {
    private val firstBounds = NormalizedBounds(0f, 0f, 0.5f, 1f)
    private val secondBounds = NormalizedBounds(0.5f, 0f, 1f, 1f)
    private val canvas = WorkspaceCanvas(
        listOf(WorkspaceCell("first", firstBounds), WorkspaceCell("second", secondBounds)),
    )
    private val maps = AssignedApp("com.google.android.apps.maps", "com.google.android.maps.MapsActivity", "Google Maps")

    @Test fun appIsAssignedToRequestedCell() {
        val result = canvas.assignApp("second", maps)
        assertEquals(maps, result.cells[1].app)
        assertNotSame(canvas, result)
    }

    @Test fun otherCellDoesNotChange() {
        val result = canvas.assignApp("second", maps)
        assertEquals(canvas.cells[0], result.cells[0])
    }

    @Test fun boundsDoNotChange() {
        val result = canvas.assignApp("second", maps)
        assertEquals(canvas.cells.map { it.bounds }, result.cells.map { it.bounds })
    }

    @Test fun assigningAgainReplacesOnlyRequestedApp() {
        val chrome = AssignedApp("com.android.chrome", "com.google.android.apps.chrome.Main", "Chrome")
        val result = canvas.assignApp("second", maps).assignApp("second", chrome)
        assertEquals(chrome, result.cells[1].app)
        assertEquals(canvas.cells[0], result.cells[0])
    }

    @Test fun missingCellIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { canvas.assignApp("missing", maps) }
    }

    @Test fun canvasRemainsValidAfterAssignment() {
        val result = canvas.assignApp("second", maps)
        assertTrue(WorkspaceCanvasValidator().validate(result).isEmpty())
    }
}
