package com.trancong.dexworkspacetouch.workspace.launcher.presentation

import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchFailure
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchFailureReason
import com.trancong.dexworkspacetouch.workspace.launcher.model.LaunchReadiness
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchResult

fun LaunchReadiness.userMessage(): String = when (this) {
    LaunchReadiness.EmptyWorkspace -> "Workspace chưa có ứng dụng."
    is LaunchReadiness.TooManyTargets -> "Workspace hỗ trợ tối đa $maximum ứng dụng."
    is LaunchReadiness.EmptyCells -> "Workspace còn ô chưa chọn ứng dụng."
    is LaunchReadiness.MissingApplications -> "Một số ứng dụng không còn trên thiết bị."
    is LaunchReadiness.NonLaunchableApplications -> "Một số ứng dụng không thể mở."
    is LaunchReadiness.InvalidCanvas -> "Bố cục workspace không hợp lệ."
    is LaunchReadiness.Ready -> error("Ready has no error message")
}

fun AppLaunchFailureReason.userMessage(): String = when (this) {
    AppLaunchFailureReason.APP_NOT_FOUND -> "Ứng dụng không còn được cài đặt."
    AppLaunchFailureReason.ACTIVITY_NOT_FOUND -> "Không tìm thấy màn hình khởi động."
    AppLaunchFailureReason.DISPLAY_UNAVAILABLE -> "Màn hình DeX không còn khả dụng."
    AppLaunchFailureReason.SECURITY_RESTRICTION -> "Hệ thống không cho phép mở ứng dụng."
    AppLaunchFailureReason.LAUNCH_REJECTED -> "Không thể đặt cửa sổ vào vùng đã chọn."
    AppLaunchFailureReason.UNKNOWN -> "Đã xảy ra lỗi không xác định."
}

fun WorkspaceLaunchUiState.Completed.summaryMessage(): String = when (val value = result) {
    is WorkspaceLaunchResult.Success -> "Đã gửi yêu cầu mở ${value.launchedTargets.size} ứng dụng."
    is WorkspaceLaunchResult.PartialSuccess ->
        "Đã mở ${value.launchedTargets.size}/$totalTargets ứng dụng."
    is WorkspaceLaunchResult.Failure -> "Không thể mở workspace."
}

fun WorkspaceLaunchUiState.Completed.failures(): List<AppLaunchFailure> = when (val value = result) {
    is WorkspaceLaunchResult.Success -> emptyList()
    is WorkspaceLaunchResult.PartialSuccess -> value.failedTargets
    is WorkspaceLaunchResult.Failure -> value.failures
}.sortedBy { it.target.order }
