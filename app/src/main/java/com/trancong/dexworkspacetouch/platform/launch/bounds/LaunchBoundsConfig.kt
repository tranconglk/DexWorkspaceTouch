package com.trancong.dexworkspacetouch.platform.launch.bounds

data class LaunchBoundsConfig(val marginDp: Float = DEFAULT_MARGIN_DP) {
    init {
        require(marginDp.isFinite() && marginDp >= 0f) {
            "marginDp must be finite and non-negative"
        }
    }

    companion object {
        const val DEFAULT_MARGIN_DP: Float = 8f
    }
}
