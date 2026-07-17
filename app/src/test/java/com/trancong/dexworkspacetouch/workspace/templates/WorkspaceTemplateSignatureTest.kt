package com.trancong.dexworkspacetouch.workspace.templates

import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WorkspaceTemplateSignatureTest {
    @Test fun `signature ignores ids cell order and differences within tolerance`() {
        val first = WorkspaceCanvas(
            listOf(
                WorkspaceCell("a", NormalizedBounds(0f, 0f, 0.5f, 1f)),
                WorkspaceCell("b", NormalizedBounds(0.5f, 0f, 1f, 1f)),
            ),
        )
        val reordered = WorkspaceCanvas(
            listOf(
                WorkspaceCell("other-b", NormalizedBounds(0.50001f, 0f, 1f, 1f)),
                WorkspaceCell("other-a", NormalizedBounds(0f, 0f, 0.50001f, 1f)),
            ),
        )
        assertEquals(first.canonicalTemplateSignature(), reordered.canonicalTemplateSignature())
    }

    @Test fun `different mirror bounds have different signatures`() {
        val builder = WorkspaceTemplateCanvasBuilder()
        assertNotEquals(
            builder.sidebar(largeOnLeft = true).canonicalTemplateSignature(),
            builder.sidebar(largeOnLeft = false).canonicalTemplateSignature(),
        )
    }
}
