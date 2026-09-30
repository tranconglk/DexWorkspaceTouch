package com.trancong.dexworkspacetouch.workspace.execution.embedded.layout

import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlan

data class EmbeddedWorkspaceViewport(val widthPx: Int, val heightPx: Int)

data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

data class MappedPane(
    val sourceCellId: String,
    val order: Int,
    val packageName: String,
    val componentName: String,
    val normalizedBounds: NormalizedBounds,
    val pixelBounds: PixelRect,
)

sealed interface EmbeddedWorkspaceLayoutResult {
    data class Mapped(
        val planSnapshot: EmbeddedWorkspacePlan,
        val viewport: EmbeddedWorkspaceViewport,
        val panes: List<MappedPane>,
    ) : EmbeddedWorkspaceLayoutResult

    data class Rejected(val cause: EmbeddedWorkspaceLayoutRejection) : EmbeddedWorkspaceLayoutResult
}

sealed interface EmbeddedWorkspaceLayoutRejection {
    data class InvalidViewport(val widthPx: Int, val heightPx: Int) : EmbeddedWorkspaceLayoutRejection
    data class InvalidNormalizedBounds(val sourceCellId: String) : EmbeddedWorkspaceLayoutRejection
    data class ZeroPixelPane(val sourceCellId: String) : EmbeddedWorkspaceLayoutRejection
    data class DuplicateSourceCellId(val sourceCellId: String) : EmbeddedWorkspaceLayoutRejection
    data class UnsupportedOverlap(val firstCellId: String, val secondCellId: String) : EmbeddedWorkspaceLayoutRejection
    data class MissingPlanItemCorrelation(val sourceCellId: String) : EmbeddedWorkspaceLayoutRejection
}
