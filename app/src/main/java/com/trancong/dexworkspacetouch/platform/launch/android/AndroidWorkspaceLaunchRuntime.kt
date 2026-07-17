package com.trancong.dexworkspacetouch.platform.launch.android

import android.app.Activity
import android.os.Build
import com.trancong.dexworkspacetouch.platform.launch.bounds.LegacyDisplayWorkAreaReferenceStore
import com.trancong.dexworkspacetouch.workspace.apppicker.infrastructure.PackageManagerAdapter
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchRequest
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchResult
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.LaunchEnvironmentCheck
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.LaunchEnvironmentFailure
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.WorkspaceLaunchRuntime

class AndroidWorkspaceLaunchRuntime(
    activity: Activity,
    legacyReferenceStore: LegacyDisplayWorkAreaReferenceStore,
) : WorkspaceLaunchRuntime {
    private val host = ActivityForegroundLaunchHost(activity)
    private val providerFactory = { hostActivity: Activity ->
        ActivityDisplayWorkAreaProvider(
            activity = hostActivity,
            legacyReferenceStore = legacyReferenceStore,
        )
    }
    private val provider = providerFactory(activity)
    private val launcher = AndroidWorkspaceLauncher(
        AndroidSingleAppLauncher(
            ActivitySingleAppLaunchPlatform(
                launchHost = host,
                packageManagerAdapter = PackageManagerAdapter(activity.packageManager),
                workAreaProviderFactory = providerFactory,
            ),
        ),
    )

    override fun checkEnvironment(): LaunchEnvironmentCheck {
        if (host.activityOrNull() == null) {
            return LaunchEnvironmentCheck.Unavailable(LaunchEnvironmentFailure.HOST_NOT_EXTERNAL)
        }
        if (provider.getSnapshot() == null) {
            val reason = if (Build.VERSION.SDK_INT in 28..29) {
                LaunchEnvironmentFailure.LEGACY_WORK_AREA_UNAVAILABLE
            } else {
                LaunchEnvironmentFailure.WORK_AREA_UNAVAILABLE
            }
            return LaunchEnvironmentCheck.Unavailable(reason)
        }
        return LaunchEnvironmentCheck.Ready
    }

    override suspend fun launch(request: WorkspaceLaunchRequest): WorkspaceLaunchResult =
        launcher.launch(request)
}
