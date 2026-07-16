package com.trancong.dexworkspacetouch.workspace.designer.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvasEditor
import com.trancong.dexworkspacetouch.workspace.designer.model.SplitDirection
import com.trancong.dexworkspacetouch.workspace.designer.model.assignApp
import com.trancong.dexworkspacetouch.workspace.designer.model.dividers

class WorkspaceDesignerStateHolder(
    initialCanvas: WorkspaceCanvas,
    private val editor: WorkspaceCanvasEditor = WorkspaceCanvasEditor(),
) {
    var canvas by mutableStateOf(initialCanvas)
        private set

    var selectedCellId by mutableStateOf<String?>(null)
        private set

    var selectedDividerId by mutableStateOf<String?>(null)
        private set

    private var undoHistory by mutableStateOf<List<WorkspaceCanvas>>(emptyList())
    private var redoHistory by mutableStateOf<List<WorkspaceCanvas>>(emptyList())

    val canUndo: Boolean get() = undoHistory.isNotEmpty()
    val canRedo: Boolean get() = redoHistory.isNotEmpty()

    fun selectCell(cellId: String) {
        require(canvas.cells.any { it.id == cellId }) { "Cell '$cellId' does not exist" }
        selectedCellId = cellId
        selectedDividerId = null
    }

    fun assignApp(cellId: String, app: AssignedApp) {
        updateCanvas(canvas.assignApp(cellId, app))
        selectedCellId = cellId
        selectedDividerId = null
    }

    fun clearSelection() {
        selectedCellId = null
        selectedDividerId = null
    }

    fun selectDivider(dividerId: String) {
        require(canvas.dividers().any { it.id == dividerId }) { "Divider '$dividerId' does not exist" }
        selectedDividerId = dividerId
        selectedCellId = null
    }

    fun clearDividerSelection() {
        selectedDividerId = null
    }

    fun resizeDivider(dividerId: String, ratio: Float): Boolean {
        val changed = updateCanvas(editor.resizeDivider(canvas, dividerId, ratio))
        selectedDividerId = dividerId
        selectedCellId = null
        return changed
    }

    fun splitSelectedCell(direction: SplitDirection): SplitResult {
        val cellId = selectedCellId ?: return SplitResult.NoSelection
        val cell = canvas.cells.firstOrNull { it.id == cellId }
            ?: return SplitResult.CellNotFound
        if (canvas.cells.size >= MAX_CELLS) return SplitResult.MaximumCellsReached

        val splitSize = when (direction) {
            SplitDirection.HORIZONTAL -> cell.bounds.height / 2f
            SplitDirection.VERTICAL -> cell.bounds.width / 2f
        }
        if (splitSize < MIN_CHILD_SIZE) return SplitResult.CellTooSmall

        val sourceIndex = canvas.cells.indexOf(cell)
        val updatedCanvas = editor.splitCell(canvas, cellId, direction, ratio = 0.5f)
        updateCanvas(updatedCanvas)
        val firstCellId = canvas.cells[sourceIndex].id
        selectedCellId = firstCellId
        selectedDividerId = null
        return SplitResult.Success(firstCellId)
    }

    fun undo(): Boolean {
        val previous = undoHistory.lastOrNull() ?: return false
        undoHistory = undoHistory.dropLast(1)
        redoHistory = appendLimited(redoHistory, canvas)
        canvas = previous
        reconcileSelection()
        return true
    }

    fun redo(): Boolean {
        val next = redoHistory.lastOrNull() ?: return false
        redoHistory = redoHistory.dropLast(1)
        undoHistory = appendLimited(undoHistory, canvas)
        canvas = next
        reconcileSelection()
        return true
    }

    private fun updateCanvas(updatedCanvas: WorkspaceCanvas): Boolean {
        if (updatedCanvas == canvas) return false
        undoHistory = appendLimited(undoHistory, canvas)
        redoHistory = emptyList()
        canvas = updatedCanvas
        return true
    }

    private fun appendLimited(
        history: List<WorkspaceCanvas>,
        snapshot: WorkspaceCanvas,
    ): List<WorkspaceCanvas> {
        if (history.lastOrNull() == snapshot) return history
        return (history + snapshot).takeLast(MAX_HISTORY)
    }

    private fun reconcileSelection() {
        selectedCellId = selectedCellId?.takeIf { selectedId ->
            canvas.cells.any { it.id == selectedId }
        }
        selectedDividerId = selectedDividerId?.takeIf { selectedId ->
            canvas.dividers().any { it.id == selectedId }
        }
    }

    private companion object {
        const val MAX_CELLS = 4
        const val MIN_CHILD_SIZE = 0.2f
        const val MAX_HISTORY = 20
    }
}
