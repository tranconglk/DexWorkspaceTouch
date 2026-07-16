package com.trancong.dexworkspacetouch.workspace.designer.state

import androidx.lifecycle.ViewModel
import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.SplitDirection
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas

class WorkspaceDesignerViewModel : ViewModel() {
    private val stateHolder = WorkspaceDesignerStateHolder(WorkspaceCanvas.singleCell())

    val canvas: WorkspaceCanvas get() = stateHolder.canvas
    val selectedCellId: String? get() = stateHolder.selectedCellId
    val canUndo: Boolean get() = stateHolder.canUndo
    val canRedo: Boolean get() = stateHolder.canRedo

    fun selectCell(cellId: String) = stateHolder.selectCell(cellId)

    fun assignApp(cellId: String, app: AssignedApp) = stateHolder.assignApp(cellId, app)

    fun clearSelection() = stateHolder.clearSelection()

    fun splitSelectedCell(direction: SplitDirection): SplitResult =
        stateHolder.splitSelectedCell(direction)

    fun undo(): Boolean = stateHolder.undo()

    fun redo(): Boolean = stateHolder.redo()
}
