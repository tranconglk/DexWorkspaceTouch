package com.trancong.dexworkspacetouch.workspace.templates

import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvasValidator
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceTemplateFactoryTest {
    private val templates = WorkspaceTemplateCatalog.default().allTemplates
    private val validator = WorkspaceCanvasValidator()

    @Test fun `every template is valid complete deterministic empty and within limit`() {
        templates.forEach { template ->
            val first = template.factory()
            val second = template.factory()
            assertTrue("${template.id} must be valid", validator.validate(first).isEmpty())
            assertEquals("${template.id} must be deterministic", first, second)
            assertNotSame("${template.id} must return a new canvas", first, second)
            assertTrue(first.cells.size in 1..WorkspaceLimits.MaxCells)
            assertEquals(first.cells.size, first.cells.map { it.id }.distinct().size)
            assertTrue(first.cells.all { it.app == null })
            assertEquals(
                1f,
                first.cells.sumOf { (it.bounds.width * it.bounds.height).toDouble() }.toFloat(),
                EPSILON,
            )
        }
    }

    @Test fun `canonical ratios are used by basic and sidebar templates`() {
        val catalog = WorkspaceTemplateCatalog.default()
        catalog.find("three-columns")!!.factory().cells.forEach {
            assertEquals(1f / 3f, it.bounds.width, EPSILON)
        }
        catalog.find("three-rows")!!.factory().cells.forEach {
            assertEquals(1f / 3f, it.bounds.height, EPSILON)
        }
        val leftSidebar = catalog.find("left-sidebar")!!.factory().cells
        val rightSidebar = catalog.find("right-sidebar")!!.factory().cells
        assertEquals(0.3f, leftSidebar.first().bounds.width, EPSILON)
        assertEquals(0.7f, rightSidebar.first().bounds.width, EPSILON)
    }

    @Test fun `five-cell top focused template preserves large and compact rows`() {
        val catalog = WorkspaceTemplateCatalog.default()
        val cells = catalog.find("top-large-four-bottom")!!.factory().cells
        assertEquals(5, cells.size)
        assertEquals(0f, cells.first().bounds.top, EPSILON)
        assertEquals(0.7f, cells.first().bounds.bottom, EPSILON)
        cells.drop(1).forEach {
            assertEquals(0.7f, it.bounds.top, EPSILON)
            assertEquals(1f, it.bounds.bottom, EPSILON)
            assertEquals(0.25f, it.bounds.width, EPSILON)
        }
    }

    private companion object { const val EPSILON = 0.000_01f }
}
