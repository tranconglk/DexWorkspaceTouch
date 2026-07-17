package com.trancong.dexworkspacetouch.workspace.apppicker.infrastructure

import android.content.Context
import com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledApp
import com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledAppDataSource

class AndroidInstalledAppDataSource(
    private val packageManagerAdapter: PackageManagerAdapter,
) : InstalledAppDataSource {
    override fun getInstalledApps(): List<InstalledApp> =
        packageManagerAdapter.getLauncherActivities().map { activity ->
            InstalledApp(
                packageName = activity.packageName,
                activityName = activity.activityName,
                label = activity.label,
                launchable = true,
                isSystemApp = activity.isSystemApp,
            )
        }

    companion object {
        fun create(context: Context): AndroidInstalledAppDataSource =
            AndroidInstalledAppDataSource(PackageManagerAdapter(context.packageManager))
    }
}
