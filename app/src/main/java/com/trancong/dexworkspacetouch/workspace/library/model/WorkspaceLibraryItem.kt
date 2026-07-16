package com.trancong.dexworkspacetouch.workspace.library.model

import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas

data class WorkspaceLibraryItem(
    val id: String,
    val name: String,
    val canvas: WorkspaceCanvas,
    val modifiedSequence: Long,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(name.isNotBlank()) { "name must not be blank" }
        require(modifiedSequence > 0L) { "modifiedSequence must be positive" }
    }

    val appCount: Int get() = canvas.cells.count { it.app != null }
}
