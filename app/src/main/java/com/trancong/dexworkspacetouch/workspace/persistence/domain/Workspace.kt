package com.trancong.dexworkspacetouch.workspace.persistence.domain

import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas

data class Workspace(
    val id: String,
    val name: String,
    val canvas: WorkspaceCanvas,
    val modifiedSequence: Long,
    val schemaVersion: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val isPinned: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(name.trim().isNotEmpty()) { "name must not be blank" }
        require(modifiedSequence >= 0) { "modifiedSequence must be non-negative" }
        require(schemaVersion >= 1) { "schemaVersion must be at least 1" }
        require(createdAtEpochMillis >= 0) { "createdAtEpochMillis must be non-negative" }
        require(updatedAtEpochMillis >= createdAtEpochMillis) {
            "updatedAtEpochMillis must not precede createdAtEpochMillis"
        }
    }
}
