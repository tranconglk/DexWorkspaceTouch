package com.trancong.dexworkspacetouch.workspace.designer.state

import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.SplitDirection
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvasValidator
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell
import com.trancong.dexworkspacetouch.workspace.designer.model.dividers
import com.trancong.dexworkspacetouch.workspace.designer.model.snapRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceDesignerStateHolderTest {
    @Test
    fun repeatedUpdatesAroundOneSnapPointStillCreateOneHistoryEntry() {
        val state = dividerState()
        val original = state.canvas
        val dividerId = original.dividers().single().id
        val snapPoints = listOf(0.25f, 1f / 3f, 0.5f, 2f / 3f, 0.75f)

        state.beginDividerResize(dividerId)
        listOf(0.64f, 0.65f, 0.67f, 0.68f).forEach { rawRatio ->
            state.updateDividerResize(snapRatio(rawRatio, snapPoints, 0.03f).ratio)
        }
        val snappedCanvas = state.canvas

        assertTrue(state.commitDividerResize())
        assertTrue(state.undo())
        assertEquals(original, state.canvas)
        assertFalse(state.canUndo)
        assertTrue(state.redo())
        assertEquals(snappedCanvas, state.canvas)
    }
    @Test
    fun dividerDragWithManyUpdatesCreatesOneHistoryEntryAndSupportsRedo() {
        val state = dividerState()
        val original = state.canvas
        val dividerId = original.dividers().single().id

        state.beginDividerResize(dividerId)
        state.updateDividerResize(0.55f)
        state.updateDividerResize(0.6f)
        state.updateDividerResize(0.65f)
        val resized = state.canvas

        assertTrue(state.commitDividerResize())
        assertTrue(state.undo())
        assertEquals(original, state.canvas)
        assertFalse(state.canUndo)
        assertTrue(state.redo())
        assertEquals(resized, state.canvas)
    }

    @Test
    fun cancellingDividerDragRestoresCanvasWithoutHistoryEntry() {
        val state = dividerState()
        val original = state.canvas
        val dividerId = original.dividers().single().id

        state.beginDividerResize(dividerId)
        state.updateDividerResize(0.7f)
        state.cancelDividerResize()

        assertEquals(original, state.canvas)
        assertFalse(state.canUndo)
    }

    @Test
    fun committingUnchangedDividerDragDoesNotAddHistory() {
        val state = dividerState()
        val dividerId = state.canvas.dividers().single().id

        state.beginDividerResize(dividerId)

        assertFalse(state.commitDividerResize())
        assertFalse(state.canUndo)
    }

    @Test
    fun liveDividerDragClampsBelowAndAboveEditorLimits() {
        val state = dividerState()
        val dividerId = state.canvas.dividers().single().id

        state.beginDividerResize(dividerId)
        state.updateDividerResize(-1f)
        assertEquals(0.2f, state.canvas.dividers().single().ratio, 0.0001f)
        state.updateDividerResize(2f)
        assertEquals(0.8f, state.canvas.dividers().single().ratio, 0.0001f)
        state.cancelDividerResize()
    }

    @Test
    fun dividerDragPreservesAssignedApp() {
        val app = assignedApp("Chrome", 1)
        val state = dividerState(app)
        val dividerId = state.canvas.dividers().single().id

        state.beginDividerResize(dividerId)
        state.updateDividerResize(snapRatio(0.74f, SNAP_POINTS, 0.03f).ratio)
        state.commitDividerResize()

        assertEquals(app, state.canvas.cells.first().app)
    }

    @Test
    fun nestedDividerLiveResizeRemainsValidWithoutGapOrOverlap() {
        val editor = com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvasEditor()
        val vertical = editor.splitCell(WorkspaceCanvas.singleCell(), "cell", SplitDirection.VERTICAL)
        val nested = editor.splitCell(vertical, "cell_b", SplitDirection.HORIZONTAL)
        val state = WorkspaceDesignerStateHolder(nested)
        val dividerId = nested.dividers().single { it.direction == SplitDirection.VERTICAL }.id

        state.beginDividerResize(dividerId)
        state.updateDividerResize(snapRatio(0.65f, SNAP_POINTS, 0.03f).ratio)

        assertTrue(WorkspaceCanvasValidator().validate(state.canvas).isEmpty())
        assertTrue(state.commitDividerResize())
    }
    @Test
    fun dividerResizeParticipatesInUndoAndRedoHistory() {
        val state = selectedSingleCellState()
        state.splitSelectedCell(SplitDirection.VERTICAL)
        val dividerId = state.canvas.dividers().single().id
        state.selectDivider(dividerId)

        assertTrue(state.resizeDivider(dividerId, 0.55f))
        assertEquals(0.55f, state.canvas.dividers().single().ratio, 0.0001f)
        assertEquals(dividerId, state.selectedDividerId)
        assertTrue(state.undo())
        assertEquals(0.5f, state.canvas.dividers().single().ratio, 0.0001f)
        assertTrue(state.redo())
        assertEquals(0.55f, state.canvas.dividers().single().ratio, 0.0001f)
    }

    @Test
    fun selectingDividerClearsCellSelectionAndCanBeCleared() {
        val state = selectedSingleCellState()
        state.splitSelectedCell(SplitDirection.VERTICAL)
        val dividerId = state.canvas.dividers().single().id

        state.selectDivider(dividerId)
        assertEquals(null, state.selectedCellId)
        assertEquals(dividerId, state.selectedDividerId)
        state.clearDividerSelection()
        assertEquals(null, state.selectedDividerId)
    }
    @Test fun splitsSelectedCellHorizontally() {
        val state = selectedSingleCellState()
        assertTrue(state.splitSelectedCell(SplitDirection.HORIZONTAL) is SplitResult.Success)
        assertEquals(NormalizedBounds(0f, 0f, 1f, 0.5f), state.canvas.cells[0].bounds)
        assertEquals(NormalizedBounds(0f, 0.5f, 1f, 1f), state.canvas.cells[1].bounds)
    }

    @Test fun splitsSelectedCellVertically() {
        val state = selectedSingleCellState()
        assertTrue(state.splitSelectedCell(SplitDirection.VERTICAL) is SplitResult.Success)
        assertEquals(NormalizedBounds(0f, 0f, 0.5f, 1f), state.canvas.cells[0].bounds)
        assertEquals(NormalizedBounds(0.5f, 0f, 1f, 1f), state.canvas.cells[1].bounds)
    }

    @Test fun assignedAppStaysOnFirstChild() {
        val app = AssignedApp("com.android.chrome", "com.google.android.apps.chrome.Main", "Chrome")
        val state = WorkspaceDesignerStateHolder(
            WorkspaceCanvas(listOf(WorkspaceCell("cell", NormalizedBounds.FullCanvas, app))),
        )
        state.selectCell("cell")
        state.splitSelectedCell(SplitDirection.VERTICAL)
        assertEquals(app, state.canvas.cells[0].app)
    }

    @Test fun secondChildIsEmpty() {
        val app = AssignedApp("com.android.chrome", "com.google.android.apps.chrome.Main", "Chrome")
        val state = WorkspaceDesignerStateHolder(
            WorkspaceCanvas(listOf(WorkspaceCell("cell", NormalizedBounds.FullCanvas, app))),
        )
        state.selectCell("cell")
        state.splitSelectedCell(SplitDirection.VERTICAL)
        assertNull(state.canvas.cells[1].app)
    }

    @Test fun selectionMovesToFirstChild() {
        val state = selectedSingleCellState()
        val result = state.splitSelectedCell(SplitDirection.VERTICAL) as SplitResult.Success
        assertEquals(result.selectedCellId, state.selectedCellId)
        assertEquals(state.canvas.cells[0].id, state.selectedCellId)
    }

    @Test fun doesNotSplitAtMaximumCellCount() {
        val cells = listOf(
            WorkspaceCell("one", NormalizedBounds(0f, 0f, 0.5f, 0.5f)),
            WorkspaceCell("two", NormalizedBounds(0.5f, 0f, 1f, 0.5f)),
            WorkspaceCell("three", NormalizedBounds(0f, 0.5f, 0.5f, 1f)),
            WorkspaceCell("four", NormalizedBounds(0.5f, 0.5f, 1f, 0.75f)),
            WorkspaceCell("five", NormalizedBounds(0.5f, 0.75f, 1f, 1f)),
        )
        val state = WorkspaceDesignerStateHolder(WorkspaceCanvas(cells))
        state.selectCell("one")
        assertEquals(SplitResult.MaximumCellsReached, state.splitSelectedCell(SplitDirection.VERTICAL))
        assertEquals(cells, state.canvas.cells)
    }

    @Test fun doesNotSplitCellThatIsTooSmall() {
        val cell = WorkspaceCell("narrow", NormalizedBounds(0f, 0f, 0.3f, 1f))
        val state = WorkspaceDesignerStateHolder(WorkspaceCanvas(listOf(cell)))
        state.selectCell("narrow")
        assertEquals(SplitResult.CellTooSmall, state.splitSelectedCell(SplitDirection.VERTICAL))
        assertEquals(listOf(cell), state.canvas.cells)
    }

    @Test fun doesNotSplitWithoutSelection() {
        val state = WorkspaceDesignerStateHolder(WorkspaceCanvas.singleCell())
        assertEquals(SplitResult.NoSelection, state.splitSelectedCell(SplitDirection.VERTICAL))
        assertEquals(1, state.canvas.cells.size)
    }

    @Test fun canvasRemainsValidAfterMultipleSplits() {
        val state = selectedSingleCellState()
        state.splitSelectedCell(SplitDirection.VERTICAL)
        state.splitSelectedCell(SplitDirection.HORIZONTAL)
        state.selectCell("cell_b")
        state.splitSelectedCell(SplitDirection.HORIZONTAL)
        assertEquals(4, state.canvas.cells.size)
        assertTrue(WorkspaceCanvasValidator().validate(state.canvas).isEmpty())
    }

    @Test fun unrelatedCellRemainsUnchanged() {
        val left = WorkspaceCell("left", NormalizedBounds(0f, 0f, 0.5f, 1f))
        val right = WorkspaceCell("right", NormalizedBounds(0.5f, 0f, 1f, 1f))
        val state = WorkspaceDesignerStateHolder(WorkspaceCanvas(listOf(left, right)))
        state.selectCell("left")
        state.splitSelectedCell(SplitDirection.HORIZONTAL)
        assertEquals(right, state.canvas.cells.last())
    }

    @Test fun undoRestoresCanvasBeforeSplit() {
        val state = selectedSingleCellState()
        val original = state.canvas
        state.splitSelectedCell(SplitDirection.VERTICAL)

        assertTrue(state.undo())
        assertEquals(original, state.canvas)
        assertFalse(state.canUndo)
        assertTrue(state.canRedo)
    }

    @Test fun undoRestoresCanvasBeforeAssigningApp() {
        val state = selectedSingleCellState()
        val original = state.canvas
        state.assignApp("cell", assignedApp("Chrome", 1))

        assertTrue(state.undo())
        assertEquals(original, state.canvas)
    }

    @Test fun assigningAppKeepsRequestedCellSelected() {
        val state = selectedSingleCellState()
        state.assignApp("cell", assignedApp("Chrome", 1))

        assertEquals("cell", state.selectedCellId)
        assertEquals("Chrome", state.canvas.cells.single().app?.label)
    }

    @Test fun redoRestoresUndoneChange() {
        val state = selectedSingleCellState()
        state.assignApp("cell", assignedApp("Chrome", 1))
        val assigned = state.canvas
        state.undo()

        assertTrue(state.redo())
        assertEquals(assigned, state.canvas)
        assertTrue(state.canUndo)
        assertFalse(state.canRedo)
    }

    @Test fun duplicateCanvasStateIsNotAddedToHistory() {
        val state = selectedSingleCellState()
        val app = assignedApp("Chrome", 1)
        state.assignApp("cell", app)
        state.assignApp("cell", app)

        assertTrue(state.undo())
        assertNull(state.canvas.cells.single().app)
        assertFalse(state.undo())
    }

    @Test fun newChangeAfterUndoClearsRedoHistory() {
        val state = selectedSingleCellState()
        state.assignApp("cell", assignedApp("Chrome", 1))
        state.undo()
        state.assignApp("cell", assignedApp("Maps", 2))

        assertFalse(state.canRedo)
        assertFalse(state.redo())
    }

    @Test fun historyKeepsOnlyTwentyMostRecentStates() {
        val state = selectedSingleCellState()
        repeat(25) { index ->
            state.assignApp("cell", assignedApp("App $index", index))
        }

        var undoCount = 0
        while (state.undo()) undoCount++
        assertEquals(20, undoCount)
        assertEquals("App 4", state.canvas.cells.single().app?.label)
    }

    private fun assignedApp(label: String, index: Int) = AssignedApp(
        packageName = "com.example.app$index",
        activityName = "com.example.app$index.MainActivity",
        label = label,
    )

    private fun selectedSingleCellState(): WorkspaceDesignerStateHolder =
        WorkspaceDesignerStateHolder(WorkspaceCanvas.singleCell()).also { it.selectCell("cell") }

    private fun dividerState(app: AssignedApp? = null): WorkspaceDesignerStateHolder {
        val source = WorkspaceCanvas(
            listOf(com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell(
                id = "cell",
                bounds = NormalizedBounds.FullCanvas,
                app = app,
            )),
        )
        val canvas = com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvasEditor()
            .splitCell(source, "cell", SplitDirection.VERTICAL)
        return WorkspaceDesignerStateHolder(canvas)
    }

    private companion object {
        val SNAP_POINTS = listOf(0.25f, 1f / 3f, 0.5f, 2f / 3f, 0.75f)
    }
}
