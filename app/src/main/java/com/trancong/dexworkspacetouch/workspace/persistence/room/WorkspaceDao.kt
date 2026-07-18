package com.trancong.dexworkspacetouch.workspace.persistence.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {
    @Query(
        "SELECT * FROM workspaces ORDER BY updatedAtEpochMillis DESC, " +
            "modifiedSequence DESC, name ASC, id ASC",
    )
    fun observeAll(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WorkspaceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: WorkspaceEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRows(entities: List<WorkspaceEntity>)

    @Transaction
    suspend fun insertAllAtomically(entities: List<WorkspaceEntity>) {
        insertRows(entities)
    }

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun updateRows(entity: WorkspaceEntity): Int

    suspend fun update(entity: WorkspaceEntity) {
        if (updateRows(entity) != 1) throw WorkspaceRowNotFoundException(entity.id)
    }

    @Query("UPDATE workspaces SET isPinned = :isPinned WHERE id = :id")
    suspend fun setPinnedRows(id: String, isPinned: Boolean): Int

    suspend fun setPinned(id: String, isPinned: Boolean) {
        if (setPinnedRows(id, isPinned) != 1) throw WorkspaceRowNotFoundException(id)
    }

    @Query("DELETE FROM workspaces WHERE id = :id")
    suspend fun deleteRowsById(id: String): Int

    suspend fun deleteById(id: String) {
        if (deleteRowsById(id) != 1) throw WorkspaceRowNotFoundException(id)
    }

    @Query("SELECT EXISTS(SELECT 1 FROM workspaces WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    @Query("SELECT COUNT(*) FROM workspaces")
    suspend fun count(): Int
}

class WorkspaceRowNotFoundException(val workspaceId: String) :
    IllegalStateException("Workspace '$workspaceId' does not exist")
