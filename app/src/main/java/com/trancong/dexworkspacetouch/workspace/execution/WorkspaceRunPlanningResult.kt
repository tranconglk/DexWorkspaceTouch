package com.trancong.dexworkspacetouch.workspace.execution

import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlan
import com.trancong.dexworkspacetouch.workspace.launcher.model.LaunchReadiness
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchRequest

sealed interface WorkspaceRunPlanningResult {
    data class Classic(val request: WorkspaceLaunchRequest) : WorkspaceRunPlanningResult
    data class Embedded(val plan: EmbeddedWorkspacePlan) : WorkspaceRunPlanningResult
    data class Rejected(val readiness: LaunchReadiness) : WorkspaceRunPlanningResult
}
