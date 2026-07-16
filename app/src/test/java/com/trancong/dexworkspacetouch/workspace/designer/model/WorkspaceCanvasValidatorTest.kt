package com.trancong.dexworkspacetouch.workspace.designer.model

import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceCanvasValidatorTest {
    private val validator = WorkspaceCanvasValidator()

    @Test fun validCanvasHasNoIssues() {
        assertTrue(validator.validate(WorkspaceCanvas.singleCell()).isEmpty())
    }

    @Test fun overlapReturnsOverlapIssue() {
        val canvas = WorkspaceCanvas(listOf(
            WorkspaceCell("a", NormalizedBounds(0f, 0f, 0.75f, 1f)),
            WorkspaceCell("b", NormalizedBounds(0.25f, 0f, 1f, 1f)),
        ))
        assertTrue(validator.validate(canvas).any { it is CanvasValidationIssue.OverlappingCells })
    }

    @Test fun outOfBoundsReturnsOutOfBoundsIssue() {
        val bounds = NormalizedBounds.FullCanvas.copy()
        NormalizedBounds::class.java.getDeclaredField("right").apply {
            isAccessible = true
            setFloat(bounds, 1.1f)
        }
        val canvas = WorkspaceCanvas(listOf(WorkspaceCell("outside", bounds)))
        assertTrue(validator.validate(canvas).any { it is CanvasValidationIssue.OutOfBounds })
    }

    @Test fun duplicateIdReturnsDuplicateIssue() {
        val canvas = WorkspaceCanvas(listOf(
            WorkspaceCell("same", NormalizedBounds(0f, 0f, 0.5f, 1f)),
            WorkspaceCell("same", NormalizedBounds(0.5f, 0f, 1f, 1f)),
        ))
        assertTrue(validator.validate(canvas).any { it is CanvasValidationIssue.DuplicateCellId })
    }
}
