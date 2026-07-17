package com.trancong.dexworkspacetouch.navigation

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.trancong.dexworkspacetouch.ui.screens.AppPickerScreen
import com.trancong.dexworkspacetouch.ui.screens.HomeScreen
import com.trancong.dexworkspacetouch.ui.screens.LayoutDesignerScreen
import com.trancong.dexworkspacetouch.workspace.designer.state.WorkspaceDesignerViewModel
import com.trancong.dexworkspacetouch.workspace.apppicker.infrastructure.AndroidInstalledAppDataSource
import com.trancong.dexworkspacetouch.workspace.apppicker.infrastructure.PackageManagerAppIconLoader
import com.trancong.dexworkspacetouch.workspace.apppicker.model.DefaultInstalledAppCatalog
import com.trancong.dexworkspacetouch.workspace.apppicker.model.toIdentity
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.AppPickerViewModel
import com.trancong.dexworkspacetouch.workspace.library.state.WorkspaceLibraryViewModel
import com.trancong.dexworkspacetouch.platform.launch.android.AndroidWorkspaceLaunchRuntime
import com.trancong.dexworkspacetouch.workspace.launcher.WorkspaceLaunchRequestFactory
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.WorkspaceLaunchViewModel

private object Routes {
    const val Home = "home"
    const val LayoutDesigner = "layout-designer"
    const val AppPicker = "app-picker"
}

@Composable
fun TouchNavigation(activity: Activity) {
    val navController = rememberNavController()
    val designerViewModel: WorkspaceDesignerViewModel = viewModel()
    val libraryViewModel: WorkspaceLibraryViewModel = viewModel()
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
                workspaces = libraryViewModel.workspaces,
                selectedWorkspaceId = libraryViewModel.selectedWorkspaceId,
                editingWorkspaceId = libraryViewModel.editingWorkspaceId,
                onWorkspaceSelected = libraryViewModel::selectWorkspace,
                onCreateWorkspace = {
                    designerViewModel.loadCanvas(libraryViewModel.createWorkspace())
                    navController.navigate(Routes.LayoutDesigner)
                },
                onEditWorkspace = { workspaceId ->
                    designerViewModel.loadCanvas(libraryViewModel.beginEditingWorkspace(workspaceId))
                    navController.navigate(Routes.LayoutDesigner)
                },
                onRenameWorkspace = { id, name -> libraryViewModel.renameWorkspace(id, name) },
                onDeleteWorkspace = libraryViewModel::deleteWorkspace,
                launchState = launchViewModel.state,
                onLaunchWorkspace = { workspace ->
                    launchViewModel.launchWorkspace(workspace, launchRuntime, launchHostToken)
                },
                onCancelLaunch = launchViewModel::cancelLaunch,
                onDismissLaunchResult = launchViewModel::dismissResult,
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
                    navController.navigate("${Routes.AppPicker}/$cellId")
                },
                onSave = { name ->
                    libraryViewModel.saveWorkspace(designerViewModel.canvas, name)
                    libraryViewModel.finishEditing()
                    navController.navigateUp()
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
            val appIconLoader = remember(applicationContext) {
                PackageManagerAppIconLoader.create(applicationContext)
            }
            val appPickerViewModel: AppPickerViewModel = viewModel(
                factory = AppPickerViewModel.factory(
                    catalog = installedAppCatalog,
                    iconLoader = appIconLoader,
                    selectedIdentity = selectedIdentity,
                ),
            )
            AppPickerScreen(
                cellId = validCellId,
                state = appPickerViewModel,
                onBack = navController::navigateUp,
                onAppSelected = { cellId, app ->
                    designerViewModel.assignApp(cellId, app)
                    navController.navigateUp()
                },
            )
        }
    }
}
