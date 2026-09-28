package com.trancong.dexworkspacetouch.feature.embeddedcalculator

import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppGeometry
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppTarget

val CALCULATOR_EMBEDDED_TARGET = EmbeddedAppTarget(
    packageName = "com.sec.android.app.popupcalculator",
    componentName = "com.sec.android.app.popupcalculator.Calculator",
    geometry = EmbeddedAppGeometry(width = 900, height = 675, densityDpi = 320),
)
