package com.trancong.dexworkspacetouch.workspace.transfer

import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas

data class WorkspaceExport(val name: String, val canvas: WorkspaceCanvas, val exportedAtEpochMillis: Long)
data class WorkspaceImportPayload(val name: String, val canvas: WorkspaceCanvas, val workspaceSchemaVersion: Int)

enum class WorkspaceTransferFailure {
    INVALID_FORMAT, UNSUPPORTED_VERSION, INVALID_WORKSPACE, FILE_TOO_LARGE,
    READ_FAILURE, WRITE_FAILURE,
}

class WorkspaceTransferException(
    val failure: WorkspaceTransferFailure,
    cause: Throwable? = null,
) : Exception(failure.name, cause)

object WorkspaceTransferFormat {
    const val Extension = "dwt"
    const val MimeType = "application/vnd.dexworkspacetouch.workspace+json"
    const val MaxBytes = 1_048_576
    const val FormatVersion = 1
}

interface WorkspaceTransferSerializer {
    fun encode(export: WorkspaceExport): ByteArray
    fun decode(bytes: ByteArray): WorkspaceImportPayload
}
