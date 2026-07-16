package com.trancong.dexworkspacetouch.workspace.designer.ui

import com.trancong.dexworkspacetouch.workspace.designer.model.SplitDirection

internal fun pointerToDividerRatio(
    direction: SplitDirection,
    pointerX: Float,
    pointerY: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    parentStart: Float,
    parentEnd: Float,
    minRatio: Float,
    maxRatio: Float,
): Float {
    require(canvasWidth.isFinite() && canvasWidth > 0f) { "Canvas width must be positive" }
    require(canvasHeight.isFinite() && canvasHeight > 0f) { "Canvas height must be positive" }
    require(parentStart.isFinite() && parentEnd.isFinite() && parentStart < parentEnd) {
        "Parent span must be valid"
    }
    require(parentStart >= 0f && parentEnd <= 1f) { "Parent span must be normalized" }
    require(minRatio in 0f..1f && maxRatio in 0f..1f && minRatio <= maxRatio) {
        "Ratio limits must be normalized"
    }

    val (pointerCoordinate, canvasExtent) = when (direction) {
        SplitDirection.VERTICAL -> pointerX to canvasWidth
        SplitDirection.HORIZONTAL -> pointerY to canvasHeight
    }
    val parentStartPx = parentStart * canvasExtent
    val parentEndPx = parentEnd * canvasExtent
    return ((pointerCoordinate - parentStartPx) / (parentEndPx - parentStartPx))
        .coerceIn(minRatio, maxRatio)
}
