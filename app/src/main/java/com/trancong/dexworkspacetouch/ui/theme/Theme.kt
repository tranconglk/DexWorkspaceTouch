package com.trancong.dexworkspacetouch.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.trancong.dexworkspacetouch.ui.design.DwtShapes

val DwtDarkColorScheme = darkColorScheme(
    primary = DwtPrimary,
    onPrimary = DwtBackground,
    primaryContainer = DwtSurfaceAlt,
    onPrimaryContainer = DwtOnBackground,
    secondary = DwtSecondary,
    onSecondary = DwtBackground,
    secondaryContainer = DwtSurfaceAlt,
    onSecondaryContainer = DwtOnBackground,
    tertiary = DwtAccent,
    onTertiary = DwtBackground,
    background = DwtBackground,
    onBackground = DwtOnBackground,
    surface = DwtSurface,
    onSurface = DwtOnBackground,
    surfaceVariant = DwtSurfaceAlt,
    onSurfaceVariant = DwtOnSurfaceMuted,
    outline = DwtOutline,
    outlineVariant = DwtOutline,
    error = DwtError,
    onError = DwtBackground,
)

@Composable
fun DexWorkspaceTouchTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DwtDarkColorScheme,
        typography = AppTypography,
        shapes = DwtShapes,
        content = content,
    )
}
