package com.trancong.dexworkspacetouch.workspace.library.state

enum class WorkspaceLibraryPersistenceOperation {
    LOAD,
    SAVE,
    RENAME,
    DELETE,
}

data class WorkspaceLibraryPersistenceError(
    val operation: WorkspaceLibraryPersistenceOperation,
) {
    val userMessage: String get() = when (operation) {
        WorkspaceLibraryPersistenceOperation.LOAD -> "Không thể đọc danh sách workspace."
        WorkspaceLibraryPersistenceOperation.SAVE -> "Không thể lưu workspace."
        WorkspaceLibraryPersistenceOperation.RENAME -> "Không thể đổi tên workspace."
        WorkspaceLibraryPersistenceOperation.DELETE -> "Không thể xóa workspace."
    }
}
