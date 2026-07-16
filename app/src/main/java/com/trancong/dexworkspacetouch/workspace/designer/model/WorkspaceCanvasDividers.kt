package com.trancong.dexworkspacetouch.workspace.designer.model

fun WorkspaceCanvas.dividers(): List<WorkspaceDivider> {
    val segments = buildList {
        cells.forEachIndexed { firstIndex, first ->
            cells.drop(firstIndex + 1).forEach { second ->
                addVerticalSegment(first, second)?.let(::add)
                addHorizontalSegment(first, second)?.let(::add)
            }
        }
    }

    return segments
        .groupBy { it.direction to it.position }
        .values
        .flatMap(::connectedGroups)
        .map { group -> group.toDivider(cells) }
        .sortedWith(compareBy(WorkspaceDivider::direction, WorkspaceDivider::position, WorkspaceDivider::start))
}

private data class DividerSegment(
    val direction: SplitDirection,
    val position: Float,
    val start: Float,
    val end: Float,
    val firstCellId: String,
    val secondCellId: String,
)

private fun addVerticalSegment(
    first: WorkspaceCell,
    second: WorkspaceCell,
): DividerSegment? {
    val (left, right) = when {
        first.bounds.right == second.bounds.left -> first to second
        second.bounds.right == first.bounds.left -> second to first
        else -> return null
    }
    val start = maxOf(left.bounds.top, right.bounds.top)
    val end = minOf(left.bounds.bottom, right.bounds.bottom)
    if (end <= start) return null
    return DividerSegment(
        direction = SplitDirection.VERTICAL,
        position = left.bounds.right,
        start = start,
        end = end,
        firstCellId = left.id,
        secondCellId = right.id,
    )
}

private fun addHorizontalSegment(
    first: WorkspaceCell,
    second: WorkspaceCell,
): DividerSegment? {
    val (top, bottom) = when {
        first.bounds.bottom == second.bounds.top -> first to second
        second.bounds.bottom == first.bounds.top -> second to first
        else -> return null
    }
    val start = maxOf(top.bounds.left, bottom.bounds.left)
    val end = minOf(top.bounds.right, bottom.bounds.right)
    if (end <= start) return null
    return DividerSegment(
        direction = SplitDirection.HORIZONTAL,
        position = top.bounds.bottom,
        start = start,
        end = end,
        firstCellId = top.id,
        secondCellId = bottom.id,
    )
}

private fun connectedGroups(segments: List<DividerSegment>): List<List<DividerSegment>> {
    if (segments.isEmpty()) return emptyList()
    val sorted = segments.sortedBy(DividerSegment::start)
    val result = mutableListOf<MutableList<DividerSegment>>()
    sorted.forEach { segment ->
        val current = result.lastOrNull()
        val currentEnd = current?.maxOf(DividerSegment::end)
        if (current == null || currentEnd == null || segment.start > currentEnd) {
            result += mutableListOf(segment)
        } else {
            current += segment
        }
    }
    return result
}

private fun List<DividerSegment>.toDivider(cells: List<WorkspaceCell>): WorkspaceDivider {
    val firstIds = mapTo(sortedSetOf()) { it.firstCellId }
    val secondIds = mapTo(sortedSetOf()) { it.secondCellId }
    val direction = first().direction
    val position = first().position
    val cellsById = cells.associateBy(WorkspaceCell::id)
    val outerStart: Float
    val outerEnd: Float
    when (direction) {
        SplitDirection.VERTICAL -> {
            outerStart = firstIds.minOf { cellsById.getValue(it).bounds.left }
            outerEnd = secondIds.maxOf { cellsById.getValue(it).bounds.right }
        }
        SplitDirection.HORIZONTAL -> {
            outerStart = firstIds.minOf { cellsById.getValue(it).bounds.top }
            outerEnd = secondIds.maxOf { cellsById.getValue(it).bounds.bottom }
        }
    }
    return WorkspaceDivider(
        id = "${direction.name.lowercase()}:${firstIds.joinToString(",")}|${secondIds.joinToString(",")}",
        direction = direction,
        position = position,
        start = minOf(DividerSegment::start),
        end = maxOf(DividerSegment::end),
        parentStart = outerStart,
        parentEnd = outerEnd,
        ratio = (position - outerStart) / (outerEnd - outerStart),
        firstCellIds = firstIds,
        secondCellIds = secondIds,
    )
}
