package com.trancong.dexworkspacetouch.feature.car.platform

import com.trancong.dexworkspacetouch.workspace.launcher.WorkspaceLaunchRequestFactory
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchFailureReason
import com.trancong.dexworkspacetouch.workspace.launcher.model.LaunchReadiness
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchResult
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.LaunchEnvironmentCheck
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.WorkspaceLaunchRuntime
import com.trancong.dexworkspacetouch.workspace.persistence.repository.WorkspaceRepository
import kotlinx.coroutines.CancellationException

fun interface CarWorkspaceLaunchPlatform {
    suspend fun launch(workspaceId: String): CarWorkspaceLaunchResult
}

sealed interface CarWorkspaceLaunchResult {
    data object Success : CarWorkspaceLaunchResult
    data object WorkspaceUnavailable : CarWorkspaceLaunchResult
    data class ExecutionFailed(val message: String? = null) : CarWorkspaceLaunchResult
}

class RepositoryCarWorkspaceLaunchPlatform(
    private val repository: WorkspaceRepository,
    private val requestFactory: WorkspaceLaunchRequestFactory,
    private val runtime: WorkspaceLaunchRuntime,
) : CarWorkspaceLaunchPlatform {
    override suspend fun launch(workspaceId: String): CarWorkspaceLaunchResult {
        return try {
            val workspace = repository.getById(workspaceId)
                ?: return CarWorkspaceLaunchResult.WorkspaceUnavailable
            val readiness = requestFactory.create(
                workspaceId = workspace.id,
                workspaceName = workspace.name,
                canvas = workspace.canvas,
            )
            val request = when (readiness) {
                is LaunchReadiness.Ready -> readiness.request
                LaunchReadiness.EmptyWorkspace,
                is LaunchReadiness.EmptyCells,
                is LaunchReadiness.MissingApplications,
                is LaunchReadiness.NonLaunchableApplications -> {
                    return CarWorkspaceLaunchResult.WorkspaceUnavailable
                }
                is LaunchReadiness.InvalidCanvas,
                is LaunchReadiness.TooManyTargets -> {
                    return CarWorkspaceLaunchResult.ExecutionFailed(
                        "Workspace launch request is invalid.",
                    )
                }
            }
            when (runtime.checkEnvironment()) {
                LaunchEnvironmentCheck.Ready -> Unit
                is LaunchEnvironmentCheck.Unavailable -> {
                    return CarWorkspaceLaunchResult.ExecutionFailed(
                        "Workspace launch environment is unavailable.",
                    )
                }
            }
            runtime.launch(request).toCarResult()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            CarWorkspaceLaunchResult.ExecutionFailed(exception.message)
        }
    }

    private fun WorkspaceLaunchResult.toCarResult(): CarWorkspaceLaunchResult = when (this) {
        is WorkspaceLaunchResult.Success -> CarWorkspaceLaunchResult.Success
        is WorkspaceLaunchResult.PartialSuccess -> CarWorkspaceLaunchResult.ExecutionFailed(
            "Workspace launch completed with ${failedTargets.size} failed target(s).",
        )
        is WorkspaceLaunchResult.Failure -> {
            if (failures.all { failure -> failure.reason.isApplicationUnavailable() }) {
                CarWorkspaceLaunchResult.WorkspaceUnavailable
            } else {
                CarWorkspaceLaunchResult.ExecutionFailed("Workspace launch failed.")
            }
        }
    }

    private fun AppLaunchFailureReason.isApplicationUnavailable(): Boolean =
        this == AppLaunchFailureReason.APP_NOT_FOUND ||
            this == AppLaunchFailureReason.ACTIVITY_NOT_FOUND
}
