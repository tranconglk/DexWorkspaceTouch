package com.trancong.dexworkspacetouch.platform.launch.bounds

data class LaunchBoundsConfig(val outerMarginPx: Int = OUTER_MARGIN_PX) {
    init {
        require(outerMarginPx >= 0) {
            "outerMarginPx must not be negative"
        }
    }

    companion object {
        const val OUTER_MARGIN_PX: Int = 8
        const val INTERNAL_GUTTER_PX: Int = 4
    }
}
