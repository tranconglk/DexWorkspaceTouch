package com.trancong.dexworkspacetouch.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trancong.dexworkspacetouch.ui.design.DesignerElevation
import com.trancong.dexworkspacetouch.ui.design.Dimensions
import com.trancong.dexworkspacetouch.ui.design.InteractionZones
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets
import com.trancong.dexworkspacetouch.workspace.designer.state.WorkspaceDesignerViewModel
import com.trancong.dexworkspacetouch.workspace.designer.state.SplitResult
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceLimits
import com.trancong.dexworkspacetouch.workspace.designer.ui.WorkspaceCanvasView
import com.trancong.dexworkspacetouch.workspace.designer.ui.layout.fitSize
import kotlinx.coroutines.launch

@Composable
fun LayoutDesignerScreen(
    state: WorkspaceDesignerViewModel,
    isNewWorkspace: Boolean,
    onBack: () -> Unit,
    onOpenAppPicker: (String) -> Unit,
    onSave: (String?) -> Unit,
) {
    var showNameDialog by rememberSaveable { mutableStateOf(false) }
    var workspaceName by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = androidx.compose.runtime.remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Tên workspace") },
            text = {
                TextField(
                    value = workspaceName,
                    onValueChange = { workspaceName = it },
                    label = { Text("Để trống để dùng tên mặc định") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showNameDialog = false
                    onSave(workspaceName)
                }, modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton)) { Text("Lưu") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNameDialog = false },
                    modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
                ) { Text("Hủy") }
            },
        )
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
        ),
        bottomBar = {
            Surface(
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                ),
                tonalElevation = DesignerElevation.BottomBar,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = Spacing.L,
                        vertical = InteractionZones.DeadZone,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(InteractionZones.DeadZone),
                ) {
                    OutlinedButton(
                        onClick = {},
                        modifier = Modifier.weight(1f).height(TouchTargets.PrimaryButton),
                    ) { Text("Mở") }
                    Button(
                        onClick = {
                            if (isNewWorkspace) showNameDialog = true else onSave(null)
                        },
                        modifier = Modifier.weight(1f).height(TouchTargets.PrimaryButton),
                    ) { Text("Lưu") }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.L),
        ) {
            TextButton(onClick = onBack) { Text("Quay lại") }
            Text("Thiết kế workspace")
            DesignerToolbar(state)
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = InteractionZones.DeadZone),
                contentAlignment = Alignment.Center,
            ) {
                if (maxWidth > 0.dp && maxHeight > 0.dp) {
                    val fittedSize = fitSize(
                        availableWidth = maxWidth.value,
                        availableHeight = maxHeight.value,
                        aspectRatio = Dimensions.WorkspaceAspectRatio,
                    )
                    Box(
                        modifier = Modifier.size(fittedSize.width.dp, fittedSize.height.dp),
                    ) {
                        WorkspaceCanvasView(
                            canvas = state.canvas,
                            selectedCellId = state.selectedCellId,
                            selectedDividerId = state.selectedDividerId,
                            onCellActivated = { cellId ->
                                state.activateCell(cellId)?.let { activation ->
                                    onOpenAppPicker(activation.cellId)
                                }
                            },
                            onDividerSelected = state::selectDivider,
                            onDividerRatioChanged = state::resizeDivider,
                            onDividerDragStart = state::beginDividerResize,
                            onDividerDragRatio = state::updateDividerResize,
                            onDividerDragEnd = { state.commitDividerResize() },
                            onDividerDragCancel = state::cancelDividerResize,
                            onClearDividerSelection = state::clearDividerSelection,
                            onSplit = { direction ->
                                if (state.splitSelectedCell(direction) == SplitResult.MaximumCellsReached) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            "Workspace hỗ trợ tối đa ${WorkspaceLimits.MaxCells} ô.",
                                        )
                                    }
                                }
                            },
                            onClearSelection = state::clearSelection,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DesignerToolbar(state: WorkspaceDesignerViewModel) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(top = Spacing.S)) {
        val wide = maxWidth >= Dimensions.WorkspaceDesignerToolbarWideWidth
        if (wide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DesignerHistoryButtons(state, Modifier.weight(2f))
                Text(
                    text = state.summary.statusText,
                    modifier = Modifier.weight(1f).heightIn(min = Dimensions.DesignerStatusMinHeight),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                DesignerHistoryButtons(state, Modifier.fillMaxWidth())
                Text(
                    text = state.summary.statusText,
                    modifier = Modifier.heightIn(min = Dimensions.DesignerStatusMinHeight),
                )
            }
        }
    }
}

@Composable
private fun DesignerHistoryButtons(state: WorkspaceDesignerViewModel, modifier: Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        OutlinedButton(
            onClick = { state.undo() },
            enabled = state.canUndo,
            modifier = Modifier.weight(1f).heightIn(min = TouchTargets.SecondaryButton),
        ) { Text("↶ Undo") }
        OutlinedButton(
            onClick = { state.redo() },
            enabled = state.canRedo,
            modifier = Modifier.weight(1f).heightIn(min = TouchTargets.SecondaryButton),
        ) { Text("↷ Redo") }
    }
}
