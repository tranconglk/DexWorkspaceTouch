package com.trancong.dexworkspacetouch.workspace.designer.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.unit.dp
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell

@Composable
fun WorkspaceCellActionOverlay(
    cell: WorkspaceCell,
    canSplitHorizontal: Boolean,
    canSplitVertical: Boolean,
    onChooseApp: () -> Unit,
    onSplitHorizontal: () -> Unit,
    onSplitVertical: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSplitChoices by remember(cell.id) { mutableStateOf(false) }
    var showActionSheet by remember(cell.id) { mutableStateOf(false) }
    val chooseLabel = if (cell.app == null) "Chọn ứng dụng" else "Đổi ứng dụng"

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 3.dp,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            when {
                maxWidth >= LARGE_CELL_WIDTH && maxHeight >= LARGE_CELL_HEIGHT -> {
                    FullActionRow(
                        chooseLabel = chooseLabel,
                        canSplitHorizontal = canSplitHorizontal,
                        canSplitVertical = canSplitVertical,
                        onChooseApp = onChooseApp,
                        onSplitHorizontal = onSplitHorizontal,
                        onSplitVertical = onSplitVertical,
                        onClearSelection = onClearSelection,
                    )
                }

                maxWidth >= MEDIUM_CELL_WIDTH && maxHeight >= MEDIUM_CELL_HEIGHT -> {
                    MediumActionRow(
                        chooseLabel = chooseLabel,
                        showSplitChoices = showSplitChoices,
                        canSplitHorizontal = canSplitHorizontal,
                        canSplitVertical = canSplitVertical,
                        onChooseApp = onChooseApp,
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
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    ) {
                        Text("Thao tác")
                    }
                }
            }
        }
    }

    if (showActionSheet) {
        CellActionSheet(
            chooseLabel = chooseLabel,
            canSplitHorizontal = canSplitHorizontal,
            canSplitVertical = canSplitVertical,
            onDismiss = { showActionSheet = false },
            onChooseApp = {
                showActionSheet = false
                onChooseApp()
            },
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
    chooseLabel: String,
    canSplitHorizontal: Boolean,
    canSplitVertical: Boolean,
    onChooseApp: () -> Unit,
    onSplitHorizontal: () -> Unit,
    onSplitVertical: () -> Unit,
    onClearSelection: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChooseAppButton(chooseLabel, onChooseApp)
        ActionButton("Chia ngang", canSplitHorizontal, onSplitHorizontal)
        ActionButton("Chia dọc", canSplitVertical, onSplitVertical)
        ActionButton("Bỏ chọn", true, onClearSelection)
    }
}

@Composable
private fun MediumActionRow(
    chooseLabel: String,
    showSplitChoices: Boolean,
    canSplitHorizontal: Boolean,
    canSplitVertical: Boolean,
    onChooseApp: () -> Unit,
    onShowSplitChoices: () -> Unit,
    onHideSplitChoices: () -> Unit,
    onSplitHorizontal: () -> Unit,
    onSplitVertical: () -> Unit,
    onClearSelection: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showSplitChoices) {
            ActionButton("Chia ngang", canSplitHorizontal, onSplitHorizontal)
            ActionButton("Chia dọc", canSplitVertical, onSplitVertical)
            ActionButton("Quay lại", true, onHideSplitChoices)
        } else {
            ChooseAppButton(chooseLabel.replace(" ứng dụng", " app"), onChooseApp)
            ActionButton("Chia", canSplitHorizontal || canSplitVertical, onShowSplitChoices)
            ActionButton("Bỏ chọn", true, onClearSelection)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CellActionSheet(
    chooseLabel: String,
    canSplitHorizontal: Boolean,
    canSplitVertical: Boolean,
    onDismiss: () -> Unit,
    onChooseApp: () -> Unit,
    onSplitHorizontal: () -> Unit,
    onSplitVertical: () -> Unit,
    onClearSelection: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Thao tác ô", style = MaterialTheme.typography.titleLarge)
            Button(
                onClick = onChooseApp,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(chooseLabel) }
            ActionButton("Chia ngang", canSplitHorizontal, onSplitHorizontal, Modifier.fillMaxWidth())
            ActionButton("Chia dọc", canSplitVertical, onSplitVertical, Modifier.fillMaxWidth())
            ActionButton("Bỏ chọn", true, onClearSelection, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ChooseAppButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 56.dp)
            .semantics { contentDescription = "$label cho ô đang chọn" },
    ) { Text(label) }
}

@Composable
private fun ActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
    ) { Text(label) }
}

private val LARGE_CELL_WIDTH = 600.dp
private val LARGE_CELL_HEIGHT = 120.dp
private val MEDIUM_CELL_WIDTH = 280.dp
private val MEDIUM_CELL_HEIGHT = 72.dp
