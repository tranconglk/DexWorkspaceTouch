package com.trancong.dexworkspacetouch.workspace.designer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkspaceCanvasEditorTest {
    private val editor = WorkspaceCanvasEditor()

    @Test fun splitVerticalFiftyFifty() {
        val cells = editor.splitCell(WorkspaceCanvas.singleCell(), "cell", SplitDirection.VERTICAL).cells
        assertEquals(NormalizedBounds(0f, 0f, 0.5f, 1f), cells[0].bounds)
        assertEquals(NormalizedBounds(0.5f, 0f, 1f, 1f), cells[1].bounds)
    }

    @Test fun splitHorizontalFiftyFifty() {
        val cells = editor.splitCell(WorkspaceCanvas.singleCell(), "cell", SplitDirection.HORIZONTAL).cells
        assertEquals(NormalizedBounds(0f, 0f, 1f, 0.5f), cells[0].bounds)
        assertEquals(NormalizedBounds(0f, 0.5f, 1f, 1f), cells[1].bounds)
    }

    @Test fun splitAtThirtySeventy() {
        val cells = editor.splitCell(WorkspaceCanvas.singleCell(), "cell", SplitDirection.VERTICAL, 0.3f).cells
        assertEquals(0.3f, cells[0].bounds.width, 0.0001f)
        assertEquals(0.7f, cells[1].bounds.width, 0.0001f)
    }

    @Test fun assignedAppStaysOnlyOnFirstCell() {
        val app = AssignedApp("com.example", "com.example.Main", "Example")
        val canvas = WorkspaceCanvas(listOf(WorkspaceCell("root", NormalizedBounds.FullCanvas, app)))
        val cells = editor.splitCell(canvas, "root", SplitDirection.VERTICAL).cells
        assertEquals(app, cells[0].app)
        assertNull(cells[1].app)
    }

    @Test fun missingCellIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            editor.splitCell(WorkspaceCanvas.singleCell(), "missing", SplitDirection.VERTICAL)
        }
    }

    @Test fun ratioOutsideRangeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            editor.splitCell(WorkspaceCanvas.singleCell(), "cell", SplitDirection.VERTICAL, 0.19f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            editor.splitCell(WorkspaceCanvas.singleCell(), "cell", SplitDirection.VERTICAL, 0.81f)
        }
    }

    @Test fun splittingChildAgainProducesUniqueIds() {
        val first = editor.splitCell(WorkspaceCanvas.singleCell(), "cell", SplitDirection.VERTICAL)
        val second = editor.splitCell(first, "cell_a", SplitDirection.HORIZONTAL)
        assertEquals(second.cells.size, second.cells.map { it.id }.toSet().size)
        assertEquals(listOf("cell_a_a", "cell_a_b", "cell_b"), second.cells.map { it.id })
    }

    @Test fun unrelatedCellsKeepTheirOrderAndValue() {
        val first = editor.splitCell(WorkspaceCanvas.singleCell(), "cell", SplitDirection.VERTICAL)
        val unrelated = first.cells[1]
        val second = editor.splitCell(first, "cell_a", SplitDirection.HORIZONTAL)
        assertEquals(unrelated, second.cells[2])
    }
}
