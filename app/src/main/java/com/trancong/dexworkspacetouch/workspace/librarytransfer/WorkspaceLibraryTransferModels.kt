package com.trancong.dexworkspacetouch.workspace.librarytransfer

import com.trancong.dexworkspacetouch.workspace.transfer.WorkspaceImportPayload

data class WorkspaceLibraryExport(
    val workspaces: List<WorkspaceImportPayload>,
    val exportedAtEpochMillis: Long,
)

data class WorkspaceLibraryImportPayload(val workspaces: List<WorkspaceImportPayload>)

data class WorkspaceLibraryImportPreview(
    val payload: WorkspaceLibraryImportPayload,
    val totalCellCount: Int,
    val totalAssignedAppCount: Int,
    val sampleWorkspaceNames: List<String>,
    val conflictingNameCount: Int,
) {
    val workspaceCount: Int get() = payload.workspaces.size
}

enum class WorkspaceLibraryTransferFailure {
    INVALID_FORMAT, UNSUPPORTED_VERSION, UNSUPPORTED_WORKSPACE_SCHEMA,
    INVALID_WORKSPACE, TOO_MANY_WORKSPACES, TOO_MANY_CELLS, FILE_TOO_LARGE,
    EMPTY_LIBRARY, READ_FAILURE, WRITE_FAILURE, ID_GENERATION_FAILURE,
}

class WorkspaceLibraryTransferException(
    val failure: WorkspaceLibraryTransferFailure,
    cause: Throwable? = null,
) : Exception(failure.name, cause)

object WorkspaceLibraryTransferFormat {
    const val Extension = "dwtbundle"
    const val MimeType = "application/vnd.dexworkspacetouch.library+json"
    const val FormatVersion = 1
    const val MaxWorkspaces = 100
    const val MaxTotalCells = 500
    const val MaxBytes = 2 * 1_048_576
}

interface WorkspaceLibraryBundleSerializer {
    fun encode(export: WorkspaceLibraryExport): ByteArray
    fun decode(bytes: ByteArray): WorkspaceLibraryImportPayload
}
