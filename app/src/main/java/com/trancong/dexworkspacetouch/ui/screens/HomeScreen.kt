package com.trancong.dexworkspacetouch.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import com.trancong.dexworkspacetouch.ui.design.Dimensions
import com.trancong.dexworkspacetouch.ui.design.DesignerElevation
import com.trancong.dexworkspacetouch.ui.design.DesignerShapes
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets
import com.trancong.dexworkspacetouch.workspace.library.model.WorkspaceLibraryItem
import com.trancong.dexworkspacetouch.workspace.library.ui.WorkspaceLibraryCard
import com.trancong.dexworkspacetouch.workspace.library.ui.WorkspaceMultiSelectToolbar
import com.trancong.dexworkspacetouch.workspace.library.ui.workspaceLibraryInteractionPolicy
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.WorkspaceLaunchStatusDialog
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.WorkspaceLaunchUiState
import com.trancong.dexworkspacetouch.workspace.library.state.WorkspaceLibraryPersistenceError
import com.trancong.dexworkspacetouch.workspace.library.state.WorkspaceDuplicateFeedback
import com.trancong.dexworkspacetouch.workspace.library.state.WorkspaceBatchFeedback
import com.trancong.dexworkspacetouch.workspace.library.state.WorkspaceSortMode
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.templates.WorkspaceTemplateCatalog
import com.trancong.dexworkspacetouch.workspace.templates.ui.WorkspaceTemplatePickerDialog
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.AppIconLoader
import com.trancong.dexworkspacetouch.about.platform.copyDiagnosticText
import com.trancong.dexworkspacetouch.about.presentation.AboutDialogEffect
import com.trancong.dexworkspacetouch.about.presentation.AboutDialogEvent
import com.trancong.dexworkspacetouch.about.presentation.AboutDialogState
import com.trancong.dexworkspacetouch.about.presentation.AppDiagnosticInfo
import com.trancong.dexworkspacetouch.about.presentation.formatAppDiagnostics
import com.trancong.dexworkspacetouch.about.presentation.reduceAboutDialog
import com.trancong.dexworkspacetouch.about.ui.AboutDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    workspaces: List<WorkspaceLibraryItem>,
    pinnedWorkspaces: List<WorkspaceLibraryItem>,
    regularWorkspaces: List<WorkspaceLibraryItem>,
    hasSourceWorkspaces: Boolean,
    searchQuery: String,
    sortMode: WorkspaceSortMode,
    onSearchQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSortModeChanged: (WorkspaceSortMode) -> Unit,
    selectedWorkspaceId: String?,
    isMultiSelectMode: Boolean = false,
    selectedWorkspaceIds: Set<String> = emptySet(),
    selectedPinnedCount: Int = 0,
    selectedUnpinnedCount: Int = 0,
    onEnterMultiSelect: (String) -> Unit = {},
    onToggleMultiSelect: (String) -> Unit = {},
    onExitMultiSelect: () -> Unit = {},
    onBatchPin: () -> Unit = {},
    onBatchUnpin: () -> Unit = {},
    onBatchExport: () -> Unit = {},
    onBatchDelete: () -> Unit = {},
    multiSelectTransferInProgress: Boolean = false,
    editingWorkspaceId: String?,
    onWorkspaceSelected: (String) -> Unit,
    onCreateWorkspace: (WorkspaceCanvas) -> Unit,
    onEditWorkspace: (String) -> Unit,
    onRenameWorkspace: (String, String) -> Unit,
    onDuplicateWorkspace: (String) -> Unit,
    onSetWorkspacePinned: (String, Boolean) -> Unit,
    onDeleteWorkspace: (String) -> Unit,
    libraryIsLoading: Boolean,
    persistenceError: WorkspaceLibraryPersistenceError?,
    hasCorruptedWorkspaces: Boolean,
    hasUnsupportedWorkspaces: Boolean,
    onRetryLibrary: () -> Unit,
    onDismissPersistenceError: () -> Unit,
    duplicateFeedback: WorkspaceDuplicateFeedback?,
    onDismissDuplicateFeedback: () -> Unit,
    libraryWriteInProgress: Boolean,
    pinFeedback: com.trancong.dexworkspacetouch.workspace.library.state.WorkspacePinFeedback?,
    onDismissPinFeedback: () -> Unit,
    batchFeedback: WorkspaceBatchFeedback? = null,
    onDismissBatchFeedback: () -> Unit = {},
    launchState: WorkspaceLaunchUiState,
    onLaunchWorkspace: (WorkspaceLibraryItem) -> Unit,
    onCancelLaunch: () -> Unit,
    onDismissLaunchResult: () -> Unit,
    onOpenCar: () -> Unit = {},
    onOpenUpdates: () -> Unit = {},
    onShareWorkspace: (String) -> Unit = {},
    onSaveWorkspaceToFile: (String) -> Unit = {},
    onImportWorkspace: () -> Unit = {},
    onBackupLibrary: () -> Unit = {},
    onRestoreLibrary: () -> Unit = {},
    backupLibraryEnabled: Boolean = true,
    diagnosticInfo: AppDiagnosticInfo,
    appIconLoader: AppIconLoader? = null,
    nowEpochMillis: Long = 0L,
) {
    var managedWorkspaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameWorkspaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    var deleteWorkspaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var showTemplatePicker by rememberSaveable { mutableStateOf(false) }
    var selectedTemplateId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    var showFileSheet by rememberSaveable { mutableStateOf(false) }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }
    var showAboutCopySuccess by rememberSaveable { mutableStateOf(false) }
    var exportWorkspaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var showBatchDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    val templateCatalog = remember { WorkspaceTemplateCatalog.default() }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val interactionPolicy = workspaceLibraryInteractionPolicy(
        libraryIsLoading = libraryIsLoading,
        libraryWriteInProgress = libraryWriteInProgress,
        transferOperationActive = multiSelectTransferInProgress,
    )

    fun dispatchAboutEvent(event: AboutDialogEvent) {
        val transition = reduceAboutDialog(
            AboutDialogState(showAboutDialog, showAboutCopySuccess),
            event,
        )
        showAboutDialog = transition.state.isOpen
        showAboutCopySuccess = transition.state.copySuccessVisible
        if (transition.effect == AboutDialogEffect.CopyDiagnostics) {
            if (copyDiagnosticText(context, formatAppDiagnostics(diagnosticInfo))) {
                val completed = reduceAboutDialog(transition.state, AboutDialogEvent.CopySucceeded)
                showAboutDialog = completed.state.isOpen
                showAboutCopySuccess = completed.state.copySuccessVisible
            }
        }
    }

    BackHandler(enabled = isMultiSelectMode) { onExitMultiSelect() }

    LaunchedEffect(isMultiSelectMode) {
        if (isMultiSelectMode) {
            managedWorkspaceId = null
            renameWorkspaceId = null
            deleteWorkspaceId = null
            exportWorkspaceId = null
            showSortSheet = false
            showFileSheet = false
            showAboutDialog = false
            showAboutCopySuccess = false
            showTemplatePicker = false
        } else {
            showBatchDeleteConfirmation = false
        }
    }

    LaunchedEffect(duplicateFeedback) {
        val message = when (val feedback = duplicateFeedback) {
            is WorkspaceDuplicateFeedback.Success -> "Đã tạo bản sao ${feedback.workspaceName}."
            WorkspaceDuplicateFeedback.Failure -> "Không thể nhân bản workspace."
            null -> return@LaunchedEffect
        }
        snackbarHostState.showSnackbar(message)
        onDismissDuplicateFeedback()
    }
    LaunchedEffect(pinFeedback) {
        val message = when (val feedback = pinFeedback) {
            is com.trancong.dexworkspacetouch.workspace.library.state.WorkspacePinFeedback.Success ->
                if (feedback.isPinned) "Đã ghim ${feedback.workspaceName}." else "Đã bỏ ghim ${feedback.workspaceName}."
            is com.trancong.dexworkspacetouch.workspace.library.state.WorkspacePinFeedback.Failure ->
                if (feedback.attemptedPinned) "Không thể ghim workspace." else "Không thể bỏ ghim workspace."
            null -> return@LaunchedEffect
        }
        snackbarHostState.showSnackbar(message)
        onDismissPinFeedback()
    }
    LaunchedEffect(batchFeedback) {
        val message = when (val feedback = batchFeedback) {
            is WorkspaceBatchFeedback.PinSuccess -> if (feedback.isPinned) {
                "Đã ghim ${feedback.count} workspace."
            } else "Đã bỏ ghim ${feedback.count} workspace."
            is WorkspaceBatchFeedback.DeleteSuccess -> "Đã xóa ${feedback.count} workspace."
            WorkspaceBatchFeedback.MutationFailure -> "Không thể cập nhật workspace đã chọn."
            null -> return@LaunchedEffect
        }
        snackbarHostState.showSnackbar(message)
        onDismissBatchFeedback()
    }

    if (showTemplatePicker) {
        WorkspaceTemplatePickerDialog(
            catalog = templateCatalog,
            selectedTemplateId = selectedTemplateId,
            onTemplateSelected = { templateId, canvas ->
                selectedTemplateId = templateId
                showTemplatePicker = false
                onCreateWorkspace(canvas)
            },
            onDismiss = { showTemplatePicker = false },
        )
    }

    WorkspaceLaunchStatusDialog(
        state = launchState,
        onCancel = onCancelLaunch,
        onDismiss = onDismissLaunchResult,
    )

    persistenceError?.let { error ->
        AlertDialog(
            onDismissRequest = onDismissPersistenceError,
            title = { Text(error.userMessage) },
            confirmButton = {
                TextButton(
                    onClick = if (
                        error.operation == com.trancong.dexworkspacetouch.workspace.library.state.WorkspaceLibraryPersistenceOperation.LOAD
                    ) onRetryLibrary else onDismissPersistenceError,
                    modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
                ) {
                    Text(
                        if (
                            error.operation == com.trancong.dexworkspacetouch.workspace.library.state.WorkspaceLibraryPersistenceOperation.LOAD
                        ) "Thử lại" else "Đã hiểu",
                    )
                }
            },
        )
    }

    fun showRename(workspace: WorkspaceLibraryItem) {
        managedWorkspaceId = null
        renameWorkspaceId = workspace.id
        renameText = workspace.name
    }

    managedWorkspaceId?.let { id ->
        workspaces.firstOrNull { it.id == id }?.let { workspace ->
            ModalBottomSheet(onDismissRequest = { managedWorkspaceId = null }) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.L),
                    verticalArrangement = Arrangement.spacedBy(Spacing.S),
                ) {
                    Text(workspace.name, style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(
                        onClick = { managedWorkspaceId = null; exportWorkspaceId = workspace.id },
                        enabled = interactionPolicy.exportEnabled,
                        modifier = Modifier.fillMaxWidth().height(TouchTargets.SecondaryButton),
                    ) { Text("Xuất") }
                    OutlinedButton(
                        onClick = {
                            managedWorkspaceId = null
                            onDuplicateWorkspace(workspace.id)
                        },
                        enabled = !libraryWriteInProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(TouchTargets.SecondaryButton)
                            .semantics {
                                contentDescription = "Nhân bản workspace ${workspace.name}."
                            },
                    ) { Text("Nhân bản") }
                    OutlinedButton(
                        onClick = { showRename(workspace) },
                        enabled = interactionPolicy.managementEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(TouchTargets.SecondaryButton)
                            .semantics {
                                contentDescription = "Đổi tên workspace ${workspace.name}."
                            },
                    ) { Text("Đổi tên") }
                    OutlinedButton(
                        onClick = {
                            managedWorkspaceId = null
                            deleteWorkspaceId = workspace.id
                        },
                        enabled = editingWorkspaceId != workspace.id &&
                            interactionPolicy.managementEnabled,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(TouchTargets.SecondaryButton)
                            .semantics {
                                contentDescription = "Xóa workspace ${workspace.name}."
                            },
                    ) { Text("Xóa") }
                    if (editingWorkspaceId == workspace.id) {
                        Text("Không thể xóa workspace đang được chỉnh sửa.")
                    }
                }
            }
        }
    }
    exportWorkspaceId?.let { id ->
        workspaces.firstOrNull { it.id == id }?.let { workspace ->
            ModalBottomSheet(onDismissRequest = { exportWorkspaceId = null }) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.L),
                    verticalArrangement = Arrangement.spacedBy(Spacing.S),
                ) {
                    Text("Xuất ${workspace.name}", style = MaterialTheme.typography.titleLarge)
                    Button(
                        onClick = { exportWorkspaceId = null; onShareWorkspace(id) },
                        modifier = Modifier.fillMaxWidth().height(TouchTargets.SecondaryButton),
                    ) { Text("Chia sẻ") }
                    OutlinedButton(
                        onClick = { exportWorkspaceId = null; onSaveWorkspaceToFile(id) },
                        modifier = Modifier.fillMaxWidth().height(TouchTargets.SecondaryButton),
                    ) { Text("Lưu vào tệp…") }
                }
            }
        }
    }

    if (showSortSheet) {
        ModalBottomSheet(onDismissRequest = { showSortSheet = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(Spacing.L),
                verticalArrangement = Arrangement.spacedBy(Spacing.S),
            ) {
                Text("Sắp xếp workspace", style = MaterialTheme.typography.titleLarge)
                WorkspaceSortMode.entries.forEach { mode ->
                    OutlinedButton(
                        onClick = {
                            onSortModeChanged(mode)
                            showSortSheet = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(TouchTargets.SecondaryButton)
                            .semantics {
                                selected = mode == sortMode
                                contentDescription = "Sắp xếp theo ${mode.accessibilityLabel()}."
                            },
                    ) { Text(mode.displayName()) }
                }
            }
        }
    }

    if (showFileSheet) {
        ModalBottomSheet(onDismissRequest = { showFileSheet = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(Spacing.L),
                verticalArrangement = Arrangement.spacedBy(Spacing.S),
            ) {
                Text("Tệp", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(
                    onClick = { showFileSheet = false; onImportWorkspace() },
                    modifier = Modifier.fillMaxWidth().height(TouchTargets.SecondaryButton),
                ) { Text("Nhập workspace") }
                OutlinedButton(
                    onClick = { showFileSheet = false; onBackupLibrary() },
                    enabled = backupLibraryEnabled,
                    modifier = Modifier.fillMaxWidth().height(TouchTargets.SecondaryButton),
                ) { Text("Sao lưu Library") }
                OutlinedButton(
                    onClick = { showFileSheet = false; onRestoreLibrary() },
                    modifier = Modifier.fillMaxWidth().height(TouchTargets.SecondaryButton),
                ) { Text("Khôi phục Library") }
                if (!backupLibraryEnabled) Text("Chưa có workspace để sao lưu.")
                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.S))
                OutlinedButton(
                    onClick = {
                        showFileSheet = false
                        dispatchAboutEvent(AboutDialogEvent.Open)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TouchTargets.SecondaryButton)
                        .semantics {
                            contentDescription = "Giới thiệu về DexWorkspaceTouch."
                        },
                ) { Text("Giới thiệu") }
            }
        }
    }

    if (showAboutDialog) {
        AboutDialog(
            info = diagnosticInfo,
            copySuccessVisible = showAboutCopySuccess,
            onCopy = { dispatchAboutEvent(AboutDialogEvent.CopyRequested) },
            onDismiss = { dispatchAboutEvent(AboutDialogEvent.Close) },
        )
    }

    renameWorkspaceId?.let { id ->
        workspaces.firstOrNull { it.id == id }?.let { workspace ->
            AlertDialog(
                onDismissRequest = { renameWorkspaceId = null },
                title = { Text("Đổi tên workspace ${workspace.name}.") },
                text = {
                    TextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        label = { Text("Tên workspace") },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onRenameWorkspace(workspace.id, renameText)
                            renameWorkspaceId = null
                        },
                        enabled = renameText.trim().isNotEmpty() &&
                            interactionPolicy.managementEnabled,
                        modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
                    ) { Text("Lưu") }
                },
                dismissButton = {
                    TextButton(
                        onClick = { renameWorkspaceId = null },
                        modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
                    ) { Text("Hủy") }
                },
            )
        }
    }

    deleteWorkspaceId?.let { id ->
        workspaces.firstOrNull { it.id == id }?.let { workspace ->
            AlertDialog(
                onDismissRequest = { deleteWorkspaceId = null },
                title = { Text("Xóa workspace ${workspace.name}?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteWorkspace(workspace.id)
                            deleteWorkspaceId = null
                        },
                        enabled = interactionPolicy.managementEnabled,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
                    ) { Text("Xóa") }
                },
                dismissButton = {
                    TextButton(
                        onClick = { deleteWorkspaceId = null },
                        modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
                    ) { Text("Hủy") }
                },
            )
        }
    }

    if (showBatchDeleteConfirmation && selectedWorkspaceIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirmation = false },
            title = { Text("Xóa ${selectedWorkspaceIds.size} workspace?") },
            text = { Text("Thao tác này không thể hoàn tác.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBatchDeleteConfirmation = false
                        onBatchDelete()
                    },
                    enabled = !libraryWriteInProgress,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
                ) { Text("Xóa") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBatchDeleteConfirmation = false },
                    modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
                ) { Text("Hủy") }
            },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (isMultiSelectMode) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = DesignerElevation.MultiSelectSelected,
                ) {
                    WorkspaceMultiSelectToolbar(
                        selectedCount = selectedWorkspaceIds.size,
                        pinnableCount = selectedUnpinnedCount,
                        unpinnableCount = selectedPinnedCount,
                        actionsEnabled = !libraryWriteInProgress && !multiSelectTransferInProgress,
                        onClose = onExitMultiSelect,
                        onPin = onBatchPin,
                        onUnpin = onBatchUnpin,
                        onExport = onBatchExport,
                        onDelete = { showBatchDeleteConfirmation = true },
                        modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.XS),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(Dimensions.WorkspaceCardMinWidth),
                modifier = Modifier.widthIn(max = Dimensions.GridContentMaxWidth).fillMaxHeight(),
                contentPadding = PaddingValues(Spacing.L),
                horizontalArrangement = Arrangement.spacedBy(Spacing.WorkspaceGrid),
                verticalArrangement = Arrangement.spacedBy(Spacing.WorkspaceGrid),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val wide = maxWidth >= Dimensions.WorkspaceLibraryToolbarWideWidth
                        if (wide) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                WorkspaceLibraryTitle()
                                if (!isMultiSelectMode) Row(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedButton(
                                        onClick = onOpenCar,
                                        modifier = Modifier.height(TouchTargets.SecondaryButton),
                                    ) { Text("Car Mode") }
                                    OutlinedButton(
                                        onClick = onOpenUpdates,
                                        modifier = Modifier.height(TouchTargets.SecondaryButton),
                                    ) { Text("Cập nhật") }
                                    TextButton(
                                        onClick = { dispatchAboutEvent(AboutDialogEvent.Open) },
                                        modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
                                    ) { Text("Giới thiệu") }
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                                WorkspaceLibraryTitle()
                                if (!isMultiSelectMode) Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                                ) {
                                    OutlinedButton(onClick = onOpenCar, modifier = Modifier.weight(1f).height(TouchTargets.SecondaryButton)) { Text("Car Mode") }
                                    OutlinedButton(onClick = onOpenUpdates, modifier = Modifier.weight(1f).height(TouchTargets.SecondaryButton)) { Text("Cập nhật") }
                                    TextButton(onClick = { dispatchAboutEvent(AboutDialogEvent.Open) }, modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton)) { Text("Info") }
                                }
                            }
                        }
                    }
                }
                if (!isMultiSelectMode) item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = DesignerShapes.Workspace,
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(Spacing.M)) {
                        val wide = maxWidth >= Dimensions.WorkspaceLibraryToolbarWideWidth
                        val create: @Composable (Modifier) -> Unit = { modifier ->
                            Button(
                                onClick = { showTemplatePicker = true },
                                enabled = interactionPolicy.createEnabled,
                                modifier = modifier.height(TouchTargets.PrimaryButton),
                            ) { Text("+ Tạo Workspace") }
                        }
                        val search: @Composable (Modifier) -> Unit = { modifier ->
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChanged,
                                placeholder = { Text("Tìm workspace") },
                                singleLine = true,
                                trailingIcon = if (searchQuery.isNotEmpty()) {
                                    {
                                        TextButton(
                                            onClick = onClearSearch,
                                            modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
                                        ) { Text("Xóa") }
                                    }
                                } else null,
                                modifier = modifier.heightIn(min = Dimensions.WorkspaceSearchMinHeight),
                            )
                        }
                        val sort: @Composable (Modifier) -> Unit = { modifier ->
                            OutlinedButton(
                                onClick = { showSortSheet = true },
                                modifier = modifier
                                    .height(TouchTargets.SecondaryButton)
                                    .widthIn(min = Dimensions.WorkspaceSortButtonMinWidth)
                                    .semantics {
                                        contentDescription =
                                            "Sắp xếp workspace. Hiện tại: ${sortMode.displayName()}."
                                    },
                            ) { Text("Sắp xếp") }
                        }
                        val files: @Composable (Modifier) -> Unit = { modifier ->
                            OutlinedButton(
                                onClick = { showFileSheet = true },
                                modifier = modifier.height(TouchTargets.SecondaryButton),
                            ) { Text("Tệp") }
                        }
                        if (wide) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                create(Modifier)
                                search(Modifier.weight(1f))
                                sort(Modifier)
                                files(Modifier)
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(Spacing.S),
                            ) {
                                create(Modifier.widthIn(min = Dimensions.WorkspaceSortButtonMinWidth))
                                search(Modifier.fillMaxWidth())
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                                    sort(Modifier.weight(1f))
                                    files(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    }
                }
            if (!libraryIsLoading && hasCorruptedWorkspaces) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Không thể đọc một số workspace.")
                }
            }
            if (!libraryIsLoading && hasUnsupportedWorkspaces) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Một workspace được tạo bằng phiên bản mới hơn và chưa thể mở.")
                }
            }
            if (libraryIsLoading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Text("Đang đọc danh sách workspace...")
                    }
                }
            } else if (!hasSourceWorkspaces) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    WorkspaceLibraryEmptyState(
                        title = "Chưa có workspace",
                        supportingText = "Tạo workspace đầu tiên để sắp xếp ứng dụng trên màn hình DeX.",
                    )
                }
            } else if (workspaces.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    WorkspaceLibraryEmptyState(
                        title = "Không tìm thấy workspace",
                        supportingText = "Thử từ khóa khác hoặc xóa bộ lọc tìm kiếm.",
                    ) {
                        if (searchQuery.trim().isNotEmpty()) {
                            OutlinedButton(
                                onClick = onClearSearch,
                                modifier = Modifier.height(TouchTargets.SecondaryButton),
                            ) { Text("Xóa tìm kiếm") }
                        }
                    }
                }
            } else {
                if (pinnedWorkspaces.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Đã ghim",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { contentDescription = "Workspace đã ghim." },
                        )
                    }
                }
                items(pinnedWorkspaces, key = { "pinned-${it.id}" }) { workspace ->
                    WorkspaceLibraryCard(
                        workspace = workspace,
                        selected = workspace.id == selectedWorkspaceId,
                        onSelect = { onWorkspaceSelected(workspace.id) },
                        onOpen = { onLaunchWorkspace(workspace) },
                        onEdit = { onEditWorkspace(workspace.id) },
                        onDuplicate = { onDuplicateWorkspace(workspace.id) },
                        onPinToggle = { onSetWorkspacePinned(workspace.id, !workspace.isPinned) },
                        onRename = { showRename(workspace) },
                        onDelete = { deleteWorkspaceId = workspace.id },
                        onManage = { managedWorkspaceId = workspace.id },
                        canDelete = editingWorkspaceId != workspace.id && interactionPolicy.managementEnabled,
                        openEnabled = launchState !is WorkspaceLaunchUiState.Checking && launchState !is WorkspaceLaunchUiState.Launching,
                        editEnabled = interactionPolicy.editEnabled,
                        duplicateEnabled = !libraryWriteInProgress,
                        pinEnabled = !libraryWriteInProgress,
                        managementEnabled = interactionPolicy.managementEnabled,
                        exportEnabled = interactionPolicy.exportEnabled,
                        onExport = { exportWorkspaceId = workspace.id },
                        appIconLoader = appIconLoader,
                        nowEpochMillis = nowEpochMillis,
                        multiSelectMode = isMultiSelectMode,
                        multiSelected = workspace.id in selectedWorkspaceIds,
                        onEnterMultiSelect = { onEnterMultiSelect(workspace.id) },
                        onToggleMultiSelect = { onToggleMultiSelect(workspace.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (regularWorkspaces.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("Workspace", style = MaterialTheme.typography.titleLarge)
                    }
                }
                items(regularWorkspaces, key = WorkspaceLibraryItem::id) { workspace ->
                    WorkspaceLibraryCard(
                        workspace = workspace,
                        selected = workspace.id == selectedWorkspaceId,
                        onSelect = { onWorkspaceSelected(workspace.id) },
                        onOpen = { onLaunchWorkspace(workspace) },
                        onEdit = { onEditWorkspace(workspace.id) },
                        onDuplicate = { onDuplicateWorkspace(workspace.id) },
                        onPinToggle = { onSetWorkspacePinned(workspace.id, !workspace.isPinned) },
                        onRename = { showRename(workspace) },
                        onDelete = { deleteWorkspaceId = workspace.id },
                        onManage = { managedWorkspaceId = workspace.id },
                        canDelete = editingWorkspaceId != workspace.id &&
                            interactionPolicy.managementEnabled,
                        openEnabled = launchState !is WorkspaceLaunchUiState.Checking &&
                            launchState !is WorkspaceLaunchUiState.Launching,
                        editEnabled = interactionPolicy.editEnabled,
                        duplicateEnabled = !libraryWriteInProgress,
                        pinEnabled = !libraryWriteInProgress,
                        managementEnabled = interactionPolicy.managementEnabled,
                        exportEnabled = interactionPolicy.exportEnabled,
                        onExport = { exportWorkspaceId = workspace.id },
                        appIconLoader = appIconLoader,
                        nowEpochMillis = nowEpochMillis,
                        multiSelectMode = isMultiSelectMode,
                        multiSelected = workspace.id in selectedWorkspaceIds,
                        onEnterMultiSelect = { onEnterMultiSelect(workspace.id) },
                        onToggleMultiSelect = { onToggleMultiSelect(workspace.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
}

@Composable
private fun WorkspaceLibraryTitle() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XXS)) {
        Text("Workspace Library", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Thiết kế và mở nhanh bố cục ứng dụng trên Samsung DeX",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WorkspaceLibraryEmptyState(
    title: String,
    supportingText: String,
    action: @Composable () -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = DesignerShapes.Workspace,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.XL),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            action()
        }
    }
}

private fun WorkspaceSortMode.displayName(): String = when (this) {
    WorkspaceSortMode.RECENTLY_UPDATED -> "Mới chỉnh sửa"
    WorkspaceSortMode.NAME_ASCENDING -> "Tên A–Z"
    WorkspaceSortMode.NAME_DESCENDING -> "Tên Z–A"
    WorkspaceSortMode.CREATED_NEWEST -> "Mới tạo"
    WorkspaceSortMode.CREATED_OLDEST -> "Cũ nhất"
}

private fun WorkspaceSortMode.accessibilityLabel(): String = when (this) {
    WorkspaceSortMode.RECENTLY_UPDATED -> "Mới chỉnh sửa"
    WorkspaceSortMode.NAME_ASCENDING -> "Tên A đến Z"
    WorkspaceSortMode.NAME_DESCENDING -> "Tên Z đến A"
    WorkspaceSortMode.CREATED_NEWEST -> "Mới tạo"
    WorkspaceSortMode.CREATED_OLDEST -> "Cũ nhất"
}
