package com.trancong.dexworkspacetouch.feature.embeddedwaze

import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppGeometry
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppTarget

val WAZE_EMBEDDED_TARGET = EmbeddedAppTarget(
    packageName = "com.waze",
    componentName = "com.waze.FreeMapAppActivity",
    geometry = EmbeddedAppGeometry(width = 900, height = 675, densityDpi = 320),
)
