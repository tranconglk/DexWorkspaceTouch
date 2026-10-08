package com.trancong.dexworkspacetouch.platform.launch.shizuku

enum class ShizukuRuntimeState {
    READY, PERMISSION_MISSING, NOT_RUNNING, UNAVAILABLE;

    fun userMessage(): String = when (this) {
        READY -> "Shizuku sẵn sàng"
        PERMISSION_MISSING -> "Shizuku chưa được cấp quyền. Cấp quyền trong Shizuku rồi bấm Repair lại."
        NOT_RUNNING -> "Shizuku chưa chạy. Tự động sửa sẽ hoạt động lại khi Shizuku sẵn sàng."
        UNAVAILABLE -> "Shizuku không khả dụng cho Workspace Repair."
    }
}

internal fun CommandTransportFailure?.toShizukuRuntimeState(): ShizukuRuntimeState = when (this) {
    null -> ShizukuRuntimeState.READY
    CommandTransportFailure.PERMISSION_DENIED -> ShizukuRuntimeState.PERMISSION_MISSING
    CommandTransportFailure.SHIZUKU_UNAVAILABLE -> ShizukuRuntimeState.NOT_RUNNING
    else -> ShizukuRuntimeState.UNAVAILABLE
}
