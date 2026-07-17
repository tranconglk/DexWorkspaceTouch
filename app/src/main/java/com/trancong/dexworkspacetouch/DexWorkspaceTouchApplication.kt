package com.trancong.dexworkspacetouch

import android.app.Application
import androidx.room.Room
import com.trancong.dexworkspacetouch.workspace.persistence.repository.RoomWorkspaceRepository
import com.trancong.dexworkspacetouch.workspace.persistence.repository.WorkspaceRepository
import com.trancong.dexworkspacetouch.workspace.persistence.room.DexWorkspaceDatabase
import com.trancong.dexworkspacetouch.workspace.persistence.serialization.DeterministicWorkspaceCanvasJsonSerializer

class DexWorkspaceTouchApplication : Application() {
    lateinit var workspaceRepository: WorkspaceRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(
            applicationContext,
            DexWorkspaceDatabase::class.java,
            DexWorkspaceDatabase.DATABASE_NAME,
        ).build()
        workspaceRepository = RoomWorkspaceRepository(
            dao = database.workspaceDao(),
            serializer = DeterministicWorkspaceCanvasJsonSerializer(),
        )
    }
}
