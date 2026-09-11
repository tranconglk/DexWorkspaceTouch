package com.trancong.dexworkspacetouch.update

import com.trancong.dexworkspacetouch.ui.design.DwtStatusTone

internal data class AppUpdatePresentation(
    val tone: DwtStatusTone,
    val title: String,
    val supportingText: String,
    val showProgress: Boolean = false,
    val showDownloadAction: Boolean = false,
)

internal fun AppUpdateUiState.presentation(): AppUpdatePresentation = when (this) {
    AppUpdateUiState.Idle -> AppUpdatePresentation(
        DwtStatusTone.Info, "Sẵn sàng kiểm tra",
        "Kiểm tra nguồn phát hành chính thức để tìm phiên bản mới.",
    )
    AppUpdateUiState.Checking -> AppUpdatePresentation(
        DwtStatusTone.Info, "Đang kiểm tra cập nhật",
        "Ứng dụng đang xác minh thông tin bản phát hành.", showProgress = true,
    )
    AppUpdateUiState.Current -> AppUpdatePresentation(
        DwtStatusTone.Success, "Đã cập nhật", "Bạn đang dùng phiên bản mới nhất.",
    )
    AppUpdateUiState.Unavailable -> AppUpdatePresentation(
        DwtStatusTone.Warning, "Chưa thể kiểm tra",
        "Thông tin cập nhật hiện không khả dụng. Ứng dụng vẫn hoạt động bình thường.",
    )
    is AppUpdateUiState.Available -> AppUpdatePresentation(
        DwtStatusTone.Info, "Có phiên bản mới ${update.versionName}",
        "Bản cập nhật đã sẵn sàng từ nguồn phát hành chính thức.",
        showDownloadAction = validatedUpdateDownloadUrl(this) != null,
    )
}
