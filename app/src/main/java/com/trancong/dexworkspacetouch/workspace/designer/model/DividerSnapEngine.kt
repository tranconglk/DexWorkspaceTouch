package com.trancong.dexworkspacetouch.workspace.designer.model

import kotlin.math.abs

data class SnapResult(
    val ratio: Float,
    val snapped: Boolean,
    val snapPoint: Float?,
)

fun snapRatio(
    rawRatio: Float,
    snapPoints: List<Float>,
    threshold: Float,
): SnapResult {
    require(rawRatio.isFinite()) { "rawRatio must be finite" }
    require(threshold.isFinite() && threshold > 0f) { "threshold must be greater than zero" }
    require(snapPoints.all { it.isFinite() && it in 0f..1f }) {
        "snapPoints must be finite and normalized"
    }

    val nearest = snapPoints
        .distinct()
        .minWithOrNull(compareBy<Float>({ abs(rawRatio - it) }, { it }))
    val snapPoint = nearest?.takeIf { abs(rawRatio - it) <= threshold }
    return SnapResult(
        ratio = (snapPoint ?: rawRatio).coerceIn(MIN_RATIO, MAX_RATIO),
        snapped = snapPoint != null,
        snapPoint = snapPoint,
    )
}

private const val MIN_RATIO = 0.2f
private const val MAX_RATIO = 0.8f
