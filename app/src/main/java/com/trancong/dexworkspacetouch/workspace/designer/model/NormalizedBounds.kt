package com.trancong.dexworkspacetouch.workspace.designer.model

data class NormalizedBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left in 0f..1f) { "left must be in 0f..1f" }
        require(top in 0f..1f) { "top must be in 0f..1f" }
        require(right in 0f..1f) { "right must be in 0f..1f" }
        require(bottom in 0f..1f) { "bottom must be in 0f..1f" }
        require(right > left) { "right must be greater than left" }
        require(bottom > top) { "bottom must be greater than top" }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top

    companion object {
        val FullCanvas = NormalizedBounds(left = 0f, top = 0f, right = 1f, bottom = 1f)
    }
}
