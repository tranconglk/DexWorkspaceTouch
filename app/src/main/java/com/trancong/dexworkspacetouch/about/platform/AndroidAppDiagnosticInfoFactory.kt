package com.trancong.dexworkspacetouch.about.platform

import android.app.Activity
import android.os.Build
import android.view.Display
import com.trancong.dexworkspacetouch.BuildConfig
import com.trancong.dexworkspacetouch.R
import com.trancong.dexworkspacetouch.about.presentation.AppDiagnosticInfo
import com.trancong.dexworkspacetouch.about.presentation.ReleaseFormatVersions
import com.trancong.dexworkspacetouch.about.presentation.displayModeFor

fun createAppDiagnosticInfo(activity: Activity): AppDiagnosticInfo {
    val displayId = activity.currentDisplayId()
    return AppDiagnosticInfo(
        appName = activity.getString(R.string.app_name),
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE.toLong(),
        buildChannel = BuildConfig.BUILD_CHANNEL,
        buildCommit = BuildConfig.BUILD_COMMIT,
        buildDateUtc = BuildConfig.BUILD_DATE_UTC,
        databaseVersion = ReleaseFormatVersions.Database,
        workspaceTransferVersion = ReleaseFormatVersions.WorkspaceTransfer,
        libraryBundleVersion = ReleaseFormatVersions.LibraryBundle,
        androidSdk = Build.VERSION.SDK_INT,
        manufacturer = Build.MANUFACTURER.orEmpty(),
        model = Build.MODEL.orEmpty(),
        isDexDisplay = null,
        currentDisplayId = displayId,
        appDisplayMode = displayModeFor(displayId, Display.DEFAULT_DISPLAY),
    )
}

@Suppress("DEPRECATION")
private fun Activity.currentDisplayId(): Int? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display?.displayId
    else windowManager.defaultDisplay?.displayId
