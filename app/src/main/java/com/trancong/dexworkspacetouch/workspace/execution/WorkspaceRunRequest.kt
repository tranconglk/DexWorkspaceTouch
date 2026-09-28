package com.trancong.dexworkspacetouch.workspace.execution

import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas

data class WorkspaceRunRequest(
    val workspaceId: String,
    val workspaceName: String,
    val canvas: WorkspaceCanvas,
    val mode: WorkspaceRunMode,
)
