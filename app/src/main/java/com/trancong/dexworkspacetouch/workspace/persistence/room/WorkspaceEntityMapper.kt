package com.trancong.dexworkspacetouch.workspace.persistence.room

import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import com.trancong.dexworkspacetouch.workspace.persistence.serialization.WorkspaceCanvasSerializer

fun Workspace.toEntity(serializer: WorkspaceCanvasSerializer): WorkspaceEntity = WorkspaceEntity(
    id = id,
    name = name,
    canvasJson = serializer.encode(canvas),
    modifiedSequence = modifiedSequence,
    schemaVersion = schemaVersion,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    isPinned = isPinned,
)

fun WorkspaceEntity.toDomain(serializer: WorkspaceCanvasSerializer): Workspace = Workspace(
    id = id,
    name = name,
    canvas = serializer.decode(canvasJson, schemaVersion),
    modifiedSequence = modifiedSequence,
    schemaVersion = schemaVersion,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    isPinned = isPinned,
)
