package com.trancong.dexworkspacetouch.platform.launch.bounds

data class PixelBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(left >= 0) { "left must not be negative" }
        require(top >= 0) { "top must not be negative" }
        require(right > left) { "right must be greater than left" }
        require(bottom > top) { "bottom must be greater than top" }
    }

    val width: Int get() = right - left
    val height: Int get() = bottom - top
}
