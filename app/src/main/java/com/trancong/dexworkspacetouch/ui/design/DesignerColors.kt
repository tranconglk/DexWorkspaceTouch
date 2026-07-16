package com.trancong.dexworkspacetouch.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object DesignerColors {
    val Selection: Color
        @Composable get() = MaterialTheme.colorScheme.primary
    val Divider: Color
        @Composable get() = MaterialTheme.colorScheme.outline
    val CellBorder: Color
        @Composable get() = MaterialTheme.colorScheme.outlineVariant
    val Accent: Color
        @Composable get() = MaterialTheme.colorScheme.primary
    val WorkspaceBackground: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceVariant
    val CellBackground: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainer
    val SelectedCellBackground: Color
        @Composable get() = MaterialTheme.colorScheme.primaryContainer
    val ActionOverlayBackground: Color
        @Composable get() = MaterialTheme.colorScheme.surface
}
