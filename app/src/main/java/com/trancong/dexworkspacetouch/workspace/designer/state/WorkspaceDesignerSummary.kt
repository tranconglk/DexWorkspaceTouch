package com.trancong.dexworkspacetouch.workspace.designer.state

import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceLimits

data class WorkspaceDesignerSummary(
    val cellCount: Int,
    val maxCellCount: Int,
    val assignedAppCount: Int,
) {
    val statusText: String
        get() = "$cellCount / $maxCellCount ô • Đã gán $assignedAppCount ứng dụng"

    companion object {
        fun from(canvas: WorkspaceCanvas) = WorkspaceDesignerSummary(
            cellCount = canvas.cells.size,
            maxCellCount = WorkspaceLimits.MaxCells,
            assignedAppCount = canvas.cells.count { it.app != null },
        )
    }
}
