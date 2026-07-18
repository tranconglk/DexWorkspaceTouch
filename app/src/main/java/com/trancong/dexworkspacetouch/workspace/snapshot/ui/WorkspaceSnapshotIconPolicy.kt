package com.trancong.dexworkspacetouch.workspace.snapshot.ui

import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell

internal enum class SnapshotIconSizeClass { SMALL, MEDIUM, LARGE }

internal fun snapshotIconSizeClass(widthDp: Float, heightDp: Float): SnapshotIconSizeClass = when {
    widthDp >= 96f && heightDp >= 56f -> SnapshotIconSizeClass.LARGE
    heightDp >= 36f -> SnapshotIconSizeClass.MEDIUM
    else -> SnapshotIconSizeClass.SMALL
}

internal fun WorkspaceCell.shouldLoadSnapshotIcon(hasLoader: Boolean): Boolean =
    hasLoader && app != null
