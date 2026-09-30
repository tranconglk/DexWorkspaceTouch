package com.trancong.dexworkspacetouch.workspace.execution.embedded.layout

import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlan
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class EmbeddedWorkspaceLayoutMapper {
    fun map(plan: EmbeddedWorkspacePlan, viewport: EmbeddedWorkspaceViewport): EmbeddedWorkspaceLayoutResult {
        if (viewport.widthPx <= 0 || viewport.heightPx <= 0) {
            return EmbeddedWorkspaceLayoutResult.Rejected(
                EmbeddedWorkspaceLayoutRejection.InvalidViewport(viewport.widthPx, viewport.heightPx),
            )
        }
        val items = plan.items.toList().sortedBy { it.order }
        val duplicateId = items.groupingBy { it.sourceCellId }.eachCount().entries.firstOrNull { it.value > 1 }?.key
        if (duplicateId != null) return EmbeddedWorkspaceLayoutResult.Rejected(
            EmbeddedWorkspaceLayoutRejection.DuplicateSourceCellId(duplicateId),
        )
        for (item in items) {
            if (!item.normalizedBounds.isValid()) return EmbeddedWorkspaceLayoutResult.Rejected(
                EmbeddedWorkspaceLayoutRejection.InvalidNormalizedBounds(item.sourceCellId),
            )
        }
        for (i in items.indices) for (j in i + 1 until items.size) {
            val a = items[i].normalizedBounds
            val b = items[j].normalizedBounds
            if (max(a.left, b.left) < min(a.right, b.right) &&
                max(a.top, b.top) < min(a.bottom, b.bottom)
            ) return EmbeddedWorkspaceLayoutResult.Rejected(
                EmbeddedWorkspaceLayoutRejection.UnsupportedOverlap(items[i].sourceCellId, items[j].sourceCellId),
            )
        }
        val panes = items.map { item ->
            val b = item.normalizedBounds
            val rect = PixelRect(
                edge(b.left, viewport.widthPx), edge(b.top, viewport.heightPx),
                edge(b.right, viewport.widthPx), edge(b.bottom, viewport.heightPx),
            )
            if (rect.width <= 0 || rect.height <= 0) return EmbeddedWorkspaceLayoutResult.Rejected(
                EmbeddedWorkspaceLayoutRejection.ZeroPixelPane(item.sourceCellId),
            )
            MappedPane(item.sourceCellId, item.order, item.packageName, item.componentName, b, rect)
        }
        val snapshot = plan.copy(items = plan.items.toList())
        return EmbeddedWorkspaceLayoutResult.Mapped(snapshot, viewport, panes)
    }

    private fun edge(value: Float, extent: Int): Int =
        floor(value.toDouble() * extent + 0.5).toInt().coerceIn(0, extent)

    private fun NormalizedBounds.isValid(): Boolean =
        left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f &&
            right > left && bottom > top
}
