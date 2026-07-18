package com.trancong.dexworkspacetouch.workspace.designer.model

class WorkspaceCanvasEditor(
    private val validator: WorkspaceCanvasValidator = WorkspaceCanvasValidator(),
) {
    fun findMergeCandidates(
        canvas: WorkspaceCanvas,
        sourceCellId: String,
    ): List<WorkspaceMergeCandidate> {
        require(validator.validate(canvas).isEmpty()) { "canvas must be valid before finding merge candidates" }
        val source = canvas.cells.firstOrNull { it.id == sourceCellId }
            ?: throw IllegalArgumentException("Cell '$sourceCellId' does not exist")

        return canvas.cells.asSequence()
            .filter { it.id != sourceCellId }
            .mapNotNull { target -> mergeCandidate(canvas, source, target) }
            .distinctBy { it.targetCellId }
            .sortedWith(compareBy<WorkspaceMergeCandidate>({ it.direction.ordinal }, { it.targetCellId }))
            .toList()
    }

    fun mergeCells(
        canvas: WorkspaceCanvas,
        sourceCellId: String,
        targetCellId: String,
    ): WorkspaceMergeResult {
        val source = canvas.cells.firstOrNull { it.id == sourceCellId }
            ?: return WorkspaceMergeResult.Failure(WorkspaceMergeFailureReason.SOURCE_NOT_FOUND)
        val target = canvas.cells.firstOrNull { it.id == targetCellId }
            ?: return WorkspaceMergeResult.Failure(WorkspaceMergeFailureReason.TARGET_NOT_FOUND)
        if (sourceCellId == targetCellId) {
            return WorkspaceMergeResult.Failure(WorkspaceMergeFailureReason.SAME_CELL)
        }
        if (validator.validate(canvas).isNotEmpty()) {
            return WorkspaceMergeResult.Failure(WorkspaceMergeFailureReason.INVALID_RESULT)
        }

        val candidate = mergeCandidate(canvas, source, target)
            ?: return WorkspaceMergeResult.Failure(classifyInvalidPair(source.bounds, target.bounds))
        val mergedCell = target.copy(
            bounds = candidate.mergedBounds,
            app = target.app ?: source.app,
        )
        val result = WorkspaceCanvas(
            canvas.cells.mapNotNull { cell ->
                when (cell.id) {
                    sourceCellId -> null
                    targetCellId -> mergedCell
                    else -> cell
                }
            },
        )
        if (validator.validate(result).isNotEmpty()) {
            return WorkspaceMergeResult.Failure(WorkspaceMergeFailureReason.INVALID_RESULT)
        }
        return WorkspaceMergeResult.Success(result, candidate)
    }

    fun splitCell(
        canvas: WorkspaceCanvas,
        cellId: String,
        direction: SplitDirection,
        ratio: Float = 0.5f,
    ): WorkspaceCanvas {
        require(ratio in MIN_RATIO..MAX_RATIO) { "ratio must be in 0.2f..0.8f" }
        require(validator.validate(canvas).isEmpty()) { "canvas must be valid before splitting" }
        require(canvas.cells.size < WorkspaceLimits.MaxCells) {
            "workspace supports at most ${WorkspaceLimits.MaxCells} cells"
        }

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

    private fun mergeCandidate(
        canvas: WorkspaceCanvas,
        source: WorkspaceCell,
        target: WorkspaceCell,
    ): WorkspaceMergeCandidate? {
        val direction = mergeDirection(source.bounds, target.bounds) ?: return null
        val mergedBounds = NormalizedBounds(
            left = minOf(source.bounds.left, target.bounds.left),
            top = minOf(source.bounds.top, target.bounds.top),
            right = maxOf(source.bounds.right, target.bounds.right),
            bottom = maxOf(source.bounds.bottom, target.bounds.bottom),
        )
        val candidate = WorkspaceMergeCandidate(source.id, target.id, mergedBounds, direction)
        val otherCells = canvas.cells.filter { it.id != source.id && it.id != target.id }
        if (otherCells.any { mergedBounds.overlapsWithTolerance(it.bounds) }) return null
        return candidate
    }

    private fun mergeDirection(
        source: NormalizedBounds,
        target: NormalizedBounds,
    ): WorkspaceMergeDirection? {
        val sameVerticalSpan = close(source.top, target.top) && close(source.bottom, target.bottom)
        val sameHorizontalSpan = close(source.left, target.left) && close(source.right, target.right)
        return when {
            sameVerticalSpan && close(target.right, source.left) -> WorkspaceMergeDirection.LEFT
            sameVerticalSpan && close(source.right, target.left) -> WorkspaceMergeDirection.RIGHT
            sameHorizontalSpan && close(target.bottom, source.top) -> WorkspaceMergeDirection.UP
            sameHorizontalSpan && close(source.bottom, target.top) -> WorkspaceMergeDirection.DOWN
            else -> null
        }
    }

    private fun classifyInvalidPair(
        source: NormalizedBounds,
        target: NormalizedBounds,
    ): WorkspaceMergeFailureReason {
        val touches = close(source.right, target.left) || close(target.right, source.left) ||
            close(source.bottom, target.top) || close(target.bottom, source.top)
        return if (touches) WorkspaceMergeFailureReason.NON_RECTANGULAR_UNION
        else WorkspaceMergeFailureReason.NOT_ADJACENT
    }

    private fun NormalizedBounds.overlapsWithTolerance(other: NormalizedBounds): Boolean =
        left < other.right - GEOMETRY_TOLERANCE && right > other.left + GEOMETRY_TOLERANCE &&
            top < other.bottom - GEOMETRY_TOLERANCE && bottom > other.top + GEOMETRY_TOLERANCE

    private fun close(first: Float, second: Float): Boolean =
        kotlin.math.abs(first - second) <= GEOMETRY_TOLERANCE

    private companion object {
        const val MIN_RATIO = 0.2f
        const val MAX_RATIO = 0.8f
        const val GEOMETRY_TOLERANCE = 0.00001f
    }
}
