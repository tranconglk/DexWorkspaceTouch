package com.trancong.dexworkspacetouch.platform.launch.android

import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTarget
import com.trancong.dexworkspacetouch.workspace.launcher.diagnostics.WorkspaceLaunchDiagnosticContext

fun interface SingleAppLauncher {
    suspend fun launch(target: AppLaunchTarget): SingleAppLaunchResult

    suspend fun launch(
        target: AppLaunchTarget,
        diagnosticContext: WorkspaceLaunchDiagnosticContext?,
    ): SingleAppLaunchResult = launch(target)
}
