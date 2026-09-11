package com.trancong.dexworkspacetouch.ui.design

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.trancong.dexworkspacetouch.ui.theme.AppTypography
import com.trancong.dexworkspacetouch.ui.theme.DwtBackground
import com.trancong.dexworkspacetouch.ui.theme.DwtDarkColorScheme
import com.trancong.dexworkspacetouch.ui.theme.DwtPrimary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DwtDesignSystemTest {
    @Test
    fun `production color scheme is deterministic dark`() {
        assertEquals(DwtBackground, DwtDarkColorScheme.background)
        assertEquals(DwtPrimary, DwtDarkColorScheme.primary)
        assertTrue(DwtDarkColorScheme.background.luminance() < 0.01f)
    }

    @Test
    fun `typography exposes product hierarchy`() {
        assertEquals(32f, AppTypography.titleLarge.fontSize.value, 0f)
        assertEquals(22f, AppTypography.titleMedium.fontSize.value, 0f)
        assertEquals(16f, AppTypography.bodyLarge.fontSize.value, 0f)
        assertEquals(14f, AppTypography.labelLarge.fontSize.value, 0f)
        assertEquals(13f, AppTypography.bodySmall.fontSize.value, 0f)
    }

    @Test
    fun `view bridge matches compose brand colors`() {
        assertEquals(DwtBackground.toArgb(), DwtViewColors.Background)
        assertEquals(DwtPrimary.toArgb(), DwtViewColors.Primary)
    }

    @Test
    fun `motion and touch contracts remain car friendly`() {
        assertEquals(150, DwtMotion.FastMillis)
        assertEquals(200, DwtMotion.NormalMillis)
        assertTrue(TouchTargets.CarPrimary >= TouchTargets.MinimumInteractive)
    }
}
