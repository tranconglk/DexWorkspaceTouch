package com.trancong.dexworkspacetouch.workspace.designer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceCanvasEditorMergeTest {
    private val editor = WorkspaceCanvasEditor()

    @Test fun mergeTwoColumnsKeepsTargetIdAndValidCanvas() {
        val canvas = twoColumns(appOnFirst = false, appOnSecond = false)
        val result = editor.mergeCells(canvas, "left", "right") as WorkspaceMergeResult.Success
        assertEquals(listOf("right"), result.canvas.cells.map { it.id })
        assertEquals(NormalizedBounds.FullCanvas, result.canvas.cells.single().bounds)
        assertTrue(WorkspaceCanvasValidator().validate(result.canvas).isEmpty())
    }

    @Test fun mergeTwoRows() {
        val canvas = WorkspaceCanvas(listOf(cell("top", 0f, 0f, 1f, .5f), cell("bottom", 0f, .5f, 1f, 1f)))
        val result = editor.mergeCells(canvas, "top", "bottom") as WorkspaceMergeResult.Success
        assertEquals(WorkspaceMergeDirection.DOWN, result.candidate.direction)
        assertEquals(NormalizedBounds.FullCanvas, result.canvas.cells.single().bounds)
    }

    @Test fun candidatesCoverLeftRightUpDown() {
        val center = cell("source", .25f, .25f, .75f, .75f)
        listOf(
            cell("left", 0f, .25f, .25f, .75f) to WorkspaceMergeDirection.LEFT,
            cell("right", .75f, .25f, 1f, .75f) to WorkspaceMergeDirection.RIGHT,
            cell("up", .25f, 0f, .75f, .25f) to WorkspaceMergeDirection.UP,
            cell("down", .25f, .75f, .75f, 1f) to WorkspaceMergeDirection.DOWN,
        ).forEach { (target, expected) ->
            val canvas = WorkspaceCanvas(listOf(center, target))
            assertEquals(expected, editor.findMergeCandidates(canvas, "source").single().direction)
        }
    }

    @Test fun missingSourceAndTargetReturnTypedFailures() {
        val canvas = WorkspaceCanvas.singleCell()
        assertEquals(WorkspaceMergeFailureReason.SOURCE_NOT_FOUND, failure(canvas, "missing", "cell"))
        assertEquals(WorkspaceMergeFailureReason.TARGET_NOT_FOUND, failure(canvas, "cell", "missing"))
    }

    @Test fun sameCellReturnsTypedFailure() {
        val canvas = WorkspaceCanvas.singleCell()
        assertEquals(WorkspaceMergeFailureReason.SAME_CELL, failure(canvas, "cell", "cell"))
    }

    @Test fun separatedCellsAreNotAdjacent() {
        val canvas = WorkspaceCanvas(listOf(cell("a", 0f, 0f, .4f, 1f), cell("b", .6f, 0f, 1f, 1f)))
        assertEquals(WorkspaceMergeFailureReason.NOT_ADJACENT, failure(canvas, "a", "b"))
    }

    @Test fun partialSharedEdgeIsNonRectangular() {
        val canvas = WorkspaceCanvas(listOf(
            cell("a", 0f, 0f, .5f, .5f),
            cell("b", .5f, 0f, 1f, 1f),
            cell("c", 0f, .5f, .5f, 1f),
        ))
        assertEquals(WorkspaceMergeFailureReason.NON_RECTANGULAR_UNION, failure(canvas, "a", "b"))
        assertFalse(editor.findMergeCandidates(canvas, "a").any { it.targetCellId == "b" })
    }

    @Test fun nestedLayoutReturnsOnlyFullEdgeCandidate() {
        val canvas = WorkspaceCanvas(listOf(
            cell("left", 0f, 0f, .5f, 1f),
            cell("topRight", .5f, 0f, 1f, .5f),
            cell("bottomRight", .5f, .5f, 1f, 1f),
        ))
        assertEquals(listOf("bottomRight"), editor.findMergeCandidates(canvas, "topRight").map { it.targetCellId })
    }

    @Test fun unrelatedCellsRemainUnchanged() {
        val untouched = cell("bottom", 0f, .5f, 1f, 1f)
        val canvas = WorkspaceCanvas(listOf(cell("left", 0f, 0f, .5f, .5f), cell("right", .5f, 0f, 1f, .5f), untouched))
        val result = editor.mergeCells(canvas, "left", "right") as WorkspaceMergeResult.Success
        assertEquals(untouched, result.canvas.cells.first { it.id == "bottom" })
    }

    @Test fun mergePreservesFullCanvasArea() {
        val result = editor.mergeCells(twoColumns(false, false), "left", "right") as WorkspaceMergeResult.Success
        val area = result.canvas.cells.sumOf { (it.bounds.width * it.bounds.height).toDouble() }.toFloat()
        assertEquals(1f, area, 0.00001f)
    }

    @Test fun targetAssignmentWins() {
        val result = editor.mergeCells(twoColumns(true, true), "left", "right") as WorkspaceMergeResult.Success
        assertEquals("right.app", result.canvas.cells.single().app?.packageName)
    }

    @Test fun sourceAssignmentMovesWhenTargetIsEmpty() {
        val result = editor.mergeCells(twoColumns(true, false), "left", "right") as WorkspaceMergeResult.Success
        assertEquals("left.app", result.canvas.cells.single().app?.packageName)
    }

    @Test fun deterministicCandidateOrderUsesDirectionThenTargetId() {
        val source = cell("source", .25f, 0f, .75f, .5f)
        val canvas = WorkspaceCanvas(listOf(
            source,
            cell("zRight", .75f, 0f, 1f, .5f),
            cell("aLeft", 0f, 0f, .25f, .5f),
            cell("down", .25f, .5f, .75f, 1f),
        ))
        val candidates = editor.findMergeCandidates(canvas, "source")
        assertEquals(listOf(WorkspaceMergeDirection.LEFT, WorkspaceMergeDirection.RIGHT, WorkspaceMergeDirection.DOWN), candidates.map { it.direction })
    }

    @Test fun oneCellHasNoCandidate() {
        assertTrue(editor.findMergeCandidates(WorkspaceCanvas.singleCell(), "cell").isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun findingCandidatesRejectsMissingSource() {
        editor.findMergeCandidates(WorkspaceCanvas.singleCell(), "missing")
    }

    @Test fun reversingSourceAndTargetReversesDirectionButKeepsRequestedTarget() {
        val canvas = twoColumns(false, false)
        val result = editor.mergeCells(canvas, "right", "left") as WorkspaceMergeResult.Success
        assertEquals(WorkspaceMergeDirection.LEFT, result.candidate.direction)
        assertEquals("left", result.canvas.cells.single().id)
    }

    @Test fun candidatesNeverDuplicateTarget() {
        val candidates = editor.findMergeCandidates(twoColumns(false, false), "left")
        assertEquals(candidates.map { it.targetCellId }.distinct(), candidates.map { it.targetCellId })
    }

    private fun failure(canvas: WorkspaceCanvas, source: String, target: String) =
        (editor.mergeCells(canvas, source, target) as WorkspaceMergeResult.Failure).reason

    private fun twoColumns(appOnFirst: Boolean, appOnSecond: Boolean) = WorkspaceCanvas(listOf(
        cell("left", 0f, 0f, .5f, 1f, if (appOnFirst) app("left") else null),
        cell("right", .5f, 0f, 1f, 1f, if (appOnSecond) app("right") else null),
    ))

    private fun cell(id: String, left: Float, top: Float, right: Float, bottom: Float, app: AssignedApp? = null) =
        WorkspaceCell(id, NormalizedBounds(left, top, right, bottom), app)

    private fun app(name: String) = AssignedApp("$name.app", "$name.Activity", name)
}
