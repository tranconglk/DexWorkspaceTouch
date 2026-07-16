package com.trancong.dexworkspacetouch.workspace.designer.model

class WorkspaceCanvasEditor(
    private val validator: WorkspaceCanvasValidator = WorkspaceCanvasValidator(),
) {
    fun splitCell(
        canvas: WorkspaceCanvas,
        cellId: String,
        direction: SplitDirection,
        ratio: Float = 0.5f,
    ): WorkspaceCanvas {
        require(ratio in MIN_RATIO..MAX_RATIO) { "ratio must be in 0.2f..0.8f" }
        require(validator.validate(canvas).isEmpty()) { "canvas must be valid before splitting" }

        val splitIndex = canvas.cells.indexOfFirst { it.id == cellId }
        require(splitIndex >= 0) { "Cell '$cellId' does not exist" }
        val source = canvas.cells[splitIndex]
        val (firstBounds, secondBounds) = source.bounds.split(direction, ratio)
        val (firstId, secondId) = generatedIds(source.id, canvas.cells.mapTo(mutableSetOf()) { it.id })
        val replacement = listOf(
            WorkspaceCell(id = firstId, bounds = firstBounds, app = source.app),
            WorkspaceCell(id = secondId, bounds = secondBounds),
        )
        val result = WorkspaceCanvas(
            cells = canvas.cells.take(splitIndex) + replacement + canvas.cells.drop(splitIndex + 1),
        )
        check(validator.validate(result).isEmpty()) { "split produced an invalid canvas" }
        return result
    }

    fun resizeDivider(
        canvas: WorkspaceCanvas,
        dividerId: String,
        ratio: Float,
    ): WorkspaceCanvas {
        require(ratio in MIN_RATIO..MAX_RATIO) { "ratio must be in 0.2f..0.8f" }
        require(validator.validate(canvas).isEmpty()) { "canvas must be valid before resizing" }
        val divider = canvas.dividers().firstOrNull { it.id == dividerId }
            ?: throw IllegalArgumentException("Divider '$dividerId' does not exist")

        val cellsById = canvas.cells.associateBy(WorkspaceCell::id)
        val outerStart: Float
        val outerEnd: Float
        when (divider.direction) {
            SplitDirection.VERTICAL -> {
                outerStart = divider.firstCellIds.minOf { cellsById.getValue(it).bounds.left }
                outerEnd = divider.secondCellIds.maxOf { cellsById.getValue(it).bounds.right }
            }
            SplitDirection.HORIZONTAL -> {
                outerStart = divider.firstCellIds.minOf { cellsById.getValue(it).bounds.top }
                outerEnd = divider.secondCellIds.maxOf { cellsById.getValue(it).bounds.bottom }
            }
        }
        val position = outerStart + (outerEnd - outerStart) * ratio
        val result = WorkspaceCanvas(canvas.cells.map { cell ->
            when {
                cell.id in divider.firstCellIds -> cell.copy(
                    bounds = when (divider.direction) {
                        SplitDirection.VERTICAL -> cell.bounds.copy(right = position)
                        SplitDirection.HORIZONTAL -> cell.bounds.copy(bottom = position)
                    },
                )
                cell.id in divider.secondCellIds -> cell.copy(
                    bounds = when (divider.direction) {
                        SplitDirection.VERTICAL -> cell.bounds.copy(left = position)
                        SplitDirection.HORIZONTAL -> cell.bounds.copy(top = position)
                    },
                )
                else -> cell
            }
        })
        check(validator.validate(result).isEmpty()) { "resize produced an invalid canvas" }
        return result
    }

    private fun NormalizedBounds.split(
        direction: SplitDirection,
        ratio: Float,
    ): Pair<NormalizedBounds, NormalizedBounds> = when (direction) {
        SplitDirection.HORIZONTAL -> {
            val divider = top + height * ratio
            NormalizedBounds(left, top, right, divider) to
                NormalizedBounds(left, divider, right, bottom)
        }

        SplitDirection.VERTICAL -> {
            val divider = left + width * ratio
            NormalizedBounds(left, top, divider, bottom) to
                NormalizedBounds(divider, top, right, bottom)
        }
    }

    private fun generatedIds(sourceId: String, existingIds: Set<String>): Pair<String, String> {
        var stem = sourceId
        do {
            val first = "${stem}_a"
            val second = "${stem}_b"
            if (first !in existingIds && second !in existingIds) return first to second
            stem += "_a"
        } while (true)
    }

    private companion object {
        const val MIN_RATIO = 0.2f
        const val MAX_RATIO = 0.8f
    }
}
