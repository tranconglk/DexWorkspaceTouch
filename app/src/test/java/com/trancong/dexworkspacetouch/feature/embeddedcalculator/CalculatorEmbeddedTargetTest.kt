package com.trancong.dexworkspacetouch.feature.embeddedcalculator

import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorEmbeddedTargetTest {
    @Test
    fun usesResolvedSamsungCalculatorTargetAndControlGeometry() {
        assertEquals(
            "com.sec.android.app.popupcalculator",
            CALCULATOR_EMBEDDED_TARGET.packageName,
        )
        assertEquals(
            "com.sec.android.app.popupcalculator.Calculator",
            CALCULATOR_EMBEDDED_TARGET.componentName,
        )
        assertEquals(
            EmbeddedAppGeometry(900, 675, 320),
            CALCULATOR_EMBEDDED_TARGET.geometry,
        )
    }
}
