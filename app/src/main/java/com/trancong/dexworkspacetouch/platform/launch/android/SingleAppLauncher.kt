package com.trancong.dexworkspacetouch.platform.launch.android

import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTarget

fun interface SingleAppLauncher {
    suspend fun launch(target: AppLaunchTarget): SingleAppLaunchResult
}
