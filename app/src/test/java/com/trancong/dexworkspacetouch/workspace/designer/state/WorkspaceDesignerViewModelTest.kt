package com.trancong.dexworkspacetouch.workspace.designer.state

import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.SplitDirection
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.dividers
import com.trancong.dexworkspacetouch.workspace.designer.ui.layout.fitSize
import com.trancong.dexworkspacetouch.workspace.templates.WorkspaceTemplateCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceDesignerViewModelTest {
    @Test fun loadingWorkspaceReplacesCanvasAndClearsTransientState() {
        val viewModel = selectedViewModel()
        viewModel.assignApp("cell", chrome)

        viewModel.loadCanvas(WorkspaceCanvas.singleCell())

        assertEquals(WorkspaceCanvas.singleCell(), viewModel.canvas)
        assertEquals(null, viewModel.selectedCellId)
        assertEquals(false, viewModel.canUndo)
        assertEquals(false, viewModel.canRedo)
    }

    @Test fun assignedAppRemainsInViewModelState() {
        val viewModel = selectedViewModel()
        viewModel.assignApp("cell", chrome)

        assertEquals(chrome, viewModel.canvas.cells.single().app)
    }

    @Test fun recreatingUiCollectorDoesNotResetCanvas() {
        val viewModel = selectedViewModel()
        viewModel.assignApp("cell", chrome)

        val firstCollector = collectCanvas(viewModel)
        val recreatedCollector = collectCanvas(viewModel)

        assertEquals(firstCollector, recreatedCollector)
        assertEquals(chrome, recreatedCollector.cells.single().app)
    }

    @Test fun selectedCellRemainsInViewModelState() {
        val viewModel = selectedViewModel()
        viewModel.assignApp("cell", chrome)

        assertEquals("cell", viewModel.selectedCellId)
    }

    @Test fun appPickerCallbackUpdatesTheSharedStateOwner() {
        val viewModel = selectedViewModel()
        val onAppSelected: (String, AssignedApp) -> Unit = viewModel::assignApp

        onAppSelected("cell", chrome)

        assertEquals(chrome, viewModel.canvas.cells.single().app)
        assertEquals("cell", viewModel.selectedCellId)
    }

    @Test fun splitAndUndoStateAreNotResetByRepeatedReads() {
        val viewModel = selectedViewModel()
        assertTrue(viewModel.splitSelectedCell(SplitDirection.VERTICAL) is SplitResult.Success)
        val splitCanvas = collectCanvas(viewModel)

        assertEquals(splitCanvas, collectCanvas(viewModel))
        assertTrue(viewModel.undo())
        assertEquals(1, viewModel.canvas.cells.size)
    }

    @Test fun initializationDoesNotRunAgainAfterStateUpdate() {
        val viewModel = selectedViewModel()
        viewModel.assignApp("cell", chrome)

        repeat(3) { assertEquals(chrome, viewModel.canvas.cells.single().app) }
        assertTrue(viewModel.canUndo)
    }

    @Test fun layoutResizeDoesNotMutateCanvasAssignedAppOrSelection() {
        val viewModel = selectedViewModel()
        viewModel.assignApp("cell", chrome)
        val canvasBeforeResize = viewModel.canvas

        fitSize(1920f, 1080f, 16f / 10f)
        fitSize(1038f, 740f, 16f / 10f)

        assertSame(canvasBeforeResize, viewModel.canvas)
        assertEquals(chrome, viewModel.canvas.cells.single().app)
        assertEquals("cell", viewModel.selectedCellId)
    }

    @Test fun splitAtFiveCellsReturnsLimitWithoutChangingHistoryOrSelection() {
        val viewModel = WorkspaceDesignerViewModel()
        val fiveCells = WorkspaceTemplateCatalog.default().find("top-large-four-bottom")!!.factory()
        viewModel.loadCanvas(fiveCells)
        val selected = fiveCells.cells.first().id
        viewModel.selectCell(selected)

        assertEquals(SplitResult.MaximumCellsReached, viewModel.splitSelectedCell(SplitDirection.VERTICAL))
        assertEquals(fiveCells, viewModel.canvas)
        assertEquals(selected, viewModel.selectedCellId)
        assertEquals(false, viewModel.canUndo)
    }

    @Test fun activateCellSelectsCellClearsDividerAndCarriesCurrentAssignment() {
        val viewModel = WorkspaceDesignerViewModel()
        viewModel.selectCell("cell")
        viewModel.splitSelectedCell(SplitDirection.VERTICAL)
        val cell = viewModel.canvas.cells.first()
        viewModel.assignApp(cell.id, chrome)
        viewModel.selectDivider(viewModel.canvas.dividers().first().id)
        val event = viewModel.activateCell(cell.id)
        assertEquals(cell.id, event?.cellId)
        assertEquals(chrome, event?.currentAssignment)
        assertEquals(cell.id, viewModel.selectedCellId)
        assertNull(viewModel.selectedDividerId)
    }

    @Test fun emptyAndAssignedCellActivationEmitExactlyOnceUntilPickerCloses() {
        val viewModel = WorkspaceDesignerViewModel()
        val empty = viewModel.activateCell("cell")
        assertNull(empty?.currentAssignment)
        assertNull(viewModel.activateCell("cell"))
        viewModel.onAppPickerClosed()
        viewModel.assignApp("cell", chrome)
        assertEquals(chrome, viewModel.activateCell("cell")?.currentAssignment)
    }

    @Test fun collectorRecreationAndStateReadsDoNotRepeatActivation() {
        val viewModel = WorkspaceDesignerViewModel()
        assertEquals("cell", viewModel.activateCell("cell")?.cellId)
        repeat(3) {
            collectCanvas(viewModel)
            assertNull(viewModel.activateCell("cell"))
        }
        viewModel.onAppPickerClosed()
        assertEquals("cell", viewModel.activateCell("cell")?.cellId)
    }

    @Test fun assignmentUndoRedoKeepSelectionAndUpdateSummary() {
        val viewModel = selectedViewModel()
        assertEquals(WorkspaceDesignerSummary(1, 5, 0), viewModel.summary)
        viewModel.assignApp("cell", chrome)
        assertEquals("cell", viewModel.selectedCellId)
        assertEquals(1, viewModel.summary.assignedAppCount)
        assertTrue(viewModel.undo())
        assertNull(viewModel.canvas.cells.single().app)
        assertEquals(0, viewModel.summary.assignedAppCount)
        assertTrue(viewModel.redo())
        assertEquals(chrome, viewModel.canvas.cells.single().app)
        assertEquals(1, viewModel.summary.assignedAppCount)
    }

    @Test fun splitToFiveDisablesSplitAndUndoToFourEnablesItAgain() {
        val viewModel = WorkspaceDesignerViewModel()
        val fourCells = WorkspaceTemplateCatalog.default().find("four-grid")!!.factory()
        viewModel.loadCanvas(fourCells)
        viewModel.selectCell(fourCells.cells.first().id)
        assertTrue(viewModel.canSplit)
        assertTrue(viewModel.splitSelectedCell(SplitDirection.VERTICAL) is SplitResult.Success)
        assertEquals(5, viewModel.summary.cellCount)
        assertFalse(viewModel.canSplit)
        assertTrue(viewModel.undo())
        assertEquals(4, viewModel.summary.cellCount)
        assertTrue(viewModel.canSplit)
    }


    private fun selectedViewModel() = WorkspaceDesignerViewModel().also { it.selectCell("cell") }

    private fun collectCanvas(viewModel: WorkspaceDesignerViewModel) = viewModel.canvas

    private val chrome = AssignedApp(
        packageName = "com.android.chrome",
        activityName = "com.google.android.apps.chrome.Main",
        label = "Chrome",
    )
}
