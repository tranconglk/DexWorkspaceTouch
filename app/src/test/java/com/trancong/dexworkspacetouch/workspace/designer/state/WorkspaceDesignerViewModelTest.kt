package com.trancong.dexworkspacetouch.workspace.designer.state

import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.SplitDirection
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.ui.layout.fitSize
import com.trancong.dexworkspacetouch.workspace.templates.WorkspaceTemplateCatalog
import org.junit.Assert.assertEquals
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
        val fiveCells = WorkspaceTemplateCatalog.default().find("three-top-two-bottom")!!.factory()
        viewModel.loadCanvas(fiveCells)
        val selected = fiveCells.cells.first().id
        viewModel.selectCell(selected)

        assertEquals(SplitResult.MaximumCellsReached, viewModel.splitSelectedCell(SplitDirection.VERTICAL))
        assertEquals(fiveCells, viewModel.canvas)
        assertEquals(selected, viewModel.selectedCellId)
        assertEquals(false, viewModel.canUndo)
    }


    private fun selectedViewModel() = WorkspaceDesignerViewModel().also { it.selectCell("cell") }

    private fun collectCanvas(viewModel: WorkspaceDesignerViewModel) = viewModel.canvas

    private val chrome = AssignedApp(
        packageName = "com.android.chrome",
        activityName = "com.google.android.apps.chrome.Main",
        label = "Chrome",
    )
}
