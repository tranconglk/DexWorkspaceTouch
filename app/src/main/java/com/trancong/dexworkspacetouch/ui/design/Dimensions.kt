package com.trancong.dexworkspacetouch.ui.design

import androidx.compose.ui.unit.dp

object Dimensions {
    const val WorkspaceAspectRatio = 16f / 10f
    const val MinCellRatio = 0.2f
    const val MaxCellRatio = 0.8f
    const val DividerSnapThreshold = 0.03f
    val DividerSnapPoints = listOf(0.25f, 1f / 3f, 0.5f, 2f / 3f, 0.75f)

    val WorkspaceBorderWidth = 2.dp
    val CellBorderWidth = 1.dp
    val SelectionBorderWidth = 3.dp
    val DividerLineWidth = 3.dp
    val DividerDragHandleLength = 48.dp
    val DividerDragHandleThickness = 8.dp
    val DividerSnapGuideLength = 32.dp
    val DividerSnapGuideThickness = 3.dp
    val AppIconSize = 32.dp
    val SnapshotLabelMinWidth = 96.dp
    val SnapshotLabelMinHeight = 56.dp
    val WorkspaceCardMinWidth = 280.dp
    val WorkspaceCardMinHeight = 336.dp
    val WorkspaceCardWideActionsWidth = 420.dp
    val AppRowMinHeight = 72.dp
    val SelectedCellContentBottomPadding = 72.dp
    val LargeCellWidth = 600.dp
    val LargeCellHeight = 120.dp
    val MediumCellWidth = 280.dp
    val MediumCellHeight = 72.dp
}
