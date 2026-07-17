package com.trancong.dexworkspacetouch.workspace.launcher.presentation

import com.trancong.dexworkspacetouch.workspace.launcher.model.LaunchReadiness
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchResult

sealed interface WorkspaceLaunchUiState {
    data object Idle : WorkspaceLaunchUiState
    data class Checking(val workspaceId: String) : WorkspaceLaunchUiState
    data class Launching(val workspaceId: String, val workspaceName: String) : WorkspaceLaunchUiState
    data class Completed(
        val workspaceId: String,
        val workspaceName: String,
        val totalTargets: Int,
        val result: WorkspaceLaunchResult,
    ) : WorkspaceLaunchUiState
    data class ReadinessError(val workspaceId: String, val readiness: LaunchReadiness) : WorkspaceLaunchUiState
    data class LaunchError(
        val workspaceId: String,
        val workspaceName: String,
        val reason: LaunchEnvironmentFailure,
    ) : WorkspaceLaunchUiState
    data class Cancelled(val workspaceId: String, val workspaceName: String) : WorkspaceLaunchUiState
}

sealed interface LaunchEnvironmentCheck {
    data object Ready : LaunchEnvironmentCheck
    data class Unavailable(val reason: LaunchEnvironmentFailure) : LaunchEnvironmentCheck
}

enum class LaunchEnvironmentFailure {
    HOST_NOT_EXTERNAL,
    WORK_AREA_UNAVAILABLE,
    LEGACY_WORK_AREA_UNAVAILABLE,
}

interface WorkspaceLaunchRuntime {
    fun checkEnvironment(): LaunchEnvironmentCheck
    suspend fun launch(request: com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchRequest): WorkspaceLaunchResult
}
