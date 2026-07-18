package com.trancong.dexworkspacetouch.workspace.persistence.room

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [WorkspaceEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class DexWorkspaceDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao

    companion object {
        const val DATABASE_NAME = "dex_workspace.db"
    }
}
