package com.trancong.dexworkspacetouch.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.trancong.dexworkspacetouch.ui.theme.DexWorkspaceTouchTheme
import com.trancong.dexworkspacetouch.workspace.library.model.WorkspaceLibraryItem
import com.trancong.dexworkspacetouch.workspace.library.state.WorkspaceSortMode
import com.trancong.dexworkspacetouch.workspace.snapshot.ui.WorkspaceSnapshotDemoData
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.WorkspaceLaunchUiState
import com.trancong.dexworkspacetouch.about.presentation.AppDiagnosticInfo
import com.trancong.dexworkspacetouch.about.presentation.AppDisplayMode

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
            pinnedWorkspaces = previewWorkspaces.filter { it.isPinned },
            regularWorkspaces = previewWorkspaces.filterNot { it.isPinned },
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
            onSetWorkspacePinned = { _, _ -> },
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
            pinFeedback = null,
            onDismissPinFeedback = {},
            launchState = WorkspaceLaunchUiState.Idle,
            onLaunchWorkspace = {},
            onCancelLaunch = {},
            onDismissLaunchResult = {},
            diagnosticInfo = previewDiagnosticInfo,
        )
    }
}

private val previewDiagnosticInfo = AppDiagnosticInfo(
    appName = "DexWorkspaceTouch",
    versionName = "1.0.0-beta.2",
    versionCode = 3,
    buildChannel = "debug",
    buildCommit = "unknown",
    buildDateUtc = "unknown",
    databaseVersion = 2,
    workspaceTransferVersion = 1,
    libraryBundleVersion = 1,
    androidSdk = 34,
    manufacturer = "Samsung",
    model = "Preview",
    isDexDisplay = null,
    currentDisplayId = 0,
    appDisplayMode = AppDisplayMode.PHONE,
)

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
