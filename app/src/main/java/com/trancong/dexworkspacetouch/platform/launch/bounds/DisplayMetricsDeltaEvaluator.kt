package com.trancong.dexworkspacetouch.platform.launch.bounds

sealed interface DisplayMetricsDeltaEvaluation {
    data class Available(val candidate: WorkAreaInsetCandidate) : DisplayMetricsDeltaEvaluation

    data object NoDelta : DisplayMetricsDeltaEvaluation

    data object HostWindowSized : DisplayMetricsDeltaEvaluation

    data object Invalid : DisplayMetricsDeltaEvaluation
}

class DisplayMetricsDeltaEvaluator(
    private val hostSizeToleranceRatio: Float = DEFAULT_HOST_SIZE_TOLERANCE_RATIO,
) {
    init {
        require(hostSizeToleranceRatio in 0f..MAX_HOST_SIZE_TOLERANCE_RATIO)
    }

    fun evaluate(
        realDisplayBounds: DiagnosticPixelBounds,
        metricsWidthPx: Int,
        metricsHeightPx: Int,
        hostWindowBounds: DiagnosticPixelBounds,
        hostWindowMode: HostWindowMode,
    ): DisplayMetricsDeltaEvaluation {
        val realWidth = realDisplayBounds.width
        val realHeight = realDisplayBounds.height
        if (
            realDisplayBounds.left != 0 || realDisplayBounds.top != 0 ||
            realWidth <= 0 || realHeight <= 0 ||
            metricsWidthPx <= 0 || metricsHeightPx <= 0 ||
            metricsWidthPx > realWidth || metricsHeightPx > realHeight
        ) {
            return DisplayMetricsDeltaEvaluation.Invalid
        }

        if (
            hostWindowMode == HostWindowMode.WINDOWED &&
            approximatelyEqual(metricsWidthPx, hostWindowBounds.width, realWidth) &&
            approximatelyEqual(metricsHeightPx, hostWindowBounds.height, realHeight) &&
            hostIsSignificantlySmaller(hostWindowBounds, realDisplayBounds)
        ) {
            return DisplayMetricsDeltaEvaluation.HostWindowSized
        }

        val right = realWidth - metricsWidthPx
        val bottom = realHeight - metricsHeightPx
        if (right == 0 && bottom == 0) return DisplayMetricsDeltaEvaluation.NoDelta
        if (right > realWidth / 2 || bottom > realHeight / 2) {
            return DisplayMetricsDeltaEvaluation.Invalid
        }

        val candidate = WorkAreaInsetCandidate(
            label = DISPLAY_METRICS_LABEL,
            source = WorkAreaInsetSource.DISPLAY_METRICS_DELTA,
            referenceBounds = realDisplayBounds,
            insets = EdgeInsets(right = right, bottom = bottom),
            coordinateSpace = InsetCoordinateSpace.DISPLAY,
        )
        if (candidate.workAreaOrNull(realDisplayBounds) == null) {
            return DisplayMetricsDeltaEvaluation.Invalid
        }
        return DisplayMetricsDeltaEvaluation.Available(candidate)
    }

    private fun approximatelyEqual(first: Int, second: Int, reference: Int): Boolean {
        val tolerancePx = (reference * hostSizeToleranceRatio).toInt().coerceAtLeast(1)
        return kotlin.math.abs(first - second) <= tolerancePx
    }

    private fun hostIsSignificantlySmaller(
        host: DiagnosticPixelBounds,
        real: DiagnosticPixelBounds,
    ): Boolean =
        host.width < real.width * HOST_SIGNIFICANT_SIZE_RATIO ||
            host.height < real.height * HOST_SIGNIFICANT_SIZE_RATIO

    companion object {
        const val DISPLAY_METRICS_LABEL = "display.metrics.delta"
        private const val DEFAULT_HOST_SIZE_TOLERANCE_RATIO = 0.02f
        private const val MAX_HOST_SIZE_TOLERANCE_RATIO = 0.1f
        private const val HOST_SIGNIFICANT_SIZE_RATIO = 0.9f
    }
}
