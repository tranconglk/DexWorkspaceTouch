package com.trancong.dexworkspacetouch.platform.launch.bounds

import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import kotlin.math.roundToInt

class LaunchBoundsCalculator(
    private val marginPx: Int,
    private val internalGutterPx: Int = LaunchBoundsConfig.INTERNAL_GUTTER_PX,
) {
    init {
        require(marginPx >= 0) { "marginPx must not be negative" }
        require(internalGutterPx >= 0) { "internalGutterPx must not be negative" }
    }

    /**
     * Maps normalized edges into the usable display rectangle using [Float.roundToInt],
     * retains [marginPx] at normalized canvas edges and splits [internalGutterPx]
     * across interior edges, then clamps to that rectangle. Shared dividers use
     * the same rounded coordinate; an odd gutter assigns its extra pixel to the
     * leading edge deterministically.
     */
    fun calculate(
        normalizedBounds: NormalizedBounds,
        workArea: DisplayWorkArea,
    ): BoundsCalculationResult = trace(normalizedBounds, workArea).result

    fun trace(
        normalizedBounds: NormalizedBounds,
        workArea: DisplayWorkArea,
    ): LaunchBoundsTrace {
        val leadingInset = internalGutterPx - internalGutterPx / 2
        val trailingInset = internalGutterPx / 2
        val left = (
            workArea.originX + normalizedBounds.left * workArea.usableWidth
            ).roundToInt() + if (normalizedBounds.left == 0f) marginPx else leadingInset
        val top = (
            workArea.originY + normalizedBounds.top * workArea.usableHeight
            ).roundToInt() + if (normalizedBounds.top == 0f) marginPx else leadingInset
        val right = (
            workArea.originX + normalizedBounds.right * workArea.usableWidth
            ).roundToInt() - if (normalizedBounds.right == 1f) marginPx else trailingInset
        val bottom = (
            workArea.originY + normalizedBounds.bottom * workArea.usableHeight
            ).roundToInt() - if (normalizedBounds.bottom == 1f) marginPx else trailingInset

        val clampedLeft = left.coerceIn(workArea.originX, workArea.usableRight)
        val clampedTop = top.coerceIn(workArea.originY, workArea.usableBottom)
        val clampedRight = right.coerceIn(workArea.originX, workArea.usableRight)
        val clampedBottom = bottom.coerceIn(workArea.originY, workArea.usableBottom)

        if (clampedRight <= clampedLeft || clampedBottom <= clampedTop) {
            return LaunchBoundsTrace(
                beforeClamp = RawPixelBounds(left, top, right, bottom),
                result = BoundsCalculationResult.Failure(
                    BoundsCalculationFailureReason.INSUFFICIENT_SPACE,
                ),
            )
        }
        return LaunchBoundsTrace(
            beforeClamp = RawPixelBounds(left, top, right, bottom),
            result = BoundsCalculationResult.Success(
                PixelBounds(clampedLeft, clampedTop, clampedRight, clampedBottom),
            ),
        )
    }
}

data class LaunchBoundsTrace(
    val beforeClamp: RawPixelBounds,
    val result: BoundsCalculationResult,
)

data class RawPixelBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)
