package com.trancong.dexworkspacetouch.workspace.designer.ui

import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell

internal object WorkspaceCanvasPreviewData {
    fun singleCellCanvas(): WorkspaceCanvas = WorkspaceCanvas.singleCell()

    fun twoVerticalCellsCanvas(): WorkspaceCanvas = WorkspaceCanvas(
        cells = listOf(
            WorkspaceCell("left", NormalizedBounds(0f, 0f, 0.5f, 1f)),
            WorkspaceCell("right", NormalizedBounds(0.5f, 0f, 1f, 1f)),
        ),
    )

    fun threeCellsCanvas(): WorkspaceCanvas = WorkspaceCanvas(
        cells = listOf(
            WorkspaceCell(
                id = "primary",
                bounds = NormalizedBounds(0f, 0f, 0.6f, 1f),
                app = AssignedApp(
                    packageName = "com.example.notes",
                    activityName = "com.example.notes.MainActivity",
                    label = "Ghi chú",
                ),
            ),
            WorkspaceCell("top-right", NormalizedBounds(0.6f, 0f, 1f, 0.5f)),
            WorkspaceCell("bottom-right", NormalizedBounds(0.6f, 0.5f, 1f, 1f)),
        ),
    )
}
