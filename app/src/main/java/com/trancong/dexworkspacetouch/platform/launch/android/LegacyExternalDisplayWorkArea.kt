package com.trancong.dexworkspacetouch.platform.launch.android

import android.content.Context
import android.graphics.Point
import android.view.Display
import android.view.Surface

internal data class LegacyExternalDisplayWorkArea(
    val widthPx: Int,
    val heightPx: Int,
    val bottomInsetPx: Int,
)

/**
 * Samsung DeX on Android 10 may compatibility-scale DisplayMetrics for an app process.
 * Display.Mode remains in the external display's actual coordinate space, which is also
 * the coordinate space expected by ActivityOptions launch bounds and overlay windows.
 */
internal fun legacyExternalDisplayWorkArea(
    display: Display,
    displayContext: Context,
): LegacyExternalDisplayWorkArea? {
    val mode = display.mode
    val rotated = display.rotation == Surface.ROTATION_90 ||
        display.rotation == Surface.ROTATION_270
    val width = if (rotated) mode.physicalHeight else mode.physicalWidth
    val height = if (rotated) mode.physicalWidth else mode.physicalHeight
    if (width <= 0 || height <= 0) return null

    @Suppress("DEPRECATION")
    val applicationSize = Point().also(display::getSize)
    val sameCoordinateSpace = legacyMetricsShareCoordinateSpace(
        applicationWidthPx = applicationSize.x,
        physicalWidthPx = width,
    )
    val reportedBottomInset = if (sameCoordinateSpace) {
        (height - applicationSize.y).coerceAtLeast(0)
    } else {
        0
    }
    val resourceBottomInset = listOf("navigation_bar_height_landscape", "navigation_bar_height")
        .firstNotNullOfOrNull { name ->
            val id = displayContext.resources.getIdentifier(name, "dimen", "android")
            id.takeIf { it != 0 }?.let(displayContext.resources::getDimensionPixelSize)
        }
        ?: 0
    val bottomInset = maxOf(reportedBottomInset, resourceBottomInset)
        .coerceAtMost(height / 4)
    return LegacyExternalDisplayWorkArea(width, height, bottomInset)
}

internal fun legacyMetricsShareCoordinateSpace(
    applicationWidthPx: Int,
    physicalWidthPx: Int,
): Boolean = physicalWidthPx > 0 &&
    applicationWidthPx in (physicalWidthPx * 3 / 4)..(physicalWidthPx * 5 / 4)
