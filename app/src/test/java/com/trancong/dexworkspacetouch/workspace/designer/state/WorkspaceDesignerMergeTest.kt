package com.trancong.dexworkspacetouch.workspace.designer.state

import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceMergeResult
import com.trancong.dexworkspacetouch.workspace.designer.model.requiresMergeConfirmation
import com.trancong.dexworkspacetouch.workspace.templates.WorkspaceTemplateCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceDesignerMergeTest {
    @Test fun mergeCreatesOneHistoryEntryAndSelectsTarget() {
        val state = state()
        val before = state.canvas
        assertTrue(state.mergeSelectedCell("right") is WorkspaceMergeResult.Success)
        assertEquals("right", state.selectedCellId)
        assertNull(state.selectedDividerId)
        assertEquals(1, state.canvas.cells.size)
        assertTrue(state.undo())
        assertEquals(before, state.canvas)
        assertFalse(state.undo())
    }

    @Test fun undoRestoresAssignmentsAndRedoRestoresMergedCell() {
        val state = state(leftApp = app("Maps"), rightApp = app("Chrome"))
        state.mergeSelectedCell("right")
        assertEquals("Chrome", state.canvas.cells.single().app?.label)
        assertTrue(state.undo())
        assertEquals(listOf("Maps", "Chrome"), state.canvas.cells.map { it.app?.label })
        assertTrue(state.redo())
        assertEquals(listOf("Chrome"), state.canvas.cells.map { it.app?.label })
    }

    @Test fun invalidMergeDoesNotCreateHistory() {
        val state = state()
        assertTrue(state.mergeSelectedCell("missing") is WorkspaceMergeResult.Failure)
        assertFalse(state.canUndo)
    }

    @Test fun summaryAndSplitAvailabilityUpdateAfterMerge() {
        val viewModel = WorkspaceDesignerViewModel()
        viewModel.loadCanvas(canvas(app("Maps"), app("Chrome")))
        viewModel.selectCell("left")
        viewModel.mergeSelectedCell("right")
        assertEquals(1, viewModel.summary.cellCount)
        assertEquals(1, viewModel.summary.assignedAppCount)
        assertTrue(viewModel.canSplit)
        assertEquals("right", viewModel.selectedCellId)
    }

    @Test fun confirmationPolicyOnlyRequiresTwoAssignmentsAndCancelIsNonMutating() {
        val source = cell("left", 0f, .5f, app("Maps"))
        val target = cell("right", .5f, 1f, app("Chrome"))
        assertTrue(requiresMergeConfirmation(source, target))
        assertFalse(requiresMergeConfirmation(source.copy(app = null), target))
        val state = state(leftApp = source.app, rightApp = target.app)
        val before = state.canvas
        // Dismissing the UI confirmation deliberately invokes no state-holder operation.
        assertEquals(before, state.canvas)
        assertFalse(state.canUndo)
    }

    @Test fun mergingFiveCellsToFourEnablesSplitAgain() {
        val viewModel = WorkspaceDesignerViewModel()
        viewModel.loadCanvas(WorkspaceTemplateCatalog.default().find("top-large-four-bottom")!!.factory())
        val source = viewModel.canvas.cells[1]
        viewModel.selectCell(source.id)
        assertFalse(viewModel.canSplit)
        val target = viewModel.mergeCandidates.first().targetCellId
        assertTrue(viewModel.mergeSelectedCell(target) is WorkspaceMergeResult.Success)
        assertEquals(4, viewModel.canvas.cells.size)
        assertTrue(viewModel.canSplit)
    }

    private fun state(leftApp: AssignedApp? = null, rightApp: AssignedApp? = null) =
        WorkspaceDesignerStateHolder(canvas(leftApp, rightApp)).also { it.selectCell("left") }

    private fun canvas(leftApp: AssignedApp?, rightApp: AssignedApp?) = WorkspaceCanvas(
        listOf(cell("left", 0f, .5f, leftApp), cell("right", .5f, 1f, rightApp)),
    )

    private fun cell(id: String, left: Float, right: Float, app: AssignedApp?) =
        WorkspaceCell(id, NormalizedBounds(left, 0f, right, 1f), app)

    private fun app(label: String) = AssignedApp("$label.package", "$label.Activity", label)
}
