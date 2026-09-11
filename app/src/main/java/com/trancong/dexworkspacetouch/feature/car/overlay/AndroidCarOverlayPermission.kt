package com.trancong.dexworkspacetouch.feature.car.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

fun createCarOverlayPermissionIntent(context: Context): Intent =
    Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )
