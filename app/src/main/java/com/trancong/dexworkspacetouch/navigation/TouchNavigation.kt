package com.trancong.dexworkspacetouch.navigation

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.trancong.dexworkspacetouch.ui.screens.AppPickerScreen
import com.trancong.dexworkspacetouch.ui.screens.HomeScreen
import com.trancong.dexworkspacetouch.ui.screens.LayoutDesignerScreen
import com.trancong.dexworkspacetouch.workspace.designer.state.WorkspaceDesignerViewModel
import com.trancong.dexworkspacetouch.workspace.apppicker.infrastructure.AndroidInstalledAppDataSource
import com.trancong.dexworkspacetouch.workspace.apppicker.model.DefaultInstalledAppCatalog
import com.trancong.dexworkspacetouch.workspace.apppicker.model.toIdentity
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.AppPickerViewModel
import com.trancong.dexworkspacetouch.workspace.library.state.WorkspaceLibraryViewModel
import com.trancong.dexworkspacetouch.platform.launch.android.AndroidWorkspaceLaunchRuntime
import com.trancong.dexworkspacetouch.workspace.launcher.WorkspaceLaunchRequestFactory
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.WorkspaceLaunchViewModel
import com.trancong.dexworkspacetouch.DexWorkspaceTouchApplication
import com.trancong.dexworkspacetouch.workspace.persistence.repository.WorkspacePersistenceIssue
import com.trancong.dexworkspacetouch.workspace.transfer.AndroidWorkspaceTransferPlatform
import com.trancong.dexworkspacetouch.workspace.transfer.WorkspaceTransferFailure
import com.trancong.dexworkspacetouch.workspace.transfer.WorkspaceTransferFormat
import com.trancong.dexworkspacetouch.workspace.transfer.WorkspaceTransferState
import com.trancong.dexworkspacetouch.workspace.transfer.WorkspaceTransferViewModel
import com.trancong.dexworkspacetouch.workspace.snapshot.ui.WorkspaceSnapshot
import com.trancong.dexworkspacetouch.ui.design.TouchTargets
import kotlinx.coroutines.launch

private object Routes {
    const val Home = "home"
    const val LayoutDesigner = "layout-designer"
    const val AppPicker = "app-picker"
}

@Composable
fun TouchNavigation(activity: Activity) {
    val navController = rememberNavController()
    val designerViewModel: WorkspaceDesignerViewModel = viewModel()
    val application = activity.application as DexWorkspaceTouchApplication
    val libraryViewModel: WorkspaceLibraryViewModel = viewModel(
        factory = WorkspaceLibraryViewModel.factory(application.workspaceRepository),
    )
    val transferViewModel: WorkspaceTransferViewModel = viewModel(
        factory = WorkspaceTransferViewModel.factory(application.workspaceRepository),
    )
    val transferPlatform = remember(activity) { AndroidWorkspaceTransferPlatform(activity) }
    val transferScope = rememberCoroutineScope()
    var exportMode by rememberSaveable { mutableStateOf<String?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(WorkspaceTransferFormat.MimeType),
    ) { uri ->
        val ready = transferViewModel.state as? WorkspaceTransferState.ExportReady
        if (uri == null || ready == null) transferViewModel.consumeExport() else transferScope.launch {
            try { transferPlatform.write(uri, ready.bytes); transferViewModel.complete("Đã lưu workspace.") }
            catch (_: Exception) { transferViewModel.fail(WorkspaceTransferFailure.WRITE_FAILURE) }
        }
        exportMode = null
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) transferScope.launch {
            try { transferViewModel.readImport(transferPlatform.read(uri)) }
            catch (error: com.trancong.dexworkspacetouch.workspace.transfer.WorkspaceTransferException) {
                transferViewModel.fail(error.failure)
            }
        }
    }
    LaunchedEffect(transferViewModel.state, exportMode) {
        val ready = transferViewModel.state as? WorkspaceTransferState.ExportReady ?: return@LaunchedEffect
        when (exportMode) {
            "share" -> {
                try {
                    transferPlatform.share(ready)
                    exportMode = null
                    transferViewModel.consumeExport()
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (_: Exception) {
                    exportMode = null
                    transferViewModel.fail(WorkspaceTransferFailure.WRITE_FAILURE)
                }
            }
            "save" -> { exportMode = "save-launched"; saveLauncher.launch(ready.fileName) }
        }
    }
    when (val transferState = transferViewModel.state) {
        WorkspaceTransferState.PreparingExport, WorkspaceTransferState.ReadingImport, WorkspaceTransferState.Importing -> AlertDialog(
            onDismissRequest = {},
            title = { Text(if (transferState is WorkspaceTransferState.PreparingExport) "Đang chuẩn bị file…" else "Đang đọc workspace…") },
            confirmButton = {},
        )
        is WorkspaceTransferState.ImportPreview -> AlertDialog(
            onDismissRequest = transferViewModel::cancelPreview,
            title = { Text("Nhập workspace") },
            text = {
                androidx.compose.foundation.layout.Column {
                    WorkspaceSnapshot(transferState.payload.canvas, Modifier.fillMaxWidth())
                    Text(transferState.payload.name)
                    Text("${transferState.payload.canvas.cells.size} ô • ${transferState.payload.canvas.cells.count { it.app != null }} ứng dụng")
                }
            },
            confirmButton = { TextButton(onClick = transferViewModel::confirmImport, modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton)) { Text("Nhập") } },
            dismissButton = { TextButton(onClick = transferViewModel::cancelPreview, modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton)) { Text("Hủy") } },
        )
        is WorkspaceTransferState.Completed -> AlertDialog(
            onDismissRequest = transferViewModel::dismissFeedback,
            title = { Text(transferState.message) },
            confirmButton = { TextButton(onClick = transferViewModel::dismissFeedback) { Text("Đã hiểu") } },
        )
        is WorkspaceTransferState.Error -> AlertDialog(
            onDismissRequest = transferViewModel::dismissFeedback,
            title = { Text(transferState.failure.userMessage()) },
            confirmButton = { TextButton(onClick = transferViewModel::dismissFeedback) { Text("Đã hiểu") } },
        )
        else -> Unit
    }
    val applicationContext = activity.applicationContext
    val installedAppCatalog = remember(applicationContext) {
        DefaultInstalledAppCatalog(AndroidInstalledAppDataSource.create(applicationContext))
    }
    val launchViewModel: WorkspaceLaunchViewModel = viewModel(
        factory = WorkspaceLaunchViewModel.factory(
            WorkspaceLaunchRequestFactory(installedAppCatalog),
        ),
    )
    val launchHostToken = remember(activity) { Any() }
    val launchRuntime = remember(activity, launchViewModel.legacyReferenceStore) {
        AndroidWorkspaceLaunchRuntime(activity, launchViewModel.legacyReferenceStore)
    }
    DisposableEffect(launchRuntime) {
        onDispose { launchViewModel.onHostDisposed(launchHostToken) }
    }
    NavHost(navController = navController, startDestination = Routes.Home) {
        composable(Routes.Home) {
            HomeScreen(
                workspaces = libraryViewModel.visibleWorkspaces,
                pinnedWorkspaces = libraryViewModel.pinnedWorkspaces,
                regularWorkspaces = libraryViewModel.regularWorkspaces,
                hasSourceWorkspaces = libraryViewModel.workspaces.isNotEmpty(),
                searchQuery = libraryViewModel.searchQuery,
                sortMode = libraryViewModel.sortMode,
                onSearchQueryChanged = libraryViewModel::updateSearchQuery,
                onClearSearch = libraryViewModel::clearSearchQuery,
                onSortModeChanged = libraryViewModel::updateSortMode,
                selectedWorkspaceId = libraryViewModel.selectedWorkspaceId,
                editingWorkspaceId = libraryViewModel.editingWorkspaceId,
                onWorkspaceSelected = libraryViewModel::selectWorkspace,
                onCreateWorkspace = { templateCanvas ->
                    designerViewModel.loadCanvas(libraryViewModel.createWorkspace(templateCanvas))
                    navController.navigate(Routes.LayoutDesigner)
                },
                onEditWorkspace = { workspaceId ->
                    designerViewModel.loadCanvas(libraryViewModel.beginEditingWorkspace(workspaceId))
                    navController.navigate(Routes.LayoutDesigner)
                },
                onRenameWorkspace = { id, name -> libraryViewModel.renameWorkspace(id, name) },
                onDuplicateWorkspace = libraryViewModel::duplicateWorkspace,
                onSetWorkspacePinned = libraryViewModel::setWorkspacePinned,
                onDeleteWorkspace = libraryViewModel::deleteWorkspace,
                libraryIsLoading = libraryViewModel.isLoading,
                persistenceError = libraryViewModel.persistenceError,
                hasCorruptedWorkspaces = libraryViewModel.persistenceIssues.any {
                    it is WorkspacePersistenceIssue.CorruptedRow
                },
                hasUnsupportedWorkspaces = libraryViewModel.persistenceIssues.any {
                    it is WorkspacePersistenceIssue.UnsupportedSchema
                },
                onRetryLibrary = libraryViewModel::retryLoad,
                onDismissPersistenceError = libraryViewModel::dismissPersistenceError,
                duplicateFeedback = libraryViewModel.duplicateFeedback,
                onDismissDuplicateFeedback = libraryViewModel::dismissDuplicateFeedback,
                libraryWriteInProgress = libraryViewModel.isWriting,
                pinFeedback = libraryViewModel.pinFeedback,
                onDismissPinFeedback = libraryViewModel::dismissPinFeedback,
                launchState = launchViewModel.state,
                onLaunchWorkspace = { workspace ->
                    launchViewModel.launchWorkspace(workspace, launchRuntime, launchHostToken)
                },
                onCancelLaunch = launchViewModel::cancelLaunch,
                onDismissLaunchResult = launchViewModel::dismissResult,
                onShareWorkspace = { id -> exportMode = "share"; transferViewModel.prepareExport(id) },
                onSaveWorkspaceToFile = { id -> exportMode = "save"; transferViewModel.prepareExport(id) },
                onImportWorkspace = { importLauncher.launch("*/*") },
                appIconLoader = application.appIconLoader,
                nowEpochMillis = com.trancong.dexworkspacetouch.workspace.library.state.SystemWorkspaceClock.nowEpochMillis(),
            )
        }
        composable(Routes.LayoutDesigner) {
            LayoutDesignerScreen(
                state = designerViewModel,
                isNewWorkspace = libraryViewModel.isCreatingWorkspace,
                onBack = {
                    libraryViewModel.finishEditing()
                    navController.navigateUp()
                },
                onOpenAppPicker = { cellId ->
                    navController.navigate("${Routes.AppPicker}/$cellId") {
                        launchSingleTop = true
                    }
                },
                onSave = { name ->
                    libraryViewModel.saveWorkspace(designerViewModel.canvas, name) {
                        libraryViewModel.finishEditing()
                        navController.navigateUp()
                    }
                },
            )
        }
        composable("${Routes.AppPicker}/{cellId}") { backStackEntry ->
            val requestedCellId = backStackEntry.arguments?.getString("cellId")
            val validCellId = requestedCellId?.takeIf { cellId ->
                designerViewModel.canvas.cells.any { it.id == cellId }
            }
            val selectedIdentity = validCellId
                ?.let { cellId -> designerViewModel.canvas.cells.first { it.id == cellId }.app }
                ?.toIdentity()
            DisposableEffect(validCellId) {
                onDispose { designerViewModel.onAppPickerClosed() }
            }
            val appPickerViewModel: AppPickerViewModel = viewModel(
                factory = AppPickerViewModel.factory(
                    catalog = installedAppCatalog,
                    iconLoader = application.appIconLoader,
                    selectedIdentity = selectedIdentity,
                ),
            )
            AppPickerScreen(
                cellId = validCellId,
                state = appPickerViewModel,
                onBack = {
                    designerViewModel.onAppPickerClosed()
                    navController.navigateUp()
                },
                onAppSelected = { cellId, app ->
                    designerViewModel.assignApp(cellId, app)
                    designerViewModel.onAppPickerClosed()
                    navController.navigateUp()
                },
            )
        }
    }
}

private fun WorkspaceTransferFailure.userMessage(): String = when (this) {
    WorkspaceTransferFailure.UNSUPPORTED_VERSION -> "File được tạo bởi phiên bản mới hơn."
    WorkspaceTransferFailure.WRITE_FAILURE -> "Không thể xuất workspace."
    WorkspaceTransferFailure.READ_FAILURE -> "Không thể đọc file workspace."
    else -> "File workspace không hợp lệ."
}
