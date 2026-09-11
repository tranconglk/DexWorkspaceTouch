package com.trancong.dexworkspacetouch.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.trancong.dexworkspacetouch.ui.design.Spacing
import java.net.URI

@Composable
fun AppUpdateScreen(currentVersion: String, state: AppUpdateUiState, onCheck: () -> Unit,
                    onOpenDownload: (String) -> Unit, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        Text("Cập nhật ứng dụng", style = MaterialTheme.typography.headlineMedium)
        Text("Phiên bản hiện tại: $currentVersion")
        AppUpdatePanel(state, onCheck, onOpenDownload)
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Quay lại") }
    }
}

@Composable
fun AppUpdatePanel(
    state: AppUpdateUiState,
    onCheck: () -> Unit,
    onOpenDownload: (String) -> Unit,
    modifier: Modifier = Modifier,
    checkEnabled: Boolean = true,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        when (state) {
            AppUpdateUiState.Idle -> Text("Chưa kiểm tra cập nhật.")
            AppUpdateUiState.Checking -> Text("Đang kiểm tra...")
            AppUpdateUiState.Current -> Text("Bạn đang dùng phiên bản mới nhất.")
            AppUpdateUiState.Unavailable -> Text("Thông tin cập nhật hiện không khả dụng. Ứng dụng vẫn hoạt động bình thường.")
            is AppUpdateUiState.Available -> {
                Text("Có phiên bản mới: ${state.update.versionName}")
                if (state.update.releaseNotes.isNotBlank()) Text(state.update.releaseNotes)
                validatedUpdateDownloadUrl(state)?.let { url ->
                    Button(onClick = { onOpenDownload(url) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Tải bản cập nhật")
                    }
                }
            }
        }
        OutlinedButton(onClick = onCheck, enabled = checkEnabled && state != AppUpdateUiState.Checking,
            modifier = Modifier.fillMaxWidth()) { Text("Kiểm tra cập nhật") }
    }
}

internal fun validatedUpdateDownloadUrl(state: AppUpdateUiState): String? {
    val value = (state as? AppUpdateUiState.Available)?.update?.apkUrl ?: return null
    return try {
        val uri = URI(value)
        value.takeIf { uri.scheme == "https" && !uri.host.isNullOrBlank() }
    } catch (_: Exception) {
        null
    }
}
