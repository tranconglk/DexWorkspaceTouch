package com.trancong.dexworkspacetouch.workspace.templates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceTemplateCatalogTest {
    private val catalog = WorkspaceTemplateCatalog.default()

    @Test fun `default catalog exposes ten stable unique templates`() {
        assertEquals(10, catalog.allTemplates.size)
        assertEquals(10, catalog.allTemplates.map { it.id }.distinct().size)
        assertEquals(
            listOf(
                "full-screen", "two-columns", "three-columns", "four-grid", "left-sidebar",
                "right-sidebar", "top-bottom", "top-two-bottom", "two-top-bottom", "three-top-two-bottom",
            ),
            catalog.allTemplates.map { it.id },
        )
    }

    @Test fun `catalog no longer contains a six-cell template`() {
        assertNull(catalog.find("six-grid"))
        assertTrue(catalog.allTemplates.none { it.factory().cells.size > 5 })
    }

    @Test fun `find returns catalog instance and unknown id returns null`() {
        val template = catalog.allTemplates[3]
        assertSame(template, catalog.find(template.id))
        assertNull(catalog.find("missing"))
    }

    @Test fun `catalog metadata is complete for previews and accessibility`() {
        assertTrue(catalog.allTemplates.all {
            it.name.isNotBlank() && it.description.isNotBlank() && it.previewId.isNotBlank()
        })
        assertTrue(catalog.allTemplates.map { it.category }.toSet().containsAll(WorkspaceTemplateCategory.entries))
    }
}
