package com.trancong.dexworkspacetouch.workspace.launcher.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets
import com.trancong.dexworkspacetouch.ui.design.DwtStatusSurface
import com.trancong.dexworkspacetouch.ui.design.DwtStatusTone
import com.trancong.dexworkspacetouch.workspace.launcher.model.LaunchReadiness

@Composable
fun WorkspaceLaunchStatusDialog(
    state: WorkspaceLaunchUiState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        WorkspaceLaunchUiState.Idle -> Unit
        is WorkspaceLaunchUiState.Checking -> StatusDialog(
            title = "Đang kiểm tra workspace...", showProgress = true,
            tone = DwtStatusTone.Info, action = "Hủy", onAction = onCancel,
        )
        is WorkspaceLaunchUiState.Launching -> StatusDialog(
            title = "Đang mở workspace ${state.workspaceName}...", showProgress = true,
            tone = DwtStatusTone.Info, action = "Hủy", onAction = onCancel,
        )
        is WorkspaceLaunchUiState.Completed -> StatusDialog(
            title = state.summaryMessage(),
            details = state.failures().map { failure ->
                "${failure.target.identity.packageName}: ${failure.reason.userMessage()}"
            },
            tone = if (state.failures().isEmpty()) DwtStatusTone.Success else DwtStatusTone.Warning,
            action = "Đóng", onAction = onDismiss,
        )
        is WorkspaceLaunchUiState.ReadinessError -> StatusDialog(
            title = state.readiness.userMessage(),
            details = state.readiness.applicationDetails(),
            tone = DwtStatusTone.Warning, action = "Đã hiểu", onAction = onDismiss,
        )
        is WorkspaceLaunchUiState.LaunchError -> StatusDialog(
            title = state.reason.title(), details = state.reason.details(),
            tone = DwtStatusTone.Error, action = "Đã hiểu", onAction = onDismiss,
        )
        is WorkspaceLaunchUiState.Cancelled -> StatusDialog(
            title = "Đã hủy mở workspace ${state.workspaceName}.",
            details = listOf("Các ứng dụng đã mở trước đó không bị đóng."),
            tone = DwtStatusTone.Warning, action = "Đóng", onAction = onDismiss,
        )
    }
}

@Composable
private fun StatusDialog(
    title: String,
    details: List<String> = emptyList(),
    showProgress: Boolean = false,
    tone: DwtStatusTone,
    action: String,
    onAction: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!showProgress) onAction() },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                DwtStatusSurface(
                    tone = tone,
                    title = title,
                    trailingContent = if (showProgress) ({ CircularProgressIndicator() }) else null,
                )
                details.forEach { Text(it) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onAction,
                modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
            ) { Text(action) }
        },
    )
}

private fun LaunchReadiness.applicationDetails(): List<String> = when (this) {
    is LaunchReadiness.MissingApplications -> items.map { it.identity.packageName }
    is LaunchReadiness.NonLaunchableApplications -> items.map { it.identity.packageName }
    is LaunchReadiness.TooManyTargets -> emptyList()
    else -> emptyList()
}

private fun LaunchEnvironmentFailure.title(): String = when (this) {
    LaunchEnvironmentFailure.HOST_NOT_EXTERNAL -> "Hãy mở DexWorkspaceTouch trên màn hình DeX."
    LaunchEnvironmentFailure.WORK_AREA_UNAVAILABLE -> "Màn hình DeX không còn khả dụng."
    LaunchEnvironmentFailure.LEGACY_WORK_AREA_UNAVAILABLE -> "Chưa xác định được vùng làm việc DeX."
}

private fun LaunchEnvironmentFailure.details(): List<String> = when (this) {
    LaunchEnvironmentFailure.LEGACY_WORK_AREA_UNAVAILABLE -> listOf(
        "Hãy phóng to DexWorkspaceTouch một lần để nhận diện vùng hiển thị, " +
            "sau đó bạn có thể thu nhỏ và mở workspace bình thường.",
    )
    else -> emptyList()
}
