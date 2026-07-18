package com.trancong.dexworkspacetouch.workspace.library.state

enum class WorkspaceLibraryPersistenceOperation {
    LOAD,
    SAVE,
    RENAME,
    DUPLICATE,
    PIN,
    UNPIN,
    DELETE,
}

data class WorkspaceLibraryPersistenceError(
    val operation: WorkspaceLibraryPersistenceOperation,
) {
    val userMessage: String get() = when (operation) {
        WorkspaceLibraryPersistenceOperation.LOAD -> "Không thể đọc danh sách workspace."
        WorkspaceLibraryPersistenceOperation.SAVE -> "Không thể lưu workspace."
        WorkspaceLibraryPersistenceOperation.RENAME -> "Không thể đổi tên workspace."
        WorkspaceLibraryPersistenceOperation.DUPLICATE -> "Không thể nhân bản workspace."
        WorkspaceLibraryPersistenceOperation.PIN -> "Không thể ghim workspace."
        WorkspaceLibraryPersistenceOperation.UNPIN -> "Không thể bỏ ghim workspace."
        WorkspaceLibraryPersistenceOperation.DELETE -> "Không thể xóa workspace."
    }
}

sealed interface WorkspaceDuplicateFeedback {
    data class Success(val workspaceName: String) : WorkspaceDuplicateFeedback
    data object Failure : WorkspaceDuplicateFeedback
}

sealed interface WorkspacePinFeedback {
    data class Success(val workspaceName: String, val isPinned: Boolean) : WorkspacePinFeedback
    data class Failure(val attemptedPinned: Boolean) : WorkspacePinFeedback
}
