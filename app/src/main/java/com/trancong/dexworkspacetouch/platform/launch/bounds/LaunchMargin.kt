package com.trancong.dexworkspacetouch.platform.launch.bounds

fun launchMarginPx(
    density: Float,
    config: LaunchBoundsConfig = LaunchBoundsConfig(),
): Int {
    require(density.isFinite() && density > 0f) { "density must be finite and positive" }
    // Workspace spacing is specified in physical pixels, independent of display density.
    return config.outerMarginPx
}
