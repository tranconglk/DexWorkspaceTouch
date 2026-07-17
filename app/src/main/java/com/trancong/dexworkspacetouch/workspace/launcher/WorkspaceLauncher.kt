package com.trancong.dexworkspacetouch.workspace.launcher

import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchRequest
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchResult

interface WorkspaceLauncher {
    suspend fun launch(request: WorkspaceLaunchRequest): WorkspaceLaunchResult
}
