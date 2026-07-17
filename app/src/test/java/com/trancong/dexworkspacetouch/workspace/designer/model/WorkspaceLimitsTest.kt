package com.trancong.dexworkspacetouch.workspace.designer.model

import com.trancong.dexworkspacetouch.workspace.designer.state.SplitResult
import com.trancong.dexworkspacetouch.workspace.designer.state.WorkspaceDesignerStateHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceLimitsTest {
    @Test fun maximumCellContractIsFive() {
        assertEquals(5, WorkspaceLimits.MaxCells)
    }

    @Test fun editorAllowsFourToFiveAndRejectsFiveToSix() {
        val editor = WorkspaceCanvasEditor()
        val four = fourCells()
        val five = editor.splitCell(four, "top-left", SplitDirection.VERTICAL)

        assertEquals(WorkspaceLimits.MaxCells, five.cells.size)
        val failure = runCatching { editor.splitCell(five, five.cells.first().id, SplitDirection.HORIZONTAL) }
        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
        assertEquals(WorkspaceLimits.MaxCells, five.cells.size)
    }

    @Test fun stateFailureAtFiveDoesNotCreateHistory() {
        val state = WorkspaceDesignerStateHolder(fiveCells())
        state.selectCell(state.canvas.cells.first().id)

        assertEquals(SplitResult.MaximumCellsReached, state.splitSelectedCell(SplitDirection.VERTICAL))
        assertFalse(state.canUndo)
        assertEquals(WorkspaceLimits.MaxCells, state.canvas.cells.size)
    }

    @Test fun undoFromFiveToFourAllowsSplitAgain() {
        val state = WorkspaceDesignerStateHolder(fourCells())
        state.selectCell("top-left")
        assertTrue(state.splitSelectedCell(SplitDirection.VERTICAL) is SplitResult.Success)
        assertEquals(5, state.canvas.cells.size)

        assertTrue(state.undo())
        assertEquals(4, state.canvas.cells.size)
        state.selectCell("top-left")
        assertTrue(state.splitSelectedCell(SplitDirection.VERTICAL) is SplitResult.Success)
        assertEquals(5, state.canvas.cells.size)
    }

    private fun fiveCells(): WorkspaceCanvas = WorkspaceCanvasEditor().splitCell(
        fourCells(), "top-left", SplitDirection.VERTICAL,
    )

    private fun fourCells() = WorkspaceCanvas(
        listOf(
            WorkspaceCell("top-left", NormalizedBounds(0f, 0f, 0.5f, 0.5f)),
            WorkspaceCell("top-right", NormalizedBounds(0.5f, 0f, 1f, 0.5f)),
            WorkspaceCell("bottom-left", NormalizedBounds(0f, 0.5f, 0.5f, 1f)),
            WorkspaceCell("bottom-right", NormalizedBounds(0.5f, 0.5f, 1f, 1f)),
        ),
    )
}
