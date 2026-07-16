package com.trancong.dexworkspacetouch.workspace.designer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceCanvasTest {
    private val validator = WorkspaceCanvasValidator()

    @Test fun singleCellCreatesOneFullCanvasCell() {
        val canvas = WorkspaceCanvas.singleCell()
        assertEquals(1, canvas.cells.size)
        assertEquals(NormalizedBounds.FullCanvas, canvas.cells.single().bounds)
    }

    @Test fun duplicateIdIsDetected() {
        val cell = WorkspaceCell("same", NormalizedBounds(0f, 0f, 0.5f, 1f))
        val issues = validator.validate(WorkspaceCanvas(listOf(cell, cell.copy(bounds = NormalizedBounds(0.5f, 0f, 1f, 1f)))))
        assertTrue(issues.any { it is CanvasValidationIssue.DuplicateCellId })
    }

    @Test fun overlapIsDetected() {
        val canvas = WorkspaceCanvas(listOf(
            WorkspaceCell("a", NormalizedBounds(0f, 0f, 0.7f, 1f)),
            WorkspaceCell("b", NormalizedBounds(0.5f, 0f, 1f, 1f)),
        ))
        assertTrue(validator.validate(canvas).any { it is CanvasValidationIssue.OverlappingCells })
    }

    @Test fun blankCellIdIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceCell(" ", NormalizedBounds.FullCanvas)
        }
    }
}
