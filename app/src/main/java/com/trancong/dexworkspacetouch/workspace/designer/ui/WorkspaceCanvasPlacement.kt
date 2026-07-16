package com.trancong.dexworkspacetouch.workspace.designer.ui

import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import kotlin.math.roundToInt

internal data class ComposePlacement(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

internal fun NormalizedBounds.toComposePlacement(
    widthPx: Float,
    heightPx: Float,
): ComposePlacement {
    require(widthPx >= 1f && heightPx >= 1f) { "Canvas dimensions must be at least one pixel" }

    val canvasWidth = widthPx.roundToInt().coerceAtLeast(1)
    val canvasHeight = heightPx.roundToInt().coerceAtLeast(1)
    val leftPx = (left * canvasWidth).roundToInt().coerceIn(0, canvasWidth - 1)
    val topPx = (top * canvasHeight).roundToInt().coerceIn(0, canvasHeight - 1)
    val rightPx = (right * canvasWidth).roundToInt().coerceIn(leftPx + 1, canvasWidth)
    val bottomPx = (bottom * canvasHeight).roundToInt().coerceIn(topPx + 1, canvasHeight)

    return ComposePlacement(
        x = leftPx,
        y = topPx,
        width = rightPx - leftPx,
        height = bottomPx - topPx,
    )
}
