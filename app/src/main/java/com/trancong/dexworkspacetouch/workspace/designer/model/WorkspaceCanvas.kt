package com.trancong.dexworkspacetouch.workspace.designer.model

data class WorkspaceCanvas(val cells: List<WorkspaceCell>) {
    companion object {
        fun singleCell(): WorkspaceCanvas = WorkspaceCanvas(
            cells = listOf(
                WorkspaceCell(
                    id = "cell",
                    bounds = NormalizedBounds.FullCanvas,
                ),
            ),
        )
    }
}
