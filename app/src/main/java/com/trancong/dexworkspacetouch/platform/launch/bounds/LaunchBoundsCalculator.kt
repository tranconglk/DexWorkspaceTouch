package com.trancong.dexworkspacetouch.platform.launch.bounds

import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import kotlin.math.roundToInt

class LaunchBoundsCalculator(private val marginPx: Int) {
    init {
        require(marginPx >= 0) { "marginPx must not be negative" }
    }

    /**
     * Maps normalized edges into the usable display rectangle using [Float.roundToInt],
     * applies [marginPx] inward on every edge, then clamps to that rectangle.
     */
    fun calculate(
        normalizedBounds: NormalizedBounds,
        workArea: DisplayWorkArea,
    ): BoundsCalculationResult = trace(normalizedBounds, workArea).result

    fun trace(
        normalizedBounds: NormalizedBounds,
        workArea: DisplayWorkArea,
    ): LaunchBoundsTrace {
        val left = (
            workArea.originX + normalizedBounds.left * workArea.usableWidth
            ).roundToInt() + marginPx
        val top = (
            workArea.originY + normalizedBounds.top * workArea.usableHeight
            ).roundToInt() + marginPx
        val right = (
            workArea.originX + normalizedBounds.right * workArea.usableWidth
            ).roundToInt() - marginPx
        val bottom = (
            workArea.originY + normalizedBounds.bottom * workArea.usableHeight
            ).roundToInt() - marginPx

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
