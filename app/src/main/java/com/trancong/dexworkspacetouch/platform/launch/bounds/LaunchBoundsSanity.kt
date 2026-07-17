package com.trancong.dexworkspacetouch.platform.launch.bounds

object LaunchBoundsSanity {
    fun isWithinWorkArea(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        workArea: DisplayWorkArea,
    ): Boolean =
        right > left &&
            bottom > top &&
            left >= workArea.originX &&
            top >= workArea.originY &&
            right <= workArea.usableRight &&
            bottom <= workArea.usableBottom

    fun isWithinWorkArea(bounds: PixelBounds, workArea: DisplayWorkArea): Boolean =
        isWithinWorkArea(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            workArea,
        )
}
