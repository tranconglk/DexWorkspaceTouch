package com.trancong.dexworkspacetouch.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.trancong.dexworkspacetouch.ui.design.DesignerShapes
import com.trancong.dexworkspacetouch.ui.design.Dimensions
import com.trancong.dexworkspacetouch.ui.design.DwtStatusSurface
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets
import java.net.URI

@Composable
fun AppUpdateScreen(
    currentVersion: String,
    state: AppUpdateUiState,
    onCheck: () -> Unit,
    onOpenDownload: (String) -> Unit,
    onBack: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
      Box(modifier = Modifier.fillMaxSize().padding(Spacing.L), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = Dimensions.UpdateContentMaxWidth).fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.L),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    Text("Cập nhật ứng dụng", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Bản phát hành DexWorkspaceTouch",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
                ) { Text("Quay lại") }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = DesignerShapes.Workspace,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.L),
                    verticalArrangement = Arrangement.spacedBy(Spacing.M),
                ) {
                    Text("DexWorkspaceTouch", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Phiên bản hiện tại: $currentVersion",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    AppUpdatePanel(state, onCheck, onOpenDownload)
                }
            }
        }
      }
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
    val presentation = state.presentation()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        DwtStatusSurface(
            tone = presentation.tone,
            title = presentation.title,
            supportingText = presentation.supportingText,
            trailingContent = if (presentation.showProgress) {
                {
                    CircularProgressIndicator(
                        modifier = Modifier.semantics { contentDescription = "Đang kiểm tra cập nhật" },
                    )
                }
            } else null,
        )
        if (state is AppUpdateUiState.Available && state.update.releaseNotes.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Text("Thông tin bản phát hành", style = MaterialTheme.typography.titleMedium)
                Text(
                    state.update.releaseNotes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth < 480.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    UpdateActions(state, presentation, onCheck, onOpenDownload, checkEnabled, Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S, Alignment.End),
                ) {
                    UpdateActions(state, presentation, onCheck, onOpenDownload, checkEnabled, Modifier)
                }
            }
        }
    }
}

@Composable
private fun UpdateActions(
    state: AppUpdateUiState,
    presentation: AppUpdatePresentation,
    onCheck: () -> Unit,
    onOpenDownload: (String) -> Unit,
    checkEnabled: Boolean,
    modifier: Modifier,
) {
    OutlinedButton(
        onClick = onCheck,
        enabled = checkEnabled && state != AppUpdateUiState.Checking,
        modifier = modifier.heightIn(min = TouchTargets.SecondaryButton),
    ) { Text(if (state == AppUpdateUiState.Checking) "Đang kiểm tra…" else "Kiểm tra lại") }
    if (presentation.showDownloadAction) {
        validatedUpdateDownloadUrl(state)?.let { url ->
            Button(
                onClick = { onOpenDownload(url) },
                modifier = modifier.heightIn(min = TouchTargets.SecondaryButton),
            ) { Text("Tải bản cập nhật") }
        }
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
