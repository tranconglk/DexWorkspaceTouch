package com.trancong.dexworkspacetouch.workspace.persistence.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val canvasJson: String,
    val modifiedSequence: Long,
    val schemaVersion: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    @ColumnInfo(defaultValue = "0") val isPinned: Boolean = false,
)
