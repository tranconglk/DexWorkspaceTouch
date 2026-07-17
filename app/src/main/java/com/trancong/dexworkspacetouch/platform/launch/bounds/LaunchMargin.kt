package com.trancong.dexworkspacetouch.platform.launch.bounds

import kotlin.math.roundToInt

fun launchMarginPx(
    density: Float,
    config: LaunchBoundsConfig = LaunchBoundsConfig(),
): Int {
    require(density.isFinite() && density > 0f) { "density must be finite and positive" }
    return (config.marginDp * density).roundToInt()
}
