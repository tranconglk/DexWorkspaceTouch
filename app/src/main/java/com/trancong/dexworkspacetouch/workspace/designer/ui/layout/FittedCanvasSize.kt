package com.trancong.dexworkspacetouch.workspace.designer.ui.layout

data class FittedCanvasSize(
    val width: Float,
    val height: Float,
)

fun fitSize(
    availableWidth: Float,
    availableHeight: Float,
    aspectRatio: Float,
): FittedCanvasSize {
    require(availableWidth.isFinite() && availableWidth > 0f) {
        "Available width must be finite and greater than zero"
    }
    require(availableHeight.isFinite() && availableHeight > 0f) {
        "Available height must be finite and greater than zero"
    }
    require(aspectRatio.isFinite() && aspectRatio > 0f) {
        "Aspect ratio must be finite and greater than zero"
    }

    return if (availableWidth / availableHeight > aspectRatio) {
        FittedCanvasSize(
            width = availableHeight * aspectRatio,
            height = availableHeight,
        )
    } else {
        FittedCanvasSize(
            width = availableWidth,
            height = availableWidth / aspectRatio,
        )
    }
}
