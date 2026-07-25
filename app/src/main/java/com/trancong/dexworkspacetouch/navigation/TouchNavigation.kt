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
import androidx.navigation.compose.currentBackStackEntryAsState
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
import com.trancong.dexworkspacetouch.workspace.librarytransfer.AndroidWorkspaceLibraryTransferPlatform
import com.trancong.dexworkspacetouch.workspace.librarytransfer.LibraryOutputActionState
import com.trancong.dexworkspacetouch.workspace.librarytransfer.WorkspaceLibraryTransferException
import com.trancong.dexworkspacetouch.workspace.librarytransfer.WorkspaceLibraryTransferFailure
import com.trancong.dexworkspacetouch.workspace.librarytransfer.WorkspaceLibraryTransferFormat
import com.trancong.dexworkspacetouch.workspace.librarytransfer.WorkspaceLibraryTransferState
import com.trancong.dexworkspacetouch.workspace.librarytransfer.WorkspaceLibraryTransferViewModel
import com.trancong.dexworkspacetouch.workspace.librarytransfer.beginLibraryOutputAction
import com.trancong.dexworkspacetouch.workspace.librarytransfer.finishLibraryOutputAction
import com.trancong.dexworkspacetouch.workspace.snapshot.ui.WorkspaceSnapshot
import com.trancong.dexworkspacetouch.ui.design.TouchTargets
import kotlinx.coroutines.launch
import com.trancong.dexworkspacetouch.workspace.externaltransfer.ExternalTransferDetection
import com.trancong.dexworkspacetouch.workspace.externaltransfer.ExternalTransferInboxState
import com.trancong.dexworkspacetouch.workspace.externaltransfer.ExternalTransferReadFailure
import com.trancong.dexworkspacetouch.workspace.externaltransfer.ExternalTransferViewModel
import com.trancong.dexworkspacetouch.workspace.externaltransfer.shouldDispatchExternalTransfer
import com.trancong.dexworkspacetouch.about.platform.createAppDiagnosticInfo

private object Routes {
    const val Home = "home"
    const val LayoutDesigner = "layout-designer"
    const val AppPicker = "app-picker"
}

@Composable
fun TouchNavigation(activity: Activity, externalTransferViewModel: ExternalTransferViewModel) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val designerViewModel: WorkspaceDesignerViewModel = viewModel()
    val application = activity.application as DexWorkspaceTouchApplication
    val libraryViewModel: WorkspaceLibraryViewModel = viewModel(
        factory = WorkspaceLibraryViewModel.factory(application.workspaceRepository),
    )
    val transferViewModel: WorkspaceTransferViewModel = viewModel(
        factory = WorkspaceTransferViewModel.factory(application.workspaceRepository),
    )
    val transferPlatform = remember(activity) { AndroidWorkspaceTransferPlatform(activity) }
    val libraryTransferViewModel: WorkspaceLibraryTransferViewModel = viewModel(
        factory = WorkspaceLibraryTransferViewModel.factory(application.workspaceRepository),
    )
    val libraryTransferPlatform = remember(activity) { AndroidWorkspaceLibraryTransferPlatform(activity) }
    val transferScope = rememberCoroutineScope()
    var exportMode by rememberSaveable { mutableStateOf<String?>(null) }
    var libraryOutputInProgress by rememberSaveable { mutableStateOf(false) }

    fun beginLibraryOutput(): Boolean {
        val transition = beginLibraryOutputAction(
            LibraryOutputActionState(inProgress = libraryOutputInProgress),
        )
        libraryOutputInProgress = transition.state.inProgress
        return transition.shouldLaunch
    }

    fun finishLibraryOutput() {
        libraryOutputInProgress = finishLibraryOutputAction().inProgress
    }

    val transferOperationActive = transferViewModel.state !is WorkspaceTransferState.Idle ||
        libraryTransferViewModel.state !is WorkspaceLibraryTransferState.Idle ||
        externalTransferViewModel.state !is ExternalTransferInboxState.Idle
    LaunchedEffect(
        externalTransferViewModel.state,
        currentRoute,
        libraryViewModel.isMultiSelectMode,
        transferViewModel.state,
        libraryTransferViewModel.state,
    ) {
        val pending = externalTransferViewModel.state as? ExternalTransferInboxState.Pending
            ?: return@LaunchedEffect
        if (!shouldDispatchExternalTransfer(
                currentRoute == Routes.Home && !libraryViewModel.isMultiSelectMode,
            )
        ) return@LaunchedEffect
        if (!transferViewModel.prepareForExternalImport() || !libraryTransferViewModel.prepareForExternalRestore()) {
            return@LaunchedEffect
        }
        when (pending.event.detection) {
            ExternalTransferDetection.SingleWorkspace -> transferViewModel.readImport(pending.event.bytes)
            ExternalTransferDetection.LibraryBundle -> libraryTransferViewModel.readRestore(pending.event.bytes)
            else -> Unit
        }
        externalTransferViewModel.consume(pending.event.identity)
    }
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
    val librarySaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(WorkspaceLibraryTransferFormat.MimeType),
    ) { uri ->
        finishLibraryOutput()
        val ready = libraryTransferViewModel.state as? WorkspaceLibraryTransferState.BackupReady
        if (uri == null || ready == null) libraryTransferViewModel.consumeBackup() else transferScope.launch {
            try {
                libraryTransferPlatform.write(uri, ready.bytes)
                libraryTransferViewModel.complete(
                    if (ready.selectedExport) "Đã lưu ${ready.workspaceCount} workspace."
                    else "Đã lưu bản sao Library.",
                )
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (_: Exception) { libraryTransferViewModel.fail(WorkspaceLibraryTransferFailure.WRITE_FAILURE) }
        }
    }
    val libraryRestoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) transferScope.launch {
            try { libraryTransferViewModel.readRestore(libraryTransferPlatform.read(uri)) }
            catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: WorkspaceLibraryTransferException) { libraryTransferViewModel.fail(error.failure) }
            catch (_: Exception) { libraryTransferViewModel.fail(WorkspaceLibraryTransferFailure.READ_FAILURE) }
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
    when (val bundleState = libraryTransferViewModel.state) {
        WorkspaceLibraryTransferState.PreparingBackup,
        WorkspaceLibraryTransferState.ReadingRestore,
        WorkspaceLibraryTransferState.Restoring -> AlertDialog(
            onDismissRequest = {},
            title = { Text(when (bundleState) {
                WorkspaceLibraryTransferState.PreparingBackup -> "Đang chuẩn bị bản sao lưu…"
                WorkspaceLibraryTransferState.ReadingRestore -> "Đang đọc bản sao lưu…"
                else -> "Đang khôi phục Library…"
            }) },
            confirmButton = {},
        )
        is WorkspaceLibraryTransferState.BackupWarning -> AlertDialog(
            onDismissRequest = libraryTransferViewModel::cancel,
            title = { Text(if (bundleState.selectedExport) "Một số workspace đã chọn không thể xuất." else "Có ${bundleState.skippedCount} workspace không thể đưa vào bản sao lưu.") },
            text = { Text(if (bundleState.selectedExport) "Tiếp tục xuất ${bundleState.validCount} workspace hợp lệ?" else "Tiếp tục sao lưu ${bundleState.validCount} workspace hợp lệ?") },
            confirmButton = { TextButton(onClick = libraryTransferViewModel::continueBackup, modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton)) { Text("Tiếp tục") } },
            dismissButton = { TextButton(onClick = libraryTransferViewModel::cancel, modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton)) { Text("Hủy") } },
        )
        is WorkspaceLibraryTransferState.BackupReady -> AlertDialog(
            onDismissRequest = {
                if (!libraryOutputInProgress) libraryTransferViewModel.consumeBackup()
            },
            title = { Text(if (bundleState.selectedExport) "Xuất workspace đã chọn" else "Sao lưu Library") },
            text = { Text("${bundleState.fileName}\n${if (bundleState.selectedExport) "Chọn cách xuất ${bundleState.workspaceCount} workspace." else "Chọn cách lưu bản sao."}") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (beginLibraryOutput()) {
                            transferScope.launch {
                                try {
                                    libraryTransferPlatform.share(bundleState)
                                    libraryTransferViewModel.consumeBackup()
                                } catch (error: kotlinx.coroutines.CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    libraryTransferViewModel.fail(
                                        WorkspaceLibraryTransferFailure.WRITE_FAILURE,
                                    )
                                } finally {
                                    finishLibraryOutput()
                                }
                            }
                        }
                    },
                    enabled = !libraryOutputInProgress,
                    modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
                ) { Text("Chia sẻ") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (beginLibraryOutput()) {
                            try {
                                librarySaveLauncher.launch(bundleState.fileName)
                            } catch (_: Exception) {
                                finishLibraryOutput()
                                libraryTransferViewModel.fail(
                                    WorkspaceLibraryTransferFailure.WRITE_FAILURE,
                                )
                            }
                        }
                    },
                    enabled = !libraryOutputInProgress,
                    modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
                ) { Text("Lưu vào tệp") }
            },
        )
        is WorkspaceLibraryTransferState.RestorePreview -> AlertDialog(
            onDismissRequest = libraryTransferViewModel::cancel,
            title = { Text("Khôi phục Library") },
            text = {
                val preview = bundleState.preview
                androidx.compose.foundation.layout.Column {
                    Text("${preview.workspaceCount} workspace • ${preview.totalCellCount} ô • ${preview.totalAssignedAppCount} ứng dụng")
                    if (preview.sampleWorkspaceNames.isNotEmpty()) Text(preview.sampleWorkspaceNames.joinToString())
                    if (preview.conflictingNameCount > 0) Text("${preview.conflictingNameCount} tên trùng sẽ được đổi tên.")
                    Text("Các workspace sẽ được thêm mới, không ghi đè dữ liệu hiện có.")
                }
            },
            confirmButton = { TextButton(onClick = libraryTransferViewModel::confirmRestore, modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton)) { Text("Khôi phục") } },
            dismissButton = { TextButton(onClick = libraryTransferViewModel::cancel, modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton)) { Text("Hủy") } },
        )
        is WorkspaceLibraryTransferState.Completed -> AlertDialog(
            onDismissRequest = libraryTransferViewModel::dismissFeedback,
            title = { Text(bundleState.message) },
            confirmButton = { TextButton(onClick = libraryTransferViewModel::dismissFeedback) { Text("Đã hiểu") } },
        )
        is WorkspaceLibraryTransferState.Error -> AlertDialog(
            onDismissRequest = libraryTransferViewModel::dismissFeedback,
            title = { Text(bundleState.failure.libraryUserMessage()) },
            confirmButton = { TextButton(onClick = libraryTransferViewModel::dismissFeedback) { Text("Đã hiểu") } },
        )
        WorkspaceLibraryTransferState.Idle -> Unit
    }
    if (currentRoute == Routes.Home) {
        when (val externalState = externalTransferViewModel.state) {
            is ExternalTransferInboxState.Reading -> AlertDialog(
                onDismissRequest = {},
                title = { Text("Đang đọc file workspace…") },
                confirmButton = {},
            )
            is ExternalTransferInboxState.Error -> AlertDialog(
                onDismissRequest = { externalTransferViewModel.consume(externalState.identity) },
                title = { Text(externalState.failure.externalUserMessage()) },
                confirmButton = {
                    TextButton(
                        onClick = { externalTransferViewModel.consume(externalState.identity) },
                        modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
                    ) { Text("Đã hiểu") }
                },
            )
            else -> Unit
        }
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
                isMultiSelectMode = libraryViewModel.isMultiSelectMode,
                selectedWorkspaceIds = libraryViewModel.selectedWorkspaceIds,
                selectedPinnedCount = libraryViewModel.selectedPinnedCount,
                selectedUnpinnedCount = libraryViewModel.selectedUnpinnedCount,
                onEnterMultiSelect = libraryViewModel::enterMultiSelect,
                onToggleMultiSelect = libraryViewModel::toggleMultiSelect,
                onExitMultiSelect = libraryViewModel::exitMultiSelect,
                onBatchPin = { libraryViewModel.setSelectedPinned(true) },
                onBatchUnpin = { libraryViewModel.setSelectedPinned(false) },
                onBatchExport = {
                    if (!transferOperationActive) {
                        libraryTransferViewModel.prepareSelectedBackup(libraryViewModel.selectedWorkspaceIds)
                    }
                },
                onBatchDelete = libraryViewModel::deleteSelectedWorkspaces,
                multiSelectTransferInProgress = transferOperationActive,
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
                batchFeedback = libraryViewModel.batchFeedback,
                onDismissBatchFeedback = libraryViewModel::dismissBatchFeedback,
                launchState = launchViewModel.state,
                onLaunchWorkspace = { workspace ->
                    launchViewModel.launchWorkspace(workspace, launchRuntime, launchHostToken)
                },
                onCancelLaunch = launchViewModel::cancelLaunch,
                onDismissLaunchResult = launchViewModel::dismissResult,
                onShareWorkspace = { id -> exportMode = "share"; transferViewModel.prepareExport(id) },
                onSaveWorkspaceToFile = { id -> exportMode = "save"; transferViewModel.prepareExport(id) },
                onImportWorkspace = { importLauncher.launch("*/*") },
                onBackupLibrary = libraryTransferViewModel::prepareBackup,
                onRestoreLibrary = { libraryRestoreLauncher.launch("*/*") },
                backupLibraryEnabled = libraryViewModel.workspaces.isNotEmpty() && !libraryViewModel.isWriting,
                diagnosticInfo = createAppDiagnosticInfo(activity),
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

private fun WorkspaceLibraryTransferFailure.libraryUserMessage(): String = when (this) {
    WorkspaceLibraryTransferFailure.UNSUPPORTED_VERSION -> "Bản sao lưu được tạo bởi phiên bản mới hơn."
    WorkspaceLibraryTransferFailure.UNSUPPORTED_WORKSPACE_SCHEMA -> "Bản sao lưu chứa workspace thuộc phiên bản chưa được hỗ trợ."
    WorkspaceLibraryTransferFailure.TOO_MANY_WORKSPACES -> "Bản sao lưu có quá nhiều workspace."
    WorkspaceLibraryTransferFailure.TOO_MANY_CELLS -> "Bản sao lưu có quá nhiều ô."
    WorkspaceLibraryTransferFailure.FILE_TOO_LARGE -> "File bản sao lưu quá lớn."
    WorkspaceLibraryTransferFailure.INVALID_WORKSPACE -> "Bản sao lưu có dữ liệu workspace không hợp lệ."
    WorkspaceLibraryTransferFailure.EMPTY_LIBRARY -> "Chưa có workspace để sao lưu."
    WorkspaceLibraryTransferFailure.SELECTED_WORKSPACES_UNAVAILABLE -> "Một số workspace đã chọn không thể xuất."
    WorkspaceLibraryTransferFailure.TOO_MANY_SELECTED_WORKSPACES -> "Có quá nhiều workspace để xuất trong một file."
    WorkspaceLibraryTransferFailure.READ_FAILURE -> "Không thể đọc bản sao lưu Library."
    WorkspaceLibraryTransferFailure.WRITE_FAILURE -> "Không thể ghi bản sao lưu hoặc khôi phục Library."
    WorkspaceLibraryTransferFailure.ID_GENERATION_FAILURE -> "Không thể tạo ID mới cho workspace."
    WorkspaceLibraryTransferFailure.INVALID_FORMAT -> "File bản sao lưu không hợp lệ."
}

private fun ExternalTransferReadFailure.externalUserMessage(): String = when (this) {
    ExternalTransferReadFailure.READ_FAILURE,
    ExternalTransferReadFailure.PERMISSION_REVOKED -> "Không thể đọc file được chia sẻ."
    ExternalTransferReadFailure.INVALID_CONTENT -> "File không phải dữ liệu DexWorkspaceTouch."
    ExternalTransferReadFailure.UNSUPPORTED_VERSION -> "File được tạo bởi phiên bản mới hơn."
    ExternalTransferReadFailure.FILE_TOO_LARGE -> "File được chia sẻ quá lớn."
    ExternalTransferReadFailure.MISSING_URI -> "Không tìm thấy file được chia sẻ."
    ExternalTransferReadFailure.MULTIPLE_URIS -> "Hiện chỉ hỗ trợ nhập một file mỗi lần."
    ExternalTransferReadFailure.INVALID_PAYLOAD -> "File chia sẻ không hợp lệ."
}
