package com.trancong.dexworkspacetouch.ui.design

import androidx.compose.ui.unit.dp

object Dimensions {
    val GridContentMaxWidth = 1440.dp
    val FormContentMaxWidth = 960.dp
    val ActivationContentMaxWidth = 520.dp
    val UpdateContentMaxWidth = 720.dp
    val BorderDefault = 1.dp
    val BorderSelected = 2.dp
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
    val WorkspaceSnapshotIconMinSize = 20.dp
    val WorkspaceSnapshotIconMediumSize = 28.dp
    val WorkspaceSnapshotIconLargeSize = 36.dp
    val WorkspaceCardMetadataMinHeight = 20.dp
    val WorkspaceCardUpdatedTextMinHeight = 20.dp
    val WorkspacePinIconSize = 24.dp
    val AppPickerIconSize = 52.dp
    val AppPickerItemMinWidth = 220.dp
    val AppPickerItemMinHeight = 120.dp
    val AppPickerControlsWideWidth = 760.dp
    val WorkspaceDesignerContentMaxWidth = 1600.dp
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
    val WorkspaceCardMinHeight = 312.dp
    val WorkspaceCardWideActionsWidth = 420.dp
    val WorkspaceLibraryToolbarWideWidth = 720.dp
    val WorkspaceDesignerToolbarWideWidth = 1100.dp
    val DesignerStatusMinHeight = 32.dp
    val WorkspaceSearchMinHeight = 56.dp
    val WorkspaceSortButtonMinWidth = 180.dp
    val MultiSelectToolbarMinHeight = 64.dp
    val MultiSelectCheckContainerSize = 40.dp
    const val MultiSelectSelectedContainerAlpha = 0.12f
    val AppRowMinHeight = 72.dp
    val SelectedCellContentBottomPadding = 72.dp
    val LargeCellWidth = 600.dp
    val LargeCellHeight = 120.dp
    val MediumCellWidth = 280.dp
    val MediumCellHeight = 72.dp
    val AboutDialogMaxWidth = 720.dp
    val AboutInfoRowMinHeight = 48.dp
    val AboutSectionSpacing = 24.dp
    val AboutWideLayoutBreakpoint = 600.dp
}
