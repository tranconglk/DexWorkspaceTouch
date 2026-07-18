package com.trancong.dexworkspacetouch

import android.app.Application
import androidx.room.Room
import com.trancong.dexworkspacetouch.workspace.persistence.repository.RoomWorkspaceRepository
import com.trancong.dexworkspacetouch.workspace.persistence.repository.WorkspaceRepository
import com.trancong.dexworkspacetouch.workspace.persistence.room.DexWorkspaceDatabase
import com.trancong.dexworkspacetouch.workspace.persistence.room.MIGRATION_1_2
import com.trancong.dexworkspacetouch.workspace.persistence.serialization.DeterministicWorkspaceCanvasJsonSerializer
import com.trancong.dexworkspacetouch.workspace.apppicker.infrastructure.PackageManagerAppIconLoader
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.AppIconLoader

class DexWorkspaceTouchApplication : Application() {
    lateinit var workspaceRepository: WorkspaceRepository
        private set
    lateinit var appIconLoader: AppIconLoader
        private set

    override fun onCreate() {
        super.onCreate()
        appIconLoader = PackageManagerAppIconLoader.create(applicationContext)
        val database = Room.databaseBuilder(
            applicationContext,
            DexWorkspaceDatabase::class.java,
            DexWorkspaceDatabase.DATABASE_NAME,
        ).addMigrations(MIGRATION_1_2).build()
        workspaceRepository = RoomWorkspaceRepository(
            dao = database.workspaceDao(),
            serializer = DeterministicWorkspaceCanvasJsonSerializer(),
        )
    }
}
