package com.trancong.dexworkspacetouch.platform.launch.bounds

data class DisplayWorkAreaSnapshot(
    val displayId: Int,
    val workArea: DisplayWorkArea,
    val rawDisplayBounds: DiagnosticPixelBounds,
    val hostWindowBounds: DiagnosticPixelBounds,
    val density: Float,
    val hostWindowMode: HostWindowMode,
) {
    init {
        require(displayId >= 0) { "displayId must not be negative" }
        require(density.isFinite() && density > 0f) { "density must be finite and positive" }
        require(rawDisplayBounds.width == workArea.widthPx) {
            "raw display width must match work area width"
        }
        require(rawDisplayBounds.height == workArea.heightPx) {
            "raw display height must match work area height"
        }
    }

    fun diagnosticMessage(): String = buildString {
        append("displayId=").append(displayId)
        append(", displayBounds=").append(rawDisplayBounds)
        append(", hostWindowBounds=").append(hostWindowBounds)
        append(", insets=[")
        append(workArea.insetLeftPx).append(',')
        append(workArea.insetTopPx).append(',')
        append(workArea.insetRightPx).append(',')
        append(workArea.insetBottomPx).append(']')
        append(", usable=").append(workArea.usableWidth).append('x').append(workArea.usableHeight)
        append(", density=").append(density)
        append(", hostWindowMode=").append(hostWindowMode)
    }
}

data class DiagnosticPixelBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(right > left) { "right must be greater than left" }
        require(bottom > top) { "bottom must be greater than top" }
    }

    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

enum class HostWindowMode {
    MAXIMIZED,
    WINDOWED,
    UNKNOWN,
}
