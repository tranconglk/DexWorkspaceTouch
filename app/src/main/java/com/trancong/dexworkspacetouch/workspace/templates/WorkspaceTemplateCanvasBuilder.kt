package com.trancong.dexworkspacetouch.workspace.templates

import com.trancong.dexworkspacetouch.workspace.designer.model.SplitDirection
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvasEditor

class WorkspaceTemplateCanvasBuilder(
    private val editor: WorkspaceCanvasEditor = WorkspaceCanvasEditor(),
) {
    fun fullScreen(): WorkspaceCanvas = WorkspaceCanvas.singleCell()

    fun columns(count: Int): WorkspaceCanvas = splitInto(
        canvas = fullScreen(),
        cellId = ROOT_ID,
        count = count,
        direction = SplitDirection.VERTICAL,
    )

    fun rows(count: Int): WorkspaceCanvas = splitInto(
        canvas = fullScreen(),
        cellId = ROOT_ID,
        count = count,
        direction = SplitDirection.HORIZONTAL,
    )

    fun sidebar(largeOnLeft: Boolean, ratio: Float = LARGE_RATIO): WorkspaceCanvas =
        editor.splitCell(
            fullScreen(),
            ROOT_ID,
            SplitDirection.VERTICAL,
            if (largeOnLeft) ratio else 1f - ratio,
        )

    fun sideWithRows(largeOnLeft: Boolean, rowCount: Int): WorkspaceCanvas {
        val canvas = sidebar(largeOnLeft)
        return splitInto(
            canvas = canvas,
            cellId = if (largeOnLeft) "cell_b" else "cell_a",
            count = rowCount,
            direction = SplitDirection.HORIZONTAL,
        )
    }

    fun bothSidesTwoRows(): WorkspaceCanvas {
        val columns = columns(2)
        val leftRows = splitInto(columns, "cell_a", 2, SplitDirection.HORIZONTAL)
        return splitInto(leftRows, "cell_b", 2, SplitDirection.HORIZONTAL)
    }

    fun sideWithGrid(largeOnLeft: Boolean): WorkspaceCanvas {
        val columns = editor.splitCell(
            fullScreen(), ROOT_ID, SplitDirection.VERTICAL,
            if (largeOnLeft) GRID_LARGE_RATIO else 1f - GRID_LARGE_RATIO,
        )
        val secondaryId = if (largeOnLeft) "cell_b" else "cell_a"
        val rows = splitInto(columns, secondaryId, 2, SplitDirection.HORIZONTAL)
        val firstRowId = "${secondaryId}_a"
        val secondRowId = "${secondaryId}_b"
        val firstColumns = splitInto(rows, firstRowId, 2, SplitDirection.VERTICAL)
        return splitInto(firstColumns, secondRowId, 2, SplitDirection.VERTICAL)
    }

    fun topWithColumns(largeOnTop: Boolean, columnCount: Int): WorkspaceCanvas {
        val rows = editor.splitCell(
            fullScreen(), ROOT_ID, SplitDirection.HORIZONTAL,
            if (largeOnTop) LARGE_RATIO else 1f - LARGE_RATIO,
        )
        return splitInto(
            rows,
            if (largeOnTop) "cell_b" else "cell_a",
            columnCount,
            SplitDirection.VERTICAL,
        )
    }

    fun mixedRows(topColumns: Int, bottomColumns: Int): WorkspaceCanvas {
        val rows = rows(2)
        val top = splitInto(rows, "cell_a", topColumns, SplitDirection.VERTICAL)
        return splitInto(top, "cell_b", bottomColumns, SplitDirection.VERTICAL)
    }

    fun cornerLarge(topLeft: Boolean): WorkspaceCanvas {
        val columns = editor.splitCell(
            fullScreen(), ROOT_ID, SplitDirection.VERTICAL,
            if (topLeft) GRID_LARGE_RATIO else 1f - GRID_LARGE_RATIO,
        )
        val leftRows = editor.splitCell(
            columns, "cell_a", SplitDirection.HORIZONTAL,
            if (topLeft) GRID_LARGE_RATIO else 0.5f,
        )
        val grid = editor.splitCell(
            leftRows, "cell_b", SplitDirection.HORIZONTAL,
            if (topLeft) 0.5f else GRID_LARGE_RATIO,
        )
        return splitInto(
            grid,
            if (topLeft) "cell_b_b" else "cell_a_b",
            2,
            SplitDirection.VERTICAL,
        )
    }

    private fun splitInto(
        canvas: WorkspaceCanvas,
        cellId: String,
        count: Int,
        direction: SplitDirection,
    ): WorkspaceCanvas {
        require(count >= 1) { "count must be positive" }
        if (count == 1) return canvas
        var result = canvas
        var remainingId = cellId
        for (remainingCount in count downTo 2) {
            result = editor.splitCell(
                result,
                remainingId,
                direction,
                ratio = 1f / remainingCount,
            )
            remainingId = "${remainingId}_b"
        }
        return result
    }

    private companion object {
        const val ROOT_ID = "cell"
        const val LARGE_RATIO = 0.7f
        const val GRID_LARGE_RATIO = 0.6f
    }
}
