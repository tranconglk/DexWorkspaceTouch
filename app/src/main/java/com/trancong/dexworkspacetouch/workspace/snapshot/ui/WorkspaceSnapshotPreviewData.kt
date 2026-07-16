package com.trancong.dexworkspacetouch.workspace.snapshot.ui

import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell

internal object WorkspaceSnapshotDemoData {
    private fun app(name: String) = AssignedApp("demo.${name.lowercase()}", "demo.MainActivity", name)

    fun empty() = WorkspaceCanvas.singleCell()

    fun two() = WorkspaceCanvas(listOf(
        WorkspaceCell("left", NormalizedBounds(0f, 0f, 0.5f, 1f), app("Maps")),
        WorkspaceCell("right", NormalizedBounds(0.5f, 0f, 1f, 1f), app("Waze")),
    ))

    fun three() = WorkspaceCanvas(listOf(
        WorkspaceCell("left", NormalizedBounds(0f, 0f, 0.5f, 1f), app("Maps")),
        WorkspaceCell("top", NormalizedBounds(0.5f, 0f, 1f, 0.5f), app("Waze")),
        WorkspaceCell("bottom", NormalizedBounds(0.5f, 0.5f, 1f, 1f)),
    ))

    fun four() = WorkspaceCanvas(listOf(
        WorkspaceCell("a", NormalizedBounds(0f, 0f, 0.5f, 0.5f), app("Maps")),
        WorkspaceCell("b", NormalizedBounds(0.5f, 0f, 1f, 0.5f), app("Waze")),
        WorkspaceCell("c", NormalizedBounds(0f, 0.5f, 0.5f, 1f), app("Notes")),
        WorkspaceCell("d", NormalizedBounds(0.5f, 0.5f, 1f, 1f)),
    ))
}
