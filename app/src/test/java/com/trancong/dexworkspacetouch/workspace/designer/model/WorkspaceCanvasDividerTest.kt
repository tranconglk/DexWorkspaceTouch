package com.trancong.dexworkspacetouch.workspace.designer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceCanvasDividerTest {
    private val editor = WorkspaceCanvasEditor()

    @Test
    fun `vertical split exposes one deterministic divider`() {
        val canvas = editor.splitCell(WorkspaceCanvas.singleCell(), "cell", SplitDirection.VERTICAL)

        val divider = canvas.dividers().single()
        assertEquals(SplitDirection.VERTICAL, divider.direction)
        assertEquals(0.5f, divider.position)
        assertEquals(0.5f, divider.ratio)
        assertEquals("vertical:cell_a|cell_b", divider.id)
    }

    @Test
    fun `nested split keeps shared divider connected and id stable after resize`() {
        val vertical = editor.splitCell(WorkspaceCanvas.singleCell(), "cell", SplitDirection.VERTICAL)
        val nested = editor.splitCell(vertical, "cell_b", SplitDirection.HORIZONTAL)
        val divider = nested.dividers().single { it.direction == SplitDirection.VERTICAL }

        assertEquals(0f, divider.start)
        assertEquals(1f, divider.end)
        val resized = editor.resizeDivider(nested, divider.id, 0.6f)

        assertEquals(divider.id, resized.dividers().single { it.direction == SplitDirection.VERTICAL }.id)
        assertTrue(WorkspaceCanvasValidator().validate(resized).isEmpty())
    }

    @Test
    fun `resize vertical divider updates both sides`() {
        val canvas = editor.splitCell(WorkspaceCanvas.singleCell(), "cell", SplitDirection.VERTICAL)
        val resized = editor.resizeDivider(canvas, canvas.dividers().single().id, 0.3f)

        assertEquals(NormalizedBounds(0f, 0f, 0.3f, 1f), resized.cells[0].bounds)
        assertEquals(NormalizedBounds(0.3f, 0f, 1f, 1f), resized.cells[1].bounds)
    }

    @Test
    fun `resize horizontal divider supports upper limit`() {
        val canvas = editor.splitCell(WorkspaceCanvas.singleCell(), "cell", SplitDirection.HORIZONTAL)
        val resized = editor.resizeDivider(canvas, canvas.dividers().single().id, 0.8f)

        assertEquals(NormalizedBounds(0f, 0f, 1f, 0.8f), resized.cells[0].bounds)
        assertEquals(NormalizedBounds(0f, 0.8f, 1f, 1f), resized.cells[1].bounds)
    }

    @Test
    fun `resize rejects ratio outside 20 to 80 percent`() {
        val canvas = editor.splitCell(WorkspaceCanvas.singleCell(), "cell", SplitDirection.VERTICAL)
        val dividerId = canvas.dividers().single().id

        assertThrows(IllegalArgumentException::class.java) {
            editor.resizeDivider(canvas, dividerId, 0.19f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            editor.resizeDivider(canvas, dividerId, 0.81f)
        }
    }

    @Test
    fun `resize rejects missing divider`() {
        assertThrows(IllegalArgumentException::class.java) {
            editor.resizeDivider(WorkspaceCanvas.singleCell(), "missing", 0.5f)
        }
    }
}
