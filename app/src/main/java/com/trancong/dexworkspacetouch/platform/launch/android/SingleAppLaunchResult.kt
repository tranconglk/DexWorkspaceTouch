package com.trancong.dexworkspacetouch.platform.launch.android

import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchFailure
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTargetResult

sealed interface SingleAppLaunchResult {
    data class Success(val launchedTarget: AppLaunchTargetResult) : SingleAppLaunchResult

    data class Failure(val failure: AppLaunchFailure) : SingleAppLaunchResult
}
