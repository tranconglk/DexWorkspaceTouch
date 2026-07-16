package com.trancong.dexworkspacetouch.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.trancong.dexworkspacetouch.ui.screens.AppPickerScreen
import com.trancong.dexworkspacetouch.ui.screens.HomeScreen
import com.trancong.dexworkspacetouch.ui.screens.LayoutDesignerScreen
import com.trancong.dexworkspacetouch.workspace.designer.state.WorkspaceDesignerViewModel
import com.trancong.dexworkspacetouch.workspace.library.state.WorkspaceLibraryViewModel

private object Routes {
    const val Home = "home"
    const val LayoutDesigner = "layout-designer"
    const val AppPicker = "app-picker"
}

@Composable
fun TouchNavigation() {
    val navController = rememberNavController()
    val designerViewModel: WorkspaceDesignerViewModel = viewModel()
    val libraryViewModel: WorkspaceLibraryViewModel = viewModel()
    NavHost(navController = navController, startDestination = Routes.Home) {
        composable(Routes.Home) {
            HomeScreen(
                workspaces = libraryViewModel.workspaces,
                selectedWorkspaceId = libraryViewModel.selectedWorkspaceId,
                onWorkspaceSelected = libraryViewModel::selectWorkspace,
                onCreateWorkspace = {
                    designerViewModel.loadCanvas(libraryViewModel.createWorkspace())
                    navController.navigate(Routes.LayoutDesigner)
                },
                onEditWorkspace = { workspaceId ->
                    designerViewModel.loadCanvas(libraryViewModel.beginEditingWorkspace(workspaceId))
                    navController.navigate(Routes.LayoutDesigner)
                },
            )
        }
        composable(Routes.LayoutDesigner) {
            LayoutDesignerScreen(
                state = designerViewModel,
                isNewWorkspace = libraryViewModel.isCreatingWorkspace,
                onBack = navController::navigateUp,
                onOpenAppPicker = { cellId ->
                    navController.navigate("${Routes.AppPicker}/$cellId")
                },
                onSave = { name ->
                    libraryViewModel.saveWorkspace(designerViewModel.canvas, name)
                    navController.navigateUp()
                },
            )
        }
        composable("${Routes.AppPicker}/{cellId}") { backStackEntry ->
            val requestedCellId = backStackEntry.arguments?.getString("cellId")
            val validCellId = requestedCellId?.takeIf { cellId ->
                designerViewModel.canvas.cells.any { it.id == cellId }
            }
            AppPickerScreen(
                cellId = validCellId,
                onBack = navController::navigateUp,
                onAppSelected = { cellId, app ->
                    designerViewModel.assignApp(cellId, app)
                    navController.navigateUp()
                },
            )
        }
    }
}
