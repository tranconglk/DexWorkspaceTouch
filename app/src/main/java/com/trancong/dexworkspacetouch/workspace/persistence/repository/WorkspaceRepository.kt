package com.trancong.dexworkspacetouch.workspace.persistence.repository

import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import kotlinx.coroutines.flow.Flow

interface WorkspaceRepository {
    fun observeAll(): Flow<List<Workspace>>
    suspend fun getById(id: String): Workspace?
    suspend fun insert(workspace: Workspace)
    suspend fun update(workspace: Workspace)
    suspend fun deleteById(id: String)
    suspend fun exists(id: String): Boolean
    suspend fun count(): Int
}
