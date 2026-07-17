package com.trancong.dexworkspacetouch.platform.launch.bounds

/** Snapshot of a display and its insets at the time a launch sequence is prepared. */
data class DisplayWorkArea(
    val widthPx: Int,
    val heightPx: Int,
    val insetLeftPx: Int = 0,
    val insetTopPx: Int = 0,
    val insetRightPx: Int = 0,
    val insetBottomPx: Int = 0,
) {
    init {
        require(widthPx > 0) { "widthPx must be positive" }
        require(heightPx > 0) { "heightPx must be positive" }
        require(insetLeftPx >= 0) { "insetLeftPx must not be negative" }
        require(insetTopPx >= 0) { "insetTopPx must not be negative" }
        require(insetRightPx >= 0) { "insetRightPx must not be negative" }
        require(insetBottomPx >= 0) { "insetBottomPx must not be negative" }
        require(insetLeftPx.toLong() + insetRightPx < widthPx.toLong()) {
            "horizontal insets must leave positive usable width"
        }
        require(insetTopPx.toLong() + insetBottomPx < heightPx.toLong()) {
            "vertical insets must leave positive usable height"
        }
    }

    val originX: Int get() = insetLeftPx
    val originY: Int get() = insetTopPx
    val usableWidth: Int get() = widthPx - insetLeftPx - insetRightPx
    val usableHeight: Int get() = heightPx - insetTopPx - insetBottomPx
    val usableRight: Int get() = widthPx - insetRightPx
    val usableBottom: Int get() = heightPx - insetBottomPx
}
