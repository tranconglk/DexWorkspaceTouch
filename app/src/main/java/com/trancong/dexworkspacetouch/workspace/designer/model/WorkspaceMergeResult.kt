package com.trancong.dexworkspacetouch.workspace.designer.model

enum class WorkspaceMergeFailureReason {
    SOURCE_NOT_FOUND,
    TARGET_NOT_FOUND,
    SAME_CELL,
    NOT_ADJACENT,
    NON_RECTANGULAR_UNION,
    INVALID_RESULT,
}

sealed interface WorkspaceMergeResult {
    data class Success(
        val canvas: WorkspaceCanvas,
        val candidate: WorkspaceMergeCandidate,
    ) : WorkspaceMergeResult

    data class Failure(val reason: WorkspaceMergeFailureReason) : WorkspaceMergeResult
}
