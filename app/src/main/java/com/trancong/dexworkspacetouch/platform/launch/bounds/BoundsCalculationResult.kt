package com.trancong.dexworkspacetouch.platform.launch.bounds

sealed interface BoundsCalculationResult {
    data class Success(val bounds: PixelBounds) : BoundsCalculationResult

    data class Failure(val reason: BoundsCalculationFailureReason) : BoundsCalculationResult
}

enum class BoundsCalculationFailureReason {
    INSUFFICIENT_SPACE,
}
