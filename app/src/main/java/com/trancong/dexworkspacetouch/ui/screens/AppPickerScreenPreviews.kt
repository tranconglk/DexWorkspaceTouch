package com.trancong.dexworkspacetouch.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trancong.dexworkspacetouch.ui.theme.DexWorkspaceTouchTheme
import com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledApp
import com.trancong.dexworkspacetouch.workspace.apppicker.model.ListInstalledAppCatalog
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.AppPickerViewModel
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.AppIconLoader
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.AppIconState
import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity

@Preview(showBackground = true, widthDp = 720, heightDp = 900)
@Composable
private fun NarrowAppPickerPreview() = AppPickerPreview()

@Preview(showBackground = true, widthDp = 1120, heightDp = 900)
@Composable
private fun MediumAppPickerPreview() = AppPickerPreview()

@Preview(showBackground = true, widthDp = 1920, heightDp = 1000)
@Composable
private fun WideAppPickerPreview() = AppPickerPreview()

@Composable
private fun AppPickerPreview() {
    val catalog = ListInstalledAppCatalog(previewApps)
    val state: AppPickerViewModel = viewModel(
        factory = AppPickerViewModel.factory(
            catalog = catalog,
            iconLoader = PreviewIconLoader,
            selectedIdentity = previewApps.first().identity,
        ),
    )
    DexWorkspaceTouchTheme(darkTheme = false) {
        AppPickerScreen(
            cellId = "preview-cell",
            state = state,
            onBack = {},
            onAppSelected = { _, _ -> },
        )
    }
}

private object PreviewIconLoader : AppIconLoader {
    override fun loadIcon(identity: AppIdentity): AppIconState = AppIconState.Fallback
}

private val previewApps = List(12) { index ->
    InstalledApp(
        packageName = "preview.app.$index",
        activityName = "preview.app.$index.MainActivity",
        label = "Ứng dụng ${index + 1}",
        launchable = true,
        isSystemApp = index % 3 == 0,
    )
}
