package com.trancong.dexworkspacetouch.platform.launch.android

import android.app.Activity
import android.os.Build
import android.view.Display

interface ForegroundLaunchHost {
    fun activityOrNull(): Activity?
}

class ActivityForegroundLaunchHost(
    private val activity: Activity,
) : ForegroundLaunchHost {
    override fun activityOrNull(): Activity? {
        if (activity.isFinishing || activity.isDestroyed) return null
        if (!activity.window.decorView.isAttachedToWindow) return null
        val displayId = activity.externalDisplayIdOrNull() ?: return null
        return activity.takeIf { displayId != Display.DEFAULT_DISPLAY }
    }
}

@Suppress("DEPRECATION")
internal fun Activity.externalDisplayIdOrNull(): Int? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display?.displayId
    } else {
        windowManager.defaultDisplay.displayId
    }
