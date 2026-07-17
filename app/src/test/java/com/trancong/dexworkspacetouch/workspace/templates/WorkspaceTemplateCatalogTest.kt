package com.trancong.dexworkspacetouch.workspace.templates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceTemplateCatalogTest {
    private val catalog = WorkspaceTemplateCatalog.default()

    @Test fun `catalog exposes a focused stable unique set in category order`() {
        assertTrue(catalog.allTemplates.size in 12..16)
        assertEquals(16, catalog.allTemplates.size)
        assertEquals(16, catalog.allTemplates.map { it.id }.distinct().size)
        assertEquals(
            listOf(
                WorkspaceTemplateCategory.BASIC,
                WorkspaceTemplateCategory.LEFT_RIGHT,
                WorkspaceTemplateCategory.TOP_BOTTOM,
            ),
            catalog.allTemplates.map { it.category }.distinct(),
        )
        assertEquals(
            listOf(6, 6, 4),
            WorkspaceTemplateCategory.entries.map { catalog.templatesIn(it).size },
        )
        assertTrue(WorkspaceTemplateCategory.entries.all { catalog.templatesIn(it).isNotEmpty() })
    }

    @Test fun `catalog order and factories are deterministic across construction`() {
        val second = WorkspaceTemplateCatalog.default()
        assertEquals(catalog.allTemplates.map { it.id }, second.allTemplates.map { it.id })
        assertEquals(
            catalog.allTemplates.map { it.factory() },
            second.allTemplates.map { it.factory() },
        )
    }

    @Test fun `canonical signatures contain no duplicate topology`() {
        val signatures = catalog.allTemplates.map { it.factory().canonicalTemplateSignature() }
        assertEquals(signatures.size, signatures.distinct().size)
    }

    @Test fun `mirror templates remain distinct`() {
        assertNotEquals(signature("left-sidebar"), signature("right-sidebar"))
        assertNotEquals(signature("left-large-two-right"), signature("right-large-two-left"))
        assertNotEquals(signature("top-large-two-bottom"), signature("two-top-bottom-large"))
    }

    @Test fun `duplicate two-by-two variant and legacy six-grid are absent`() {
        assertNull(catalog.find("two-left-two-right"))
        assertNull(catalog.find("six-grid"))
        assertEquals(1, catalog.allTemplates.count {
            it.factory().canonicalTemplateSignature() == signature("four-grid")
        })
    }

    @Test fun `low value dense and advanced variants are absent`() {
        listOf(
            "left-large-three-right",
            "right-large-three-left",
            "top-large-three-bottom",
            "three-top-bottom-large",
            "two-top-three-bottom",
            "three-top-two-bottom",
            "top-left-large",
            "top-right-large",
        ).forEach { assertNull(catalog.find(it)) }
    }

    @Test fun `find returns catalog instance and metadata is complete`() {
        val template = catalog.allTemplates[3]
        assertSame(template, catalog.find(template.id))
        assertNull(catalog.find("missing"))
        assertTrue(catalog.allTemplates.all {
            it.name.isNotBlank() && it.description.isNotBlank() && it.previewId.isNotBlank()
        })
    }

    private fun signature(id: String) = catalog.find(id)!!.factory().canonicalTemplateSignature()
}
