package com.trancong.dexworkspacetouch.workspace.library.model

import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace

fun Workspace.toLibraryItem(): WorkspaceLibraryItem = WorkspaceLibraryItem(
    id = id,
    name = name,
    canvas = canvas,
    modifiedSequence = modifiedSequence,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun WorkspaceLibraryItem.toDomainWorkspace(
    schemaVersion: Int,
    createdAtEpochMillis: Long,
    updatedAtEpochMillis: Long,
): Workspace = Workspace(
    id = id,
    name = name,
    canvas = canvas,
    modifiedSequence = modifiedSequence,
    schemaVersion = schemaVersion,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)
