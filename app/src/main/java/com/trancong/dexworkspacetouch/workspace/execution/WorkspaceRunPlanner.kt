package com.trancong.dexworkspacetouch.workspace.execution

import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlanner
import com.trancong.dexworkspacetouch.workspace.launcher.WorkspaceLaunchRequestFactory
import com.trancong.dexworkspacetouch.workspace.launcher.model.LaunchReadiness

class WorkspaceRunPlanner(
    private val requestFactory: WorkspaceLaunchRequestFactory,
    private val embeddedPlanner: EmbeddedWorkspacePlanner,
) {
    fun plan(request: WorkspaceRunRequest): WorkspaceRunPlanningResult {
        val readiness = requestFactory.create(
            workspaceId = request.workspaceId,
            workspaceName = request.workspaceName,
            canvas = request.canvas,
        )
        if (readiness !is LaunchReadiness.Ready) {
            return WorkspaceRunPlanningResult.Rejected(readiness)
        }

        return when (request.mode) {
            WorkspaceRunMode.CLASSIC -> WorkspaceRunPlanningResult.Classic(readiness.request)
            WorkspaceRunMode.EMBEDDED -> WorkspaceRunPlanningResult.Embedded(
                embeddedPlanner.plan(readiness.request),
            )
        }
    }
}
