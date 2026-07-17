package com.trancong.dexworkspacetouch.workspace.launcher.model

import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds

data class WorkspaceLaunchRequest(
    val workspaceId: String,
    val workspaceName: String,
    val targets: List<AppLaunchTarget>,
) {
    init {
        require(workspaceId.isNotBlank()) { "workspaceId must not be blank" }
        require(workspaceName.isNotBlank()) { "workspaceName must not be blank" }
        require(targets.isNotEmpty()) { "targets must not be empty" }
        require(targets.map(AppLaunchTarget::order).distinct().size == targets.size) {
            "target orders must be unique"
        }
    }
}

data class AppLaunchTarget(
    val identity: AppIdentity,
    val bounds: NormalizedBounds,
    val order: Int,
) {
    init {
        require(order >= 0) { "order must be non-negative" }
    }
}
