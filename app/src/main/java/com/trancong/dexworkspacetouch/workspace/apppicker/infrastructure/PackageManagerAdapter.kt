package com.trancong.dexworkspacetouch.workspace.apppicker.infrastructure

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build

class PackageManagerAdapter(
    private val packageManager: PackageManager,
) {
    fun getLauncherActivities(): List<LauncherActivityRecord> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        }
        return activities.mapNotNull { resolveInfo ->
            val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
            val packageName = activityInfo.packageName?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val activityName = activityInfo.name?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val applicationFlags = activityInfo.applicationInfo?.flags ?: 0
            LauncherActivityRecord(
                packageName = packageName,
                activityName = activityName,
                label = resolveInfo.loadLabel(packageManager).toString().ifBlank { packageName },
                isSystemApp = applicationFlags and SYSTEM_APP_FLAGS != 0,
            )
        }
    }

    private companion object {
        const val SYSTEM_APP_FLAGS =
            ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
    }
}

data class LauncherActivityRecord(
    val packageName: String,
    val activityName: String,
    val label: String,
    val isSystemApp: Boolean,
)
