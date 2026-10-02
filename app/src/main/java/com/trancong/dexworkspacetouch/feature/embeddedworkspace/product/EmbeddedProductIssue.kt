package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import com.trancong.dexworkspacetouch.workspace.launcher.model.LaunchReadiness

sealed interface EmbeddedProductIssue {
    val sourceCellId: String? get() = null

    data class MissingWorkspace(val workspaceId: String) : EmbeddedProductIssue
    data class LaunchNotReady(val reason: LaunchReadiness) : EmbeddedProductIssue
    data object UnsupportedLayout : EmbeddedProductIssue
    data class UnsupportedEmbeddedItemCount(val count: Int) : EmbeddedProductIssue
    data class DuplicateTarget(override val sourceCellId: String? = null) : EmbeddedProductIssue
    data object UnsupportedPlatform : EmbeddedProductIssue
    data class GeometryUnavailable(override val sourceCellId: String? = null) : EmbeddedProductIssue
    data object ShizukuUnavailable : EmbeddedProductIssue
    data object ShizukuPermissionMissing : EmbeddedProductIssue
    data class RendererNotReady(override val sourceCellId: String? = null) : EmbeddedProductIssue
    data class RuntimeStartFailed(override val sourceCellId: String?) : EmbeddedProductIssue
    data class SurfaceLost(override val sourceCellId: String?) : EmbeddedProductIssue
    data class RemoteDied(override val sourceCellId: String?) : EmbeddedProductIssue
    data class RuntimeCleanupIncomplete(override val sourceCellId: String?) : EmbeddedProductIssue
    data class CleanupOutcomeUncertain(override val sourceCellId: String? = null) : EmbeddedProductIssue

    val code: String get() = when (this) {
        is MissingWorkspace -> "MissingWorkspace"
        is LaunchNotReady -> "LaunchNotReady"
        UnsupportedLayout -> "UnsupportedLayout"
        is UnsupportedEmbeddedItemCount -> "UnsupportedEmbeddedItemCount"
        is DuplicateTarget -> "DuplicateTarget"
        UnsupportedPlatform -> "UnsupportedPlatform"
        is GeometryUnavailable -> "GeometryUnavailable"
        ShizukuUnavailable -> "ShizukuUnavailable"
        ShizukuPermissionMissing -> "ShizukuPermissionMissing"
        is RendererNotReady -> "RendererNotReady"
        is RuntimeStartFailed -> "RuntimeStartFailed"
        is SurfaceLost -> "SurfaceLost"
        is RemoteDied -> "RemoteDied"
        is RuntimeCleanupIncomplete -> "RuntimeCleanupIncomplete"
        is CleanupOutcomeUncertain -> "CleanupOutcomeUncertain"
    }
}

fun EmbeddedProductIssue.message(): String = when (this) {
    is EmbeddedProductIssue.MissingWorkspace -> "Không tìm thấy Workspace đã chọn."
    is EmbeddedProductIssue.LaunchNotReady -> "Workspace chưa sẵn sàng; hãy kiểm tra bố cục và ứng dụng."
    EmbeddedProductIssue.UnsupportedLayout -> "Bố cục Workspace chưa được Embedded hỗ trợ."
    is EmbeddedProductIssue.UnsupportedEmbeddedItemCount -> "Embedded thử nghiệm hỗ trợ 1–2 ứng dụng; hiện có $count."
    is EmbeddedProductIssue.DuplicateTarget -> "Embedded chưa hỗ trợ mở trùng ứng dụng/hoạt động."
    EmbeddedProductIssue.UnsupportedPlatform -> "Thiết bị hiện chưa đáp ứng điều kiện chạy Embedded thử nghiệm."
    is EmbeddedProductIssue.GeometryUnavailable -> if (sourceCellId == null) {
        "Không xác định được kích thước màn hình khách."
    } else "Không xác định được kích thước cho ô $sourceCellId."
    EmbeddedProductIssue.ShizukuUnavailable -> "Shizuku chưa chạy hoặc chưa kết nối."
    EmbeddedProductIssue.ShizukuPermissionMissing -> "Chưa cấp quyền Shizuku cho Embedded."
    is EmbeddedProductIssue.RendererNotReady -> "Vùng hiển thị Embedded chưa sẵn sàng."
    is EmbeddedProductIssue.RuntimeStartFailed -> "Không thể khởi động Embedded."
    is EmbeddedProductIssue.SurfaceLost -> "Vùng hiển thị Embedded đã mất; phiên đang kết thúc."
    is EmbeddedProductIssue.RemoteDied -> "Kết nối phiên Embedded đã mất."
    is EmbeddedProductIssue.RuntimeCleanupIncomplete -> "Dọn Embedded chưa hoàn tất."
    is EmbeddedProductIssue.CleanupOutcomeUncertain -> "Chưa xác nhận được kết quả dọn Embedded."
}
