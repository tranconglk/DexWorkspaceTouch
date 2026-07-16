package com.trancong.dexworkspacetouch.workspace.designer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceActionSafeAreaTest {
    @Test
    fun `divider on top adds clearance above overlay`() {
        val area = safeArea(top = true)

        assertEquals(48, area.y)
        assertEquals(136, area.height)
    }

    @Test
    fun `divider on bottom adds clearance below overlay`() {
        val area = safeArea(bottom = true)

        assertEquals(16, area.y)
        assertEquals(136, area.height)
        assertEquals(152, area.y + area.height)
    }

    @Test
    fun `divider on left adds clearance before overlay`() {
        val area = safeArea(left = true)

        assertEquals(48, area.x)
        assertEquals(236, area.width)
    }

    @Test
    fun `divider on right adds clearance after overlay`() {
        val area = safeArea(right = true)

        assertEquals(16, area.x)
        assertEquals(236, area.width)
        assertEquals(252, area.x + area.width)
    }

    @Test
    fun `small cell preserves a valid 56 pixel action area`() {
        val area = workspaceActionSafeArea(
            cell = ComposePlacement(10, 20, 80, 60),
            baseInset = 16,
            dividerClearance = 32,
            minimumContentSize = 56,
            dividerOnLeft = true,
            dividerOnTop = true,
            dividerOnRight = true,
            dividerOnBottom = true,
        )

        assertEquals(56, area.width)
        assertEquals(56, area.height)
        assertEquals(22, area.x)
        assertEquals(22, area.y)
    }

    private fun safeArea(
        left: Boolean = false,
        top: Boolean = false,
        right: Boolean = false,
        bottom: Boolean = false,
    ) = workspaceActionSafeArea(
        cell = ComposePlacement(0, 0, 300, 200),
        baseInset = 16,
        dividerClearance = 32,
        minimumContentSize = 56,
        dividerOnLeft = left,
        dividerOnTop = top,
        dividerOnRight = right,
        dividerOnBottom = bottom,
    )
}
