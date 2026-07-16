package com.trancong.dexworkspacetouch.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.trancong.dexworkspacetouch.ui.design.Dimensions
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets
import com.trancong.dexworkspacetouch.workspace.library.model.WorkspaceLibraryItem
import com.trancong.dexworkspacetouch.workspace.library.ui.WorkspaceLibraryCard
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    workspaces: List<WorkspaceLibraryItem>,
    selectedWorkspaceId: String?,
    onWorkspaceSelected: (String) -> Unit,
    onCreateWorkspace: () -> Unit,
    onEditWorkspace: (String) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(Dimensions.WorkspaceCardMinWidth),
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(Spacing.L),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.WorkspaceGrid),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.WorkspaceGrid),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("DeX Workspace Manager", style = MaterialTheme.typography.headlineMedium)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Workspace Library")
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Button(
                    onClick = onCreateWorkspace,
                    modifier = Modifier.fillMaxWidth().height(TouchTargets.PrimaryButton),
                ) { Text("Tạo bố cục mới") }
            }
            if (workspaces.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Chưa có workspace. Hãy tạo bố cục đầu tiên.")
                }
            } else {
                items(workspaces, key = WorkspaceLibraryItem::id) { workspace ->
                    WorkspaceLibraryCard(
                        workspace = workspace,
                        selected = workspace.id == selectedWorkspaceId,
                        onSelect = { onWorkspaceSelected(workspace.id) },
                        onOpen = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    "Chức năng mở workspace sẽ được tích hợp sau.",
                                )
                            }
                        },
                        onEdit = { onEditWorkspace(workspace.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
