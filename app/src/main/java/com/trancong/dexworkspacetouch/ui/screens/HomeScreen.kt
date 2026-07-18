package com.trancong.dexworkspacetouch.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.trancong.dexworkspacetouch.ui.design.Dimensions
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets
import com.trancong.dexworkspacetouch.workspace.library.model.WorkspaceLibraryItem
import com.trancong.dexworkspacetouch.workspace.library.ui.WorkspaceLibraryCard
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.WorkspaceLaunchStatusDialog
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.WorkspaceLaunchUiState
import com.trancong.dexworkspacetouch.workspace.library.state.WorkspaceLibraryPersistenceError
import com.trancong.dexworkspacetouch.workspace.library.state.WorkspaceDuplicateFeedback
import com.trancong.dexworkspacetouch.workspace.library.state.WorkspaceSortMode
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.templates.WorkspaceTemplateCatalog
import com.trancong.dexworkspacetouch.workspace.templates.ui.WorkspaceTemplatePickerDialog

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
    launchState: WorkspaceLaunchUiState,
    onLaunchWorkspace: (WorkspaceLibraryItem) -> Unit,
    onCancelLaunch: () -> Unit,
    onDismissLaunchResult: () -> Unit,
) {
    var managedWorkspaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameWorkspaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    var deleteWorkspaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var showTemplatePicker by rememberSaveable { mutableStateOf(false) }
    var selectedTemplateId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    val templateCatalog = remember { WorkspaceTemplateCatalog.default() }
    val snackbarHostState = remember { SnackbarHostState() }

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
                        enabled = editingWorkspaceId != workspace.id,
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
                        enabled = renameText.trim().isNotEmpty(),
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

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(Dimensions.WorkspaceCardMinWidth),
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(Spacing.L),
            horizontalArrangement = Arrangement.spacedBy(Spacing.WorkspaceGrid),
            verticalArrangement = Arrangement.spacedBy(Spacing.WorkspaceGrid),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("DeX Workspace Manager", style = MaterialTheme.typography.headlineMedium)
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Text("Workspace Library") }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Button(
                    onClick = { showTemplatePicker = true },
                    enabled = !libraryIsLoading,
                    modifier = Modifier.fillMaxWidth().height(TouchTargets.PrimaryButton),
                ) { Text("Tạo bố cục mới") }
            }
            if (hasSourceWorkspaces) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val wide = maxWidth >= Dimensions.WorkspaceLibraryToolbarWideWidth
                        val search: @Composable (Modifier) -> Unit = { modifier ->
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChanged,
                                label = { Text("Tìm workspace") },
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
                        if (wide) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                search(Modifier.weight(1f))
                                sort(Modifier)
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(Spacing.S),
                            ) {
                                search(Modifier.fillMaxWidth())
                                sort(Modifier.fillMaxWidth())
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
                    Text("Chưa có workspace.")
                }
            } else if (workspaces.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Text("Không tìm thấy workspace phù hợp.")
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
                items(pinnedWorkspaces, key = WorkspaceLibraryItem::id) { workspace ->
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
                        canDelete = editingWorkspaceId != workspace.id,
                        openEnabled = launchState !is WorkspaceLaunchUiState.Checking &&
                            launchState !is WorkspaceLaunchUiState.Launching,
                        duplicateEnabled = !libraryWriteInProgress,
                        pinEnabled = !libraryWriteInProgress,
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
                        canDelete = editingWorkspaceId != workspace.id,
                        openEnabled = launchState !is WorkspaceLaunchUiState.Checking &&
                            launchState !is WorkspaceLaunchUiState.Launching,
                        duplicateEnabled = !libraryWriteInProgress,
                        pinEnabled = !libraryWriteInProgress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
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
