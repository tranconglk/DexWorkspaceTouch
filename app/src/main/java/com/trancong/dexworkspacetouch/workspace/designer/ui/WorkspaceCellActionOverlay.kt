package com.trancong.dexworkspacetouch.workspace.designer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.trancong.dexworkspacetouch.ui.design.DesignerColors
import com.trancong.dexworkspacetouch.ui.design.DesignerElevation
import com.trancong.dexworkspacetouch.ui.design.Dimensions
import com.trancong.dexworkspacetouch.ui.design.InteractionZones
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell

@Composable
fun WorkspaceCellActionOverlay(
    cell: WorkspaceCell,
    canSplitHorizontal: Boolean,
    canSplitVertical: Boolean,
    maximumCellsReached: Boolean,
    onSplitHorizontal: () -> Unit,
    onSplitVertical: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSplitChoices by remember(cell.id) { mutableStateOf(false) }
    var showActionSheet by remember(cell.id) { mutableStateOf(false) }
    val overlayInteractionSource = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier.clickable(
            interactionSource = overlayInteractionSource,
            indication = null,
            onClick = {},
        ),
        color = DesignerColors.ActionOverlayBackground.copy(alpha = 0.94f),
        tonalElevation = DesignerElevation.ActionOverlay,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(Spacing.XS)) {
            when {
                maxWidth >= Dimensions.LargeCellWidth && maxHeight >= Dimensions.LargeCellHeight -> {
                    FullActionRow(
                        canSplitHorizontal = canSplitHorizontal,
                        canSplitVertical = canSplitVertical,
                        maximumCellsReached = maximumCellsReached,
                        onSplitHorizontal = onSplitHorizontal,
                        onSplitVertical = onSplitVertical,
                        onClearSelection = onClearSelection,
                    )
                }

                maxWidth >= Dimensions.MediumCellWidth && maxHeight >= Dimensions.MediumCellHeight -> {
                    MediumActionRow(
                        showSplitChoices = showSplitChoices,
                        canSplitHorizontal = canSplitHorizontal,
                        canSplitVertical = canSplitVertical,
                        maximumCellsReached = maximumCellsReached,
                        onShowSplitChoices = { showSplitChoices = true },
                        onHideSplitChoices = { showSplitChoices = false },
                        onSplitHorizontal = onSplitHorizontal,
                        onSplitVertical = onSplitVertical,
                        onClearSelection = onClearSelection,
                    )
                }

                else -> {
                    Button(
                        onClick = { showActionSheet = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = TouchTargets.SecondaryButton),
                    ) {
                        Text("Thao tác")
                    }
                }
            }
        }
    }

    if (showActionSheet) {
        CellActionSheet(
            canSplitHorizontal = canSplitHorizontal,
            canSplitVertical = canSplitVertical,
            maximumCellsReached = maximumCellsReached,
            onDismiss = { showActionSheet = false },
            onSplitHorizontal = {
                showActionSheet = false
                onSplitHorizontal()
            },
            onSplitVertical = {
                showActionSheet = false
                onSplitVertical()
            },
            onClearSelection = {
                showActionSheet = false
                onClearSelection()
            },
        )
    }
}

@Composable
private fun FullActionRow(
    canSplitHorizontal: Boolean,
    canSplitVertical: Boolean,
    maximumCellsReached: Boolean,
    onSplitHorizontal: () -> Unit,
    onSplitVertical: () -> Unit,
    onClearSelection: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(InteractionZones.ActionPadding),
    ) {
        ActionButton("Chia ngang", canSplitHorizontal, onSplitHorizontal, disabledContentDescription = disabledDescription(maximumCellsReached))
        ActionButton("Chia dọc", canSplitVertical, onSplitVertical, disabledContentDescription = disabledDescription(maximumCellsReached))
        ActionButton("Bỏ chọn", true, onClearSelection)
    }
}

@Composable
private fun MediumActionRow(
    showSplitChoices: Boolean,
    canSplitHorizontal: Boolean,
    canSplitVertical: Boolean,
    maximumCellsReached: Boolean,
    onShowSplitChoices: () -> Unit,
    onHideSplitChoices: () -> Unit,
    onSplitHorizontal: () -> Unit,
    onSplitVertical: () -> Unit,
    onClearSelection: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(InteractionZones.ActionPadding),
    ) {
        if (showSplitChoices) {
            ActionButton("Chia ngang", canSplitHorizontal, onSplitHorizontal, disabledContentDescription = disabledDescription(maximumCellsReached))
            ActionButton("Chia dọc", canSplitVertical, onSplitVertical, disabledContentDescription = disabledDescription(maximumCellsReached))
            ActionButton("Quay lại", true, onHideSplitChoices)
        } else {
            ActionButton(
                "Chia",
                canSplitHorizontal || canSplitVertical,
                onShowSplitChoices,
                disabledContentDescription = disabledDescription(maximumCellsReached),
            )
            ActionButton("Bỏ chọn", true, onClearSelection)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CellActionSheet(
    canSplitHorizontal: Boolean,
    canSplitVertical: Boolean,
    maximumCellsReached: Boolean,
    onDismiss: () -> Unit,
    onSplitHorizontal: () -> Unit,
    onSplitVertical: () -> Unit,
    onClearSelection: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(
                start = Spacing.L,
                end = Spacing.L,
                bottom = Spacing.L,
            ),
            verticalArrangement = Arrangement.spacedBy(InteractionZones.ActionPadding),
        ) {
            Text("Thao tác ô", style = MaterialTheme.typography.titleLarge)
            ActionButton(
                "Chia ngang", canSplitHorizontal, onSplitHorizontal,
                modifier = Modifier.fillMaxWidth(),
                disabledContentDescription = disabledDescription(maximumCellsReached),
            )
            ActionButton(
                "Chia dọc", canSplitVertical, onSplitVertical,
                modifier = Modifier.fillMaxWidth(),
                disabledContentDescription = disabledDescription(maximumCellsReached),
            )
            ActionButton("Bỏ chọn", true, onClearSelection, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    disabledContentDescription: String? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = TouchTargets.SecondaryButton)
            .semantics {
                if (!enabled && disabledContentDescription != null) {
                    contentDescription = disabledContentDescription
                }
            },
    ) { Text(label) }
}

private fun disabledDescription(maximumCellsReached: Boolean): String? =
    if (maximumCellsReached) "Không thể chia thêm. Workspace đã có tối đa 5 ô." else null
