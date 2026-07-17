package com.trancong.dexworkspacetouch.workspace.launcher.model

sealed interface WorkspaceLaunchResult {
    data class Success(
        val launchedTargets: List<AppLaunchTargetResult>,
    ) : WorkspaceLaunchResult {
        init {
            require(launchedTargets.isNotEmpty()) { "launchedTargets must not be empty" }
        }
    }

    data class PartialSuccess(
        val launchedTargets: List<AppLaunchTargetResult>,
        val failedTargets: List<AppLaunchFailure>,
    ) : WorkspaceLaunchResult {
        init {
            require(launchedTargets.isNotEmpty()) { "launchedTargets must not be empty" }
            require(failedTargets.isNotEmpty()) { "failedTargets must not be empty" }
        }
    }

    data class Failure(
        val failures: List<AppLaunchFailure>,
    ) : WorkspaceLaunchResult {
        init {
            require(failures.isNotEmpty()) { "failures must not be empty" }
        }
    }
}

data class AppLaunchTargetResult(val target: AppLaunchTarget)

data class AppLaunchFailure(
    val target: AppLaunchTarget,
    val reason: AppLaunchFailureReason,
    val technicalMessage: String? = null,
) {
    init {
        require(technicalMessage == null || technicalMessage.isNotBlank()) {
            "technicalMessage must be null or non-blank"
        }
    }
}

enum class AppLaunchFailureReason {
    APP_NOT_FOUND,
    ACTIVITY_NOT_FOUND,
    DISPLAY_UNAVAILABLE,
    SECURITY_RESTRICTION,
    LAUNCH_REJECTED,
    UNKNOWN,
}
