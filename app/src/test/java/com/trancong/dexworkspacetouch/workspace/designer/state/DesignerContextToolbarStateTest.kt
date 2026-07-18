package com.trancong.dexworkspacetouch.workspace.designer.state

import com.trancong.dexworkspacetouch.workspace.designer.model.SplitDirection
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvasEditor
import com.trancong.dexworkspacetouch.workspace.designer.model.dividers
import com.trancong.dexworkspacetouch.workspace.templates.WorkspaceTemplateCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell

class DesignerContextToolbarStateTest {
    @Test fun selectedCellMergeAvailabilityFollowsTopology() {
        val single = DesignerContextToolbarState.from(WorkspaceCanvas.singleCell(), "cell", null, false, false)
        assertFalse((single.context as DesignerContextToolbarState.Context.Cell).canMerge)

        val columns = WorkspaceCanvas(listOf(
            WorkspaceCell("left", NormalizedBounds(0f, 0f, 0.5f, 1f)),
            WorkspaceCell("right", NormalizedBounds(0.5f, 0f, 1f, 1f)),
        ))
        val paired = DesignerContextToolbarState.from(columns, "left", null, false, false)
        assertTrue((paired.context as DesignerContextToolbarState.Context.Cell).canMerge)
    }

    @Test fun `no selection exposes history and summary only`() {
        val state = DesignerContextToolbarState.from(WorkspaceCanvas.singleCell(), null, null, true, false)
        assertEquals(DesignerContextToolbarState.Context.None, state.context)
        assertTrue(state.canUndo)
        assertFalse(state.canRedo)
        assertEquals("1 / 5 ô • Đã gán 0 ứng dụng", state.summary.statusText)
    }

    @Test fun `cell selection exposes split capability`() {
        val canvas = WorkspaceCanvas.singleCell()
        val state = DesignerContextToolbarState.from(canvas, "cell", null, false, false)
        val cell = state.context as DesignerContextToolbarState.Context.Cell
        assertTrue(cell.canSplitHorizontal)
        assertTrue(cell.canSplitVertical)
        assertFalse(cell.maximumCellsReached)
    }

    @Test fun `five cells disable both split actions`() {
        val canvas = WorkspaceTemplateCatalog.default().find("top-large-four-bottom")!!.factory()
        val state = DesignerContextToolbarState.from(canvas, canvas.cells.first().id, null, false, false)
        val cell = state.context as DesignerContextToolbarState.Context.Cell
        assertTrue(cell.maximumCellsReached)
        assertFalse(cell.canSplitHorizontal)
        assertFalse(cell.canSplitVertical)
    }

    @Test fun `divider selection wins and formats direction ratio deterministically`() {
        val canvas = WorkspaceCanvasEditor().splitCell(
            WorkspaceCanvas.singleCell(), "cell", SplitDirection.VERTICAL, 0.5f,
        )
        val divider = canvas.dividers().single()
        val state = DesignerContextToolbarState.from(canvas, canvas.cells.first().id, divider.id, true, true)
        val context = state.context as DesignerContextToolbarState.Context.Divider
        assertEquals("50%", context.ratioText)
        assertEquals("Đường chia dọc • 50%", context.statusText)
        assertTrue(context.canDecrease)
        assertTrue(context.canIncrease)
    }

    @Test fun `layout policy switches at breakpoint`() {
        assertEquals(DesignerToolbarLayoutPolicy.NARROW, designerToolbarLayoutPolicy(1099f, 1100f))
        assertEquals(DesignerToolbarLayoutPolicy.WIDE, designerToolbarLayoutPolicy(1100f, 1100f))
    }
}
