package com.trancong.dexworkspacetouch.workspace.apppicker.infrastructure

import android.content.Intent
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.drawable.Drawable
import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity

class PackageManagerAdapter(
    private val packageManager: PackageManager,
) {
    fun verifyPackageExists(packageName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
    }

    fun verifyActivityExists(identity: AppIdentity) {
        val activityName = requireNotNull(identity.activityName) {
            "activityName is required for launch verification"
        }
        val component = ComponentName(identity.packageName, activityName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getActivityInfo(component, 0)
        }
    }

    fun canResolveLauncherActivity(identity: AppIdentity): Boolean {
        val activityName = identity.activityName ?: return false
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(identity.packageName, activityName)
        }
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(intent, PackageManager.MATCH_ALL)
        } ?: return false
        return resolved.activityInfo?.let {
            it.packageName == identity.packageName && it.name == activityName
        } == true
    }

    fun loadIcon(identity: AppIdentity): Drawable {
        if (identity.activityName != null) {
            runCatching {
                packageManager.getActivityIcon(ComponentName(identity.packageName, identity.activityName))
            }.getOrNull()?.let { return it }
        }
        return packageManager.getApplicationIcon(identity.packageName)
    }

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
