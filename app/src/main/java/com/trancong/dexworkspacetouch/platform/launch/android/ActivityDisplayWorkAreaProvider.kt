package com.trancong.dexworkspacetouch.platform.launch.android

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.WindowInsets
import androidx.annotation.RequiresApi
import com.trancong.dexworkspacetouch.platform.launch.bounds.DiagnosticPixelBounds
import com.trancong.dexworkspacetouch.platform.launch.bounds.DisplayWorkArea
import com.trancong.dexworkspacetouch.platform.launch.bounds.DisplayWorkAreaProvider
import com.trancong.dexworkspacetouch.platform.launch.bounds.DisplayWorkAreaSnapshot
import com.trancong.dexworkspacetouch.platform.launch.bounds.HostWindowMode
import kotlin.math.max

class ActivityDisplayWorkAreaProvider(
    private val activity: Activity,
) : DisplayWorkAreaProvider {
    override fun getSnapshot(): DisplayWorkAreaSnapshot? {
        if (activity.isFinishing || activity.isDestroyed) return null
        val decorView = activity.window.decorView
        if (!decorView.isAttachedToWindow) return null

        val hostDisplay = currentDisplay() ?: return null
        if (hostDisplay.displayId == Display.DEFAULT_DISPLAY) return null
        val displayManager = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val activeDisplay = displayManager.getDisplay(hostDisplay.displayId) ?: return null
        if (activeDisplay.state == Display.STATE_OFF) return null

        val snapshot = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            snapshotFromWindowMetrics(activeDisplay)
        } else {
            snapshotFromLegacyDisplay(activeDisplay)
        } ?: return null

        Log.d(LOG_TAG, snapshot.diagnosticMessage())
        return snapshot
    }

    @Suppress("DEPRECATION")
    private fun currentDisplay(): Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.display
    } else {
        activity.windowManager.defaultDisplay
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun snapshotFromWindowMetrics(display: Display): DisplayWorkAreaSnapshot? {
        val maximumMetrics = activity.windowManager.maximumWindowMetrics
        val currentMetrics = activity.windowManager.currentWindowMetrics
        val displayBounds = maximumMetrics.bounds
        val hostBounds = currentMetrics.bounds
        if (displayBounds.width() <= 0 || displayBounds.height() <= 0 ||
            hostBounds.width() <= 0 || hostBounds.height() <= 0
        ) return null

        val insets = maximumMetrics.windowInsets.getInsets(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
        )
        val density = displayDensity(display) ?: return null
        val workArea = runCatching {
            DisplayWorkArea(
                widthPx = displayBounds.width(),
                heightPx = displayBounds.height(),
                insetLeftPx = insets.left,
                insetTopPx = insets.top,
                insetRightPx = insets.right,
                insetBottomPx = insets.bottom,
            )
        }.getOrNull() ?: return null

        return DisplayWorkAreaSnapshot(
            displayId = display.displayId,
            workArea = workArea,
            rawDisplayBounds = displayBounds.toDiagnosticBounds(),
            hostWindowBounds = hostBounds.toDiagnosticBounds(),
            density = density,
            hostWindowMode = if (hostBounds == displayBounds) {
                HostWindowMode.MAXIMIZED
            } else {
                HostWindowMode.WINDOWED
            },
        )
    }

    @Suppress("DEPRECATION")
    private fun snapshotFromLegacyDisplay(display: Display): DisplayWorkAreaSnapshot? {
        val rootInsets = activity.window.decorView.rootWindowInsets ?: return null
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)
        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) return null

        val cutout = rootInsets.displayCutout
        val leftInset = max(rootInsets.systemWindowInsetLeft, cutout?.safeInsetLeft ?: 0)
        val topInset = max(rootInsets.systemWindowInsetTop, cutout?.safeInsetTop ?: 0)
        val rightInset = max(rootInsets.systemWindowInsetRight, cutout?.safeInsetRight ?: 0)
        val bottomInset = max(rootInsets.systemWindowInsetBottom, cutout?.safeInsetBottom ?: 0)
        val density = displayDensity(display) ?: return null
        val workArea = runCatching {
            DisplayWorkArea(
                widthPx = metrics.widthPixels,
                heightPx = metrics.heightPixels,
                insetLeftPx = leftInset,
                insetTopPx = topInset,
                insetRightPx = rightInset,
                insetBottomPx = bottomInset,
            )
        }.getOrNull() ?: return null
        val displayBounds = DiagnosticPixelBounds(0, 0, metrics.widthPixels, metrics.heightPixels)
        val decorView = activity.window.decorView
        if (decorView.width <= 0 || decorView.height <= 0) return null
        val windowLocation = IntArray(2)
        decorView.getLocationOnScreen(windowLocation)
        val hostBounds = DiagnosticPixelBounds(
            left = windowLocation[0],
            top = windowLocation[1],
            right = windowLocation[0] + decorView.width,
            bottom = windowLocation[1] + decorView.height,
        )

        return DisplayWorkAreaSnapshot(
            displayId = display.displayId,
            workArea = workArea,
            rawDisplayBounds = displayBounds,
            hostWindowBounds = hostBounds,
            density = density,
            hostWindowMode = if (hostBounds == displayBounds) {
                HostWindowMode.MAXIMIZED
            } else {
                HostWindowMode.WINDOWED
            },
        )
    }

    private fun displayDensity(display: Display): Float? {
        val density = activity.createDisplayContext(display).resources.displayMetrics.density
        return density.takeIf { it.isFinite() && it > 0f }
    }

    private fun android.graphics.Rect.toDiagnosticBounds() = DiagnosticPixelBounds(
        left = left,
        top = top,
        right = right,
        bottom = bottom,
    )

    private companion object {
        const val LOG_TAG = "DexLaunchWorkArea"
    }
}
