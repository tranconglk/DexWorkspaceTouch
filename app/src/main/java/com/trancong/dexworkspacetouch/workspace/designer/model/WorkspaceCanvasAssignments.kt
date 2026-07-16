package com.trancong.dexworkspacetouch.workspace.designer.model

fun WorkspaceCanvas.assignApp(cellId: String, app: AssignedApp): WorkspaceCanvas {
    require(cells.any { it.id == cellId }) { "Cell '$cellId' does not exist" }
    return copy(
        cells = cells.map { cell ->
            if (cell.id == cellId) cell.copy(app = app) else cell
        },
    )
}
