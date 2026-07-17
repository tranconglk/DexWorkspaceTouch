package com.trancong.dexworkspacetouch.workspace.templates.ui

import com.trancong.dexworkspacetouch.workspace.templates.WorkspaceTemplateCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceTemplateDialogLayoutTest {
    @Test fun `basic and left right start expanded while top bottom starts collapsed`() {
        assertTrue(WorkspaceTemplateCategory.BASIC.expandedByDefault())
        assertTrue(WorkspaceTemplateCategory.LEFT_RIGHT.expandedByDefault())
        assertTrue(!WorkspaceTemplateCategory.TOP_BOTTOM.expandedByDefault())
    }

    @Test fun `large medium and narrow widths map to three two and one columns`() {
        assertEquals(3, workspaceTemplateColumnCount(1400f, 620f, 1000f))
        assertEquals(2, workspaceTemplateColumnCount(800f, 620f, 1000f))
        assertEquals(1, workspaceTemplateColumnCount(500f, 620f, 1000f))
    }

    @Test fun `column policy never returns four`() {
        listOf(0f, 500f, 620f, 999f, 1000f, 1400f, 3000f).forEach { width ->
            assertTrue(workspaceTemplateColumnCount(width, 620f, 1000f) in 1..3)
        }
    }

    @Test fun `dialog size is clamped inside safe available constraints`() {
        val size = calculateWorkspaceTemplateDialogSize(1872f, 1080f, 0.88f, 1440f, 0.84f)
        assertEquals(1440f, size.width)
        assertEquals(907.2f, size.height, 0.001f)
        assertTrue(size.width <= 1872f)
        assertTrue(size.height <= 1080f)
    }

    @Test fun `small window remains inside its available size`() {
        val size = calculateWorkspaceTemplateDialogSize(480f, 300f, 0.88f, 1440f, 0.84f)
        assertEquals(422.4f, size.width, 0.001f)
        assertEquals(252f, size.height, 0.001f)
    }

    @Test fun `windowed host uses platform width while large host uses custom width`() {
        assertTrue(shouldUsePlatformDefaultTemplateDialogWidth(639f, 1000f))
        assertTrue(!shouldUsePlatformDefaultTemplateDialogWidth(1920f, 1000f))
    }
}
