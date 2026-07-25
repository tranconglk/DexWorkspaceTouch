package com.trancong.dexworkspacetouch.workspace.library.ui

data class WorkspaceLibraryInteractionPolicy(
    val createEnabled: Boolean,
    val editEnabled: Boolean,
    val managementEnabled: Boolean,
    val exportEnabled: Boolean,
)

fun workspaceLibraryInteractionPolicy(
    libraryIsLoading: Boolean,
    libraryWriteInProgress: Boolean,
    transferOperationActive: Boolean,
): WorkspaceLibraryInteractionPolicy {
    val stableLibrary = !libraryWriteInProgress
    return WorkspaceLibraryInteractionPolicy(
        createEnabled = !libraryIsLoading && stableLibrary,
        editEnabled = stableLibrary,
        managementEnabled = stableLibrary,
        exportEnabled = stableLibrary && !transferOperationActive,
    )
}
