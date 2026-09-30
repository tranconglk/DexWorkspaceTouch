package com.trancong.dexworkspacetouch.workspace.execution.embedded.layout

import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlan
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlanItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedWorkspaceLayoutMapperTest {
    private val mapper = EmbeddedWorkspaceLayoutMapper()

    @Test fun sharedEdgesAndOddViewport() {
        val plan = plan(item("a", 0f, 0f, .5f, 1f, 0), item("b", .5f, 0f, 1f, 1f, 1))
        val mapped = mapper.map(plan, EmbeddedWorkspaceViewport(1101, 701)) as EmbeddedWorkspaceLayoutResult.Mapped
        assertEquals(PixelRect(0, 0, 551, 701), mapped.panes[0].pixelBounds)
        assertEquals(PixelRect(551, 0, 1101, 701), mapped.panes[1].pixelBounds)
        assertEquals(plan, mapped.planSnapshot)
    }

    @Test fun threeAndFourPaneEdgesAlign() {
        val three = plan(item("a", 0f, 0f, .5f, .5f, 0), item("b", 0f, .5f, .5f, 1f, 1), item("c", .5f, 0f, 1f, 1f, 2))
        val threeRects = (mapper.map(three, EmbeddedWorkspaceViewport(1100, 700)) as EmbeddedWorkspaceLayoutResult.Mapped).panes.map { it.pixelBounds }
        assertEquals(listOf(PixelRect(0, 0, 550, 350), PixelRect(0, 350, 550, 700), PixelRect(550, 0, 1100, 700)), threeRects)
        val four = plan(item("a", 0f, 0f, .5f, .5f, 0), item("b", .5f, 0f, 1f, .5f, 1), item("c", 0f, .5f, .5f, 1f, 2), item("d", .5f, .5f, 1f, 1f, 3))
        val fourRects = (mapper.map(four, EmbeddedWorkspaceViewport(1100, 700)) as EmbeddedWorkspaceLayoutResult.Mapped).panes.map { it.pixelBounds }
        assertEquals(listOf(PixelRect(0, 0, 550, 350), PixelRect(550, 0, 1100, 350), PixelRect(0, 350, 550, 700), PixelRect(550, 350, 1100, 700)), fourRects)
    }

    @Test fun resizeChangesPixelsButNotIdentityOrBounds() {
        val plan = plan(item("waze", 0f, 0f, .625f, 1f, 0), item("calc", .625f, 0f, 1f, 1f, 1))
        val old = mapper.map(plan, EmbeddedWorkspaceViewport(1100, 700)) as EmbeddedWorkspaceLayoutResult.Mapped
        val new = mapper.map(plan, EmbeddedWorkspaceViewport(1400, 850)) as EmbeddedWorkspaceLayoutResult.Mapped
        assertEquals(listOf(PixelRect(0, 0, 688, 700), PixelRect(688, 0, 1100, 700)), old.panes.map { it.pixelBounds })
        assertEquals(listOf(PixelRect(0, 0, 875, 850), PixelRect(875, 0, 1400, 850)), new.panes.map { it.pixelBounds })
        assertEquals(old.panes.map { it.sourceCellId to it.normalizedBounds }, new.panes.map { it.sourceCellId to it.normalizedBounds })
        assertEquals(plan, new.planSnapshot)
    }

    @Test fun invalidViewportAndZeroPixelPaneReject() {
        val plan = plan(item("tiny", 0f, 0f, .01f, 1f, 0))
        assertEquals(EmbeddedWorkspaceLayoutRejection.InvalidViewport(0, 100), (mapper.map(plan, EmbeddedWorkspaceViewport(0, 100)) as EmbeddedWorkspaceLayoutResult.Rejected).cause)
        assertEquals(EmbeddedWorkspaceLayoutRejection.InvalidViewport(-1, 100), (mapper.map(plan, EmbeddedWorkspaceViewport(-1, 100)) as EmbeddedWorkspaceLayoutResult.Rejected).cause)
        assertEquals(EmbeddedWorkspaceLayoutRejection.ZeroPixelPane("tiny"), (mapper.map(plan, EmbeddedWorkspaceViewport(10, 100)) as EmbeddedWorkspaceLayoutResult.Rejected).cause)
    }

    @Test fun overlapRejectsButTouchingEdgesPass() {
        val overlap = plan(item("a", 0f, 0f, .6f, 1f, 0), item("b", .5f, 0f, 1f, 1f, 1))
        assertTrue((mapper.map(overlap, EmbeddedWorkspaceViewport(100, 100)) as EmbeddedWorkspaceLayoutResult.Rejected).cause is EmbeddedWorkspaceLayoutRejection.UnsupportedOverlap)
        val touching = plan(item("a", 0f, 0f, .5f, 1f, 1), item("b", .5f, 0f, 1f, 1f, 0))
        val mapped = mapper.map(touching, EmbeddedWorkspaceViewport(100, 100)) as EmbeddedWorkspaceLayoutResult.Mapped
        assertEquals(listOf("b", "a"), mapped.panes.map { it.sourceCellId })
        assertEquals(PixelRect(50, 0, 100, 100), mapped.panes[0].pixelBounds)
    }

    private fun plan(vararg items: EmbeddedWorkspacePlanItem) = EmbeddedWorkspacePlan("ws", "Workspace", items.toList())
    private fun item(id: String, l: Float, t: Float, r: Float, b: Float, order: Int) =
        EmbeddedWorkspacePlanItem(id, "com.$id", "com.$id.Main", NormalizedBounds(l, t, r, b), order)
}
