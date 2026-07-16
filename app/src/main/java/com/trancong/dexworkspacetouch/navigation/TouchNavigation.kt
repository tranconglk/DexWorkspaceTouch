package com.trancong.dexworkspacetouch.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.trancong.dexworkspacetouch.ui.screens.AppPickerScreen
import com.trancong.dexworkspacetouch.ui.screens.HomeScreen
import com.trancong.dexworkspacetouch.ui.screens.LayoutDesignerScreen
import com.trancong.dexworkspacetouch.workspace.designer.state.WorkspaceDesignerStateHolder
import com.trancong.dexworkspacetouch.workspace.designer.ui.WorkspaceCanvasPreviewData

private object Routes {
    const val Home = "home"
    const val LayoutDesigner = "layout-designer"
    const val AppPicker = "app-picker"
}

@Composable
fun TouchNavigation() {
    val navController = rememberNavController()
    val designerState = remember {
        WorkspaceDesignerStateHolder(WorkspaceCanvasPreviewData.threeCellsCanvas())
    }
    NavHost(navController = navController, startDestination = Routes.Home) {
        composable(Routes.Home) {
            HomeScreen(
                onOpenLayoutDesigner = { navController.navigate(Routes.LayoutDesigner) },
                onOpenAppPicker = { navController.navigate("${Routes.AppPicker}/missing") },
            )
        }
        composable(Routes.LayoutDesigner) {
            LayoutDesignerScreen(
                state = designerState,
                onBack = navController::navigateUp,
                onOpenAppPicker = { cellId ->
                    navController.navigate("${Routes.AppPicker}/$cellId")
                },
            )
        }
        composable("${Routes.AppPicker}/{cellId}") { backStackEntry ->
            val requestedCellId = backStackEntry.arguments?.getString("cellId")
            val validCellId = requestedCellId?.takeIf { cellId ->
                designerState.canvas.cells.any { it.id == cellId }
            }
            AppPickerScreen(
                cellId = validCellId,
                onBack = navController::navigateUp,
                onAppSelected = { cellId, app ->
                    designerState.assignApp(cellId, app)
                    navController.navigateUp()
                },
            )
        }
    }
}
