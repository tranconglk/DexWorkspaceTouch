package com.trancong.dexworkspacetouch.workspace.designer.model

data class WorkspaceMergeCandidate(
    val sourceCellId: String,
    val targetCellId: String,
    val mergedBounds: NormalizedBounds,
    val direction: WorkspaceMergeDirection,
)

enum class WorkspaceMergeDirection {
    LEFT,
    RIGHT,
    UP,
    DOWN,
}

fun requiresMergeConfirmation(source: WorkspaceCell, target: WorkspaceCell): Boolean =
    source.app != null && target.app != null
