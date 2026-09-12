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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import com.trancong.dexworkspacetouch.ui.design.DesignerElevation
import com.trancong.dexworkspacetouch.ui.design.Dimensions
import com.trancong.dexworkspacetouch.ui.design.InteractionZones
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets
import com.trancong.dexworkspacetouch.workspace.designer.state.WorkspaceDesignerViewModel
import com.trancong.dexworkspacetouch.workspace.designer.state.SplitResult
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceLimits
import com.trancong.dexworkspacetouch.workspace.designer.ui.WorkspaceCanvasView
import com.trancong.dexworkspacetouch.workspace.designer.ui.DesignerContextToolbar
import com.trancong.dexworkspacetouch.workspace.designer.ui.WorkspaceMergeConfirmationDialog
import com.trancong.dexworkspacetouch.workspace.designer.ui.WorkspaceMergeTargetDialog
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceMergeResult
import com.trancong.dexworkspacetouch.workspace.designer.model.requiresMergeConfirmation
import com.trancong.dexworkspacetouch.workspace.designer.state.DesignerContextToolbarState
import com.trancong.dexworkspacetouch.workspace.designer.ui.layout.fitSize
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.AppIconLoader
import kotlinx.coroutines.launch

@Composable
fun LayoutDesignerScreen(
    state: WorkspaceDesignerViewModel,
    isNewWorkspace: Boolean,
    appIconLoader: AppIconLoader,
    onBack: () -> Unit,
    onOpenAppPicker: (String) -> Unit,
    onSave: (String?) -> Unit,
) {
    var showNameDialog by rememberSaveable { mutableStateOf(false) }
    var workspaceName by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = androidx.compose.runtime.remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showMergePicker by rememberSaveable { mutableStateOf(false) }
    var pendingMergeTargetId by rememberSaveable { mutableStateOf<String?>(null) }
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
    if (showMergePicker) {
        WorkspaceMergeTargetDialog(
            canvas = state.canvas,
            candidates = state.mergeCandidates,
            onTargetSelected = { targetId ->
                showMergePicker = false
                val source = state.selectedCellId?.let { id -> state.canvas.cells.firstOrNull { it.id == id } }
                val target = state.canvas.cells.firstOrNull { it.id == targetId }
                if (source != null && target != null && requiresMergeConfirmation(source, target)) {
                    pendingMergeTargetId = targetId
                } else {
                    performMerge(state, targetId, scope, snackbarHostState)
                }
            },
            onDismiss = { showMergePicker = false },
        )
    }
    pendingMergeTargetId?.let { targetId ->
        val sourceLabel = state.selectedCellId
            ?.let { id -> state.canvas.cells.firstOrNull { it.id == id } }
            ?.app?.label
        if (sourceLabel != null) {
            WorkspaceMergeConfirmationDialog(
                sourceLabel = sourceLabel,
                onConfirm = {
                    pendingMergeTargetId = null
                    performMerge(state, targetId, scope, snackbarHostState)
                },
                onDismiss = { pendingMergeTargetId = null },
            )
        } else {
            pendingMergeTargetId = null
        }
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
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = Spacing.L,
                        vertical = InteractionZones.DeadZone,
                    ),
                ) {
                    if (maxWidth >= 520.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(InteractionZones.DeadZone),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Workspace",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            WorkspaceActionButtons(
                                isNewWorkspace = isNewWorkspace,
                                onRequestName = { showNameDialog = true },
                                onSave = onSave,
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(InteractionZones.DeadZone),
                        ) {
                            WorkspaceActionButtons(
                                isNewWorkspace = isNewWorkspace,
                                onRequestName = { showNameDialog = true },
                                onSave = onSave,
                                weighted = true,
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = Dimensions.WorkspaceDesignerContentMaxWidth)
                .padding(horizontal = Spacing.L),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.M),
            ) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
                ) { Text("Quay lại") }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Thiết kế workspace",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (isNewWorkspace) "Workspace mới" else "Chỉnh sửa bố cục hiện tại",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DesignerContextToolbar(
                state = state.toolbarState,
                onUndo = { state.undo() },
                onRedo = { state.redo() },
                onSplitHorizontal = {
                    handleSplit(state, com.trancong.dexworkspacetouch.workspace.designer.model.SplitDirection.HORIZONTAL, scope, snackbarHostState)
                },
                onSplitVertical = {
                    handleSplit(state, com.trancong.dexworkspacetouch.workspace.designer.model.SplitDirection.VERTICAL, scope, snackbarHostState)
                },
                onMergeCell = { showMergePicker = true },
                onDecreaseDivider = { resizeSelectedDivider(state, -DIVIDER_STEP) },
                onIncreaseDivider = { resizeSelectedDivider(state, DIVIDER_STEP) },
                onResetDivider = { resetSelectedDivider(state) },
                onClearSelection = state::clearSelection,
                modifier = Modifier.padding(top = Spacing.S),
            )
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
                    Surface(
                        modifier = Modifier.size(fittedSize.width.dp, fittedSize.height.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = DesignerElevation.SnapshotCard,
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
                            onDividerDragStart = state::beginDividerResize,
                            onDividerDragRatio = state::updateDividerResize,
                            onDividerDragEnd = { state.commitDividerResize() },
                            onDividerDragCancel = state::cancelDividerResize,
                            appIconLoader = appIconLoader,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.WorkspaceActionButtons(
    isNewWorkspace: Boolean,
    onRequestName: () -> Unit,
    onSave: (String?) -> Unit,
    weighted: Boolean = false,
) {
    val buttonModifier = if (weighted) Modifier.weight(1f) else Modifier.widthIn(min = 120.dp)
    OutlinedButton(
        onClick = {},
        modifier = buttonModifier.height(TouchTargets.PrimaryButton),
    ) { Text("Mở") }
    Button(
        onClick = { if (isNewWorkspace) onRequestName() else onSave(null) },
        modifier = buttonModifier.height(TouchTargets.PrimaryButton),
    ) { Text("Lưu") }
}

private fun performMerge(
    state: WorkspaceDesignerViewModel,
    targetCellId: String,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState,
) {
    if (state.mergeSelectedCell(targetCellId) is WorkspaceMergeResult.Failure) {
        scope.launch { snackbarHostState.showSnackbar("Không thể gộp hai ô này.") }
    }
}

private fun handleSplit(
    state: WorkspaceDesignerViewModel,
    direction: com.trancong.dexworkspacetouch.workspace.designer.model.SplitDirection,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState,
) {
    if (state.splitSelectedCell(direction) == SplitResult.MaximumCellsReached) {
        scope.launch {
            snackbarHostState.showSnackbar("Workspace hỗ trợ tối đa ${WorkspaceLimits.MaxCells} ô.")
        }
    }
}

private fun resizeSelectedDivider(state: WorkspaceDesignerViewModel, delta: Float) {
    val divider = state.toolbarState.context as? DesignerContextToolbarState.Context.Divider ?: return
    state.resizeDivider(
        divider.dividerId,
        (divider.ratio + delta).coerceIn(Dimensions.MinCellRatio, Dimensions.MaxCellRatio),
    )
}

private fun resetSelectedDivider(state: WorkspaceDesignerViewModel) {
    val divider = state.toolbarState.context as? DesignerContextToolbarState.Context.Divider ?: return
    state.resizeDivider(divider.dividerId, 0.5f)
}

private const val DIVIDER_STEP = 0.05f
