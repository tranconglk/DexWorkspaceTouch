package com.trancong.dexworkspacetouch.workspace.persistence.repository

import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class WorkspaceRepositorySnapshot(
    val workspaces: List<Workspace>,
    val issues: List<WorkspacePersistenceIssue> = emptyList(),
)

sealed interface WorkspacePersistenceIssue {
    val workspaceId: String

    data class CorruptedRow(override val workspaceId: String) : WorkspacePersistenceIssue
    data class UnsupportedSchema(
        override val workspaceId: String,
        val schemaVersion: Int,
    ) : WorkspacePersistenceIssue
}

interface WorkspaceRepository {
    fun observeAll(): Flow<List<Workspace>>
    fun observeSnapshot(): Flow<WorkspaceRepositorySnapshot> =
        observeAll().map(::WorkspaceRepositorySnapshot)
    suspend fun getById(id: String): Workspace?
    suspend fun insert(workspace: Workspace)
    suspend fun update(workspace: Workspace)
    suspend fun deleteById(id: String)
    suspend fun exists(id: String): Boolean
    suspend fun count(): Int
    suspend fun setPinned(id: String, isPinned: Boolean) {
        throw UnsupportedOperationException("Pin is not supported")
    }
}
