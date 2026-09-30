package com.trancong.dexworkspacetouch.feature.embeddedworkspace

import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlan
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlanner
import com.trancong.dexworkspacetouch.workspace.launcher.WorkspaceLaunchRequestFactory
import com.trancong.dexworkspacetouch.workspace.launcher.model.LaunchReadiness
import com.trancong.dexworkspacetouch.workspace.persistence.repository.WorkspaceRepository

sealed interface EmbeddedWorkspaceLayoutLoadResult {
    data class Ready(val plan: EmbeddedWorkspacePlan) : EmbeddedWorkspaceLayoutLoadResult
    data class MissingWorkspace(val workspaceId: String) : EmbeddedWorkspaceLayoutLoadResult
    data class LaunchNotReady(val reason: LaunchReadiness) : EmbeddedWorkspaceLayoutLoadResult
}

class EmbeddedWorkspaceLayoutLoader(
    private val repository: WorkspaceRepository,
    private val requestFactory: WorkspaceLaunchRequestFactory,
    private val planner: EmbeddedWorkspacePlanner = EmbeddedWorkspacePlanner(),
) {
    suspend fun load(workspaceId: String): EmbeddedWorkspaceLayoutLoadResult {
        val workspace = repository.getById(workspaceId)
            ?: return EmbeddedWorkspaceLayoutLoadResult.MissingWorkspace(workspaceId)
        return when (val readiness = requestFactory.create(workspace.id, workspace.name, workspace.canvas)) {
            is LaunchReadiness.Ready -> EmbeddedWorkspaceLayoutLoadResult.Ready(planner.plan(readiness.request))
            else -> EmbeddedWorkspaceLayoutLoadResult.LaunchNotReady(readiness)
        }
    }
}
