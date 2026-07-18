package com.trancong.dexworkspacetouch.workspace.library.model

import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas

data class WorkspaceLibraryItem(
    val id: String,
    val name: String,
    val canvas: WorkspaceCanvas,
    val modifiedSequence: Long,
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(name.isNotBlank()) { "name must not be blank" }
        require(modifiedSequence > 0L) { "modifiedSequence must be positive" }
        require(createdAtEpochMillis >= 0L) { "createdAtEpochMillis must be non-negative" }
        require(updatedAtEpochMillis >= createdAtEpochMillis) { "updatedAtEpochMillis must not precede createdAt" }
    }

    val appCount: Int get() = canvas.cells.count { it.app != null }
}
