package com.trancong.dexworkspacetouch.workspace.persistence.repository

import android.database.sqlite.SQLiteConstraintException
import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import com.trancong.dexworkspacetouch.workspace.persistence.domain.WorkspacePersistenceException
import com.trancong.dexworkspacetouch.workspace.persistence.room.WorkspaceDao
import com.trancong.dexworkspacetouch.workspace.persistence.room.toDomain
import com.trancong.dexworkspacetouch.workspace.persistence.room.toEntity
import com.trancong.dexworkspacetouch.workspace.persistence.serialization.WorkspaceCanvasSerializer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class RoomWorkspaceRepository(
    private val dao: WorkspaceDao,
    private val serializer: WorkspaceCanvasSerializer,
) : WorkspaceRepository {
    override fun observeAll(): Flow<List<Workspace>> = observeSnapshot().map { it.workspaces }

    override fun observeSnapshot(): Flow<WorkspaceRepositorySnapshot> = dao.observeAll()
        .map { entities ->
            val workspaces = mutableListOf<Workspace>()
            val issues = mutableListOf<WorkspacePersistenceIssue>()
            entities.forEach { entity ->
                try {
                    workspaces += entity.toDomain(serializer)
                } catch (error: WorkspacePersistenceException.UnsupportedSchema) {
                    issues += WorkspacePersistenceIssue.UnsupportedSchema(entity.id, error.schemaVersion)
                } catch (_: WorkspacePersistenceException.SerializationFailure) {
                    issues += WorkspacePersistenceIssue.CorruptedRow(entity.id)
                } catch (_: IllegalArgumentException) {
                    issues += WorkspacePersistenceIssue.CorruptedRow(entity.id)
                }
            }
            WorkspaceRepositorySnapshot(workspaces, issues)
        }
        .catch { error -> throw mapFailure("observe workspaces", error) }

    override suspend fun getById(id: String): Workspace? = databaseCall("read workspace '$id'") {
        dao.getById(id)?.toDomain(serializer)
    }

    override suspend fun insert(workspace: Workspace) {
        if (databaseCall("check workspace '${workspace.id}'") { dao.exists(workspace.id) }) {
            throw WorkspacePersistenceException.DuplicateId(workspace.id)
        }
        try {
            databaseCall("insert workspace '${workspace.id}'") {
                dao.insert(workspace.toEntity(serializer))
            }
        } catch (error: WorkspacePersistenceException.DatabaseFailure) {
            if (error.cause is SQLiteConstraintException) {
                throw WorkspacePersistenceException.DuplicateId(workspace.id)
            }
            throw error
        }
    }

    override suspend fun update(workspace: Workspace) =
        databaseCall("update workspace '${workspace.id}'") { dao.update(workspace.toEntity(serializer)) }

    override suspend fun deleteById(id: String) =
        databaseCall("delete workspace '$id'") { dao.deleteById(id) }

    override suspend fun exists(id: String): Boolean =
        databaseCall("check workspace '$id'") { dao.exists(id) }

    override suspend fun count(): Int = databaseCall("count workspaces") { dao.count() }

    private suspend fun <T> databaseCall(action: String, block: suspend () -> T): T = try {
        block()
    } catch (error: Throwable) {
        throw mapFailure(action, error)
    }

    private fun mapFailure(action: String, error: Throwable): Throwable = when (error) {
        is CancellationException -> error
        is WorkspacePersistenceException -> error
        else -> WorkspacePersistenceException.DatabaseFailure("Unable to $action", error)
    }
}
