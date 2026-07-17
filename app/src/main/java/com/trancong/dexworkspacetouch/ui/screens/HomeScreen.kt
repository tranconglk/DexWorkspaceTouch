package com.trancong.dexworkspacetouch.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.trancong.dexworkspacetouch.ui.design.Dimensions
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets
import com.trancong.dexworkspacetouch.workspace.library.model.WorkspaceLibraryItem
import com.trancong.dexworkspacetouch.workspace.library.ui.WorkspaceLibraryCard
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.WorkspaceLaunchStatusDialog
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.WorkspaceLaunchUiState
import com.trancong.dexworkspacetouch.workspace.library.state.WorkspaceLibraryPersistenceError
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.templates.WorkspaceTemplateCatalog
import com.trancong.dexworkspacetouch.workspace.templates.ui.WorkspaceTemplatePickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    workspaces: List<WorkspaceLibraryItem>,
    selectedWorkspaceId: String?,
    editingWorkspaceId: String?,
    onWorkspaceSelected: (String) -> Unit,
    onCreateWorkspace: (WorkspaceCanvas) -> Unit,
    onEditWorkspace: (String) -> Unit,
    onRenameWorkspace: (String, String) -> Unit,
    onDeleteWorkspace: (String) -> Unit,
    libraryIsLoading: Boolean,
    persistenceError: WorkspaceLibraryPersistenceError?,
    hasCorruptedWorkspaces: Boolean,
    hasUnsupportedWorkspaces: Boolean,
    onRetryLibrary: () -> Unit,
    onDismissPersistenceError: () -> Unit,
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
    val templateCatalog = remember { WorkspaceTemplateCatalog.default() }

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
            } else if (workspaces.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Chưa có workspace. Hãy tạo bố cục đầu tiên.")
                }
            } else {
                items(workspaces, key = WorkspaceLibraryItem::id) { workspace ->
                    WorkspaceLibraryCard(
                        workspace = workspace,
                        selected = workspace.id == selectedWorkspaceId,
                        onSelect = { onWorkspaceSelected(workspace.id) },
                        onOpen = { onLaunchWorkspace(workspace) },
                        onEdit = { onEditWorkspace(workspace.id) },
                        onRename = { showRename(workspace) },
                        onDelete = { deleteWorkspaceId = workspace.id },
                        onManage = { managedWorkspaceId = workspace.id },
                        canDelete = editingWorkspaceId != workspace.id,
                        openEnabled = launchState !is WorkspaceLaunchUiState.Checking &&
                            launchState !is WorkspaceLaunchUiState.Launching,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
