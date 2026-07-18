package com.trancong.dexworkspacetouch.workspace.designer.state

import androidx.lifecycle.ViewModel
import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.SplitDirection
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceMergeCandidate
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceMergeResult

class WorkspaceDesignerViewModel : ViewModel() {
    private val stateHolder = WorkspaceDesignerStateHolder(WorkspaceCanvas.singleCell())
    private var appPickerNavigationPending = false

    val canvas: WorkspaceCanvas get() = stateHolder.canvas
    val selectedCellId: String? get() = stateHolder.selectedCellId
    val selectedDividerId: String? get() = stateHolder.selectedDividerId
    val canUndo: Boolean get() = stateHolder.canUndo
    val canRedo: Boolean get() = stateHolder.canRedo
    val summary: WorkspaceDesignerSummary get() = WorkspaceDesignerSummary.from(canvas)
    val canSplit: Boolean get() = canvas.cells.size < com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceLimits.MaxCells
    val toolbarState: DesignerContextToolbarState
        get() = DesignerContextToolbarState.from(
            canvas = canvas,
            selectedCellId = selectedCellId,
            selectedDividerId = selectedDividerId,
            canUndo = canUndo,
            canRedo = canRedo,
        )
    val mergeCandidates: List<WorkspaceMergeCandidate>
        get() = stateHolder.findMergeCandidates()

    fun loadCanvas(canvas: WorkspaceCanvas) {
        appPickerNavigationPending = false
        stateHolder.loadCanvas(canvas)
    }

    fun selectCell(cellId: String) = stateHolder.selectCell(cellId)

    fun activateCell(cellId: String): WorkspaceCellActivation? {
        if (appPickerNavigationPending) return null
        stateHolder.selectCell(cellId)
        appPickerNavigationPending = true
        val assignment = canvas.cells.first { it.id == cellId }.app
        return WorkspaceCellActivation(cellId, assignment)
    }

    fun onAppPickerClosed() {
        appPickerNavigationPending = false
    }

    fun assignApp(cellId: String, app: AssignedApp) = stateHolder.assignApp(cellId, app)

    fun clearSelection() = stateHolder.clearSelection()

    fun selectDivider(dividerId: String) = stateHolder.selectDivider(dividerId)

    fun clearDividerSelection() = stateHolder.clearDividerSelection()

    fun resizeDivider(dividerId: String, ratio: Float): Boolean =
        stateHolder.resizeDivider(dividerId, ratio)

    fun beginDividerResize(dividerId: String) = stateHolder.beginDividerResize(dividerId)

    fun updateDividerResize(ratio: Float) = stateHolder.updateDividerResize(ratio)

    fun commitDividerResize(): Boolean = stateHolder.commitDividerResize()

    fun cancelDividerResize() = stateHolder.cancelDividerResize()

    fun splitSelectedCell(direction: SplitDirection): SplitResult =
        stateHolder.splitSelectedCell(direction)

    fun mergeSelectedCell(targetCellId: String): WorkspaceMergeResult =
        stateHolder.mergeSelectedCell(targetCellId)

    fun undo(): Boolean = stateHolder.undo()

    fun redo(): Boolean = stateHolder.redo()
}

data class WorkspaceCellActivation(
    val cellId: String,
    val currentAssignment: AssignedApp?,
)
