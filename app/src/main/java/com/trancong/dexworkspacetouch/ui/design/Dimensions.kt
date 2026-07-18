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
    val WorkspacePinIconSize = 24.dp
    val AppPickerIconSize = 64.dp
    val AppPickerItemMinWidth = 280.dp
    val AppPickerItemMinHeight = 168.dp
    val SnapshotLabelMinWidth = 96.dp
    val SnapshotLabelMinHeight = 56.dp
    val WorkspaceCardMinWidth = 280.dp
    val TemplateCardMinWidth = 260.dp
    const val TemplateDialogWidthFraction = 0.88f
    val TemplateDialogMaxWidth = 1440.dp
    const val TemplateDialogMaxHeightFraction = 0.84f
    val TemplateDialogHeaderHeight = 72.dp
    val TemplateSectionHeaderMinHeight = 56.dp
    val TemplateCardTitleMinHeight = 48.dp
    val TemplateGridMediumBreakpoint = 620.dp
    val TemplateGridLargeBreakpoint = 1000.dp
    val WorkspaceCardMinHeight = 336.dp
    val WorkspaceCardWideActionsWidth = 420.dp
    val WorkspaceLibraryToolbarWideWidth = 720.dp
    val WorkspaceSearchMinHeight = 56.dp
    val WorkspaceSortButtonMinWidth = 180.dp
    val AppRowMinHeight = 72.dp
    val SelectedCellContentBottomPadding = 72.dp
    val LargeCellWidth = 600.dp
    val LargeCellHeight = 120.dp
    val MediumCellWidth = 280.dp
    val MediumCellHeight = 72.dp
}
