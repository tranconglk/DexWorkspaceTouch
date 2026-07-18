package com.trancong.dexworkspacetouch.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.trancong.dexworkspacetouch.ui.theme.DexWorkspaceTouchTheme
import com.trancong.dexworkspacetouch.workspace.library.model.WorkspaceLibraryItem
import com.trancong.dexworkspacetouch.workspace.library.state.WorkspaceSortMode
import com.trancong.dexworkspacetouch.workspace.snapshot.ui.WorkspaceSnapshotDemoData
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.WorkspaceLaunchUiState

@Preview(showBackground = true, widthDp = 720, heightDp = 900)
@Composable
private fun NarrowWorkspaceLibraryPreview() = WorkspaceLibraryPreview()

@Preview(showBackground = true, widthDp = 1120, heightDp = 900)
@Composable
private fun MediumWorkspaceLibraryPreview() = WorkspaceLibraryPreview()

@Preview(showBackground = true, widthDp = 1920, heightDp = 1000)
@Composable
private fun WideWorkspaceLibraryPreview() = WorkspaceLibraryPreview()

@Composable
private fun WorkspaceLibraryPreview() {
    DexWorkspaceTouchTheme(darkTheme = false) {
        HomeScreen(
            workspaces = previewWorkspaces,
            hasSourceWorkspaces = previewWorkspaces.isNotEmpty(),
            searchQuery = "",
            sortMode = WorkspaceSortMode.RECENTLY_UPDATED,
            onSearchQueryChanged = {},
            onClearSearch = {},
            onSortModeChanged = {},
            selectedWorkspaceId = "workspace-2",
            editingWorkspaceId = null,
            onWorkspaceSelected = {},
            onCreateWorkspace = {},
            onEditWorkspace = {},
            onRenameWorkspace = { _, _ -> },
            onDuplicateWorkspace = {},
            onDeleteWorkspace = {},
            libraryIsLoading = false,
            persistenceError = null,
            hasCorruptedWorkspaces = false,
            hasUnsupportedWorkspaces = false,
            onRetryLibrary = {},
            onDismissPersistenceError = {},
            duplicateFeedback = null,
            onDismissDuplicateFeedback = {},
            libraryWriteInProgress = false,
            launchState = WorkspaceLaunchUiState.Idle,
            onLaunchWorkspace = {},
            onCancelLaunch = {},
            onDismissLaunchResult = {},
        )
    }
}

private val previewWorkspaces = List(6) { index ->
    WorkspaceLibraryItem(
        id = "workspace-${index + 1}",
        name = "Workspace ${index + 1}",
        canvas = when (index % 3) {
            0 -> WorkspaceSnapshotDemoData.two()
            1 -> WorkspaceSnapshotDemoData.three()
            else -> WorkspaceSnapshotDemoData.four()
        },
        modifiedSequence = (6 - index).toLong(),
    )
}
