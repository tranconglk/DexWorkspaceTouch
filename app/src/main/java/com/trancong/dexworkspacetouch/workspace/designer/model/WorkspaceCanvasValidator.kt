package com.trancong.dexworkspacetouch.workspace.designer.model

sealed interface CanvasValidationIssue {
    data object EmptyCanvas : CanvasValidationIssue
    data class DuplicateCellId(val cellId: String) : CanvasValidationIssue
    data class OutOfBounds(val cellId: String) : CanvasValidationIssue
    data class InvalidBounds(val cellId: String) : CanvasValidationIssue
    data class OverlappingCells(val firstCellId: String, val secondCellId: String) : CanvasValidationIssue
}

class WorkspaceCanvasValidator {
    fun validate(canvas: WorkspaceCanvas): List<CanvasValidationIssue> {
        val issues = mutableListOf<CanvasValidationIssue>()
        if (canvas.cells.isEmpty()) issues += CanvasValidationIssue.EmptyCanvas

        canvas.cells
            .groupingBy(WorkspaceCell::id)
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .forEach { issues += CanvasValidationIssue.DuplicateCellId(it) }

        canvas.cells.forEach { cell ->
            val bounds = cell.bounds
            val finite = bounds.left.isFinite() && bounds.top.isFinite() &&
                bounds.right.isFinite() && bounds.bottom.isFinite()
            if (!finite || bounds.right <= bounds.left || bounds.bottom <= bounds.top) {
                issues += CanvasValidationIssue.InvalidBounds(cell.id)
            }
            if (!finite || bounds.left < 0f || bounds.top < 0f ||
                bounds.right > 1f || bounds.bottom > 1f) {
                issues += CanvasValidationIssue.OutOfBounds(cell.id)
            }
        }

        for (firstIndex in canvas.cells.indices) {
            for (secondIndex in firstIndex + 1 until canvas.cells.size) {
                val first = canvas.cells[firstIndex]
                val second = canvas.cells[secondIndex]
                if (first.bounds.overlaps(second.bounds)) {
                    issues += CanvasValidationIssue.OverlappingCells(first.id, second.id)
                }
            }
        }
        return issues
    }

    private fun NormalizedBounds.overlaps(other: NormalizedBounds): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top
}
