package com.trancong.dexworkspacetouch.workspace.designer.state

import com.trancong.dexworkspacetouch.workspace.designer.model.SplitDirection
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceLimits
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvasEditor
import com.trancong.dexworkspacetouch.workspace.designer.model.dividers
import kotlin.math.roundToInt

data class DesignerContextToolbarState(
    val canUndo: Boolean,
    val canRedo: Boolean,
    val summary: WorkspaceDesignerSummary,
    val context: Context,
) {
    sealed interface Context {
        data object None : Context

        data class Cell(
            val cellId: String,
            val canSplitHorizontal: Boolean,
            val canSplitVertical: Boolean,
            val maximumCellsReached: Boolean,
            val canMerge: Boolean,
        ) : Context

        data class Divider(
            val dividerId: String,
            val direction: SplitDirection,
            val ratio: Float,
        ) : Context {
            val canDecrease: Boolean get() = ratio > MIN_RATIO
            val canIncrease: Boolean get() = ratio < MAX_RATIO
            val ratioText: String get() = "${(ratio * 100f).roundToInt()}%"
            val statusText: String get() =
                "Đường chia ${if (direction == SplitDirection.VERTICAL) "dọc" else "ngang"} • $ratioText"
        }
    }

    companion object {
        fun from(
            canvas: WorkspaceCanvas,
            selectedCellId: String?,
            selectedDividerId: String?,
            canUndo: Boolean,
            canRedo: Boolean,
        ): DesignerContextToolbarState {
            val maximumReached = canvas.cells.size >= WorkspaceLimits.MaxCells
            val context = selectedDividerId
                ?.let { id -> canvas.dividers().firstOrNull { it.id == id } }
                ?.let { divider ->
                    Context.Divider(divider.id, divider.direction, divider.ratio)
                }
                ?: selectedCellId
                    ?.let { id -> canvas.cells.firstOrNull { it.id == id } }
                    ?.let { cell ->
                        Context.Cell(
                            cellId = cell.id,
                            canSplitHorizontal = !maximumReached && cell.bounds.height / 2f >= MIN_CHILD_RATIO,
                            canSplitVertical = !maximumReached && cell.bounds.width / 2f >= MIN_CHILD_RATIO,
                            maximumCellsReached = maximumReached,
                            canMerge = WorkspaceCanvasEditor().findMergeCandidates(canvas, cell.id).isNotEmpty(),
                        )
                    }
                ?: Context.None
            return DesignerContextToolbarState(
                canUndo = canUndo,
                canRedo = canRedo,
                summary = WorkspaceDesignerSummary.from(canvas),
                context = context,
            )
        }

        private const val MIN_CHILD_RATIO = 0.2f
        private const val MIN_RATIO = 0.2f
        private const val MAX_RATIO = 0.8f
    }
}

enum class DesignerToolbarLayoutPolicy { WIDE, NARROW }

fun designerToolbarLayoutPolicy(widthDp: Float, wideBreakpointDp: Float): DesignerToolbarLayoutPolicy {
    require(widthDp >= 0f) { "widthDp must not be negative" }
    require(wideBreakpointDp > 0f) { "wideBreakpointDp must be positive" }
    return if (widthDp >= wideBreakpointDp) DesignerToolbarLayoutPolicy.WIDE
    else DesignerToolbarLayoutPolicy.NARROW
}
