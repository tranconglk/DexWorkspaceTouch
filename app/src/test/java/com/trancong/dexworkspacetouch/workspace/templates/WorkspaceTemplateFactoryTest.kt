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

    @Test fun `every template creates a valid deterministic full-area canvas`() {
        templates.forEach { template ->
            val first = template.factory()
            val second = template.factory()
            assertTrue("${template.id} must be valid", validator.validate(first).isEmpty())
            assertEquals("${template.id} must be deterministic", first, second)
            assertNotSame("${template.id} must return an independent immutable value", first, second)
            assertEquals(1f, first.cells.sumOf { (it.bounds.width * it.bounds.height).toDouble() }.toFloat(), EPSILON)
            assertTrue(template.previewId.isNotBlank())
            assertTrue(template.factory().cells.size <= WorkspaceLimits.MaxCells)
        }
    }

    @Test fun `template cell counts match their contracts`() {
        val expected = mapOf(
            "full-screen" to 1,
            "two-columns" to 2,
            "three-columns" to 3,
            "four-grid" to 4,
            "left-sidebar" to 2,
            "right-sidebar" to 2,
            "top-bottom" to 2,
            "top-two-bottom" to 3,
            "two-top-bottom" to 3,
            "three-top-two-bottom" to 5,
        )
        assertEquals(expected, templates.associate { it.id to it.factory().cells.size })
    }

    @Test fun `five-cell template has equal three-top two-bottom bounds`() {
        val cells = WorkspaceTemplateCatalog.default().find("three-top-two-bottom")!!.factory().cells
        assertEquals(5, cells.size)
        cells.take(3).forEach {
            assertEquals(0f, it.bounds.top, EPSILON)
            assertEquals(0.5f, it.bounds.bottom, EPSILON)
            assertEquals(1f / 3f, it.bounds.width, EPSILON)
        }
        cells.drop(3).forEach {
            assertEquals(0.5f, it.bounds.top, EPSILON)
            assertEquals(1f, it.bounds.bottom, EPSILON)
            assertEquals(0.5f, it.bounds.width, EPSILON)
        }
    }

    @Test fun `sidebar and equal-column bounds use normalized ratios`() {
        val catalog = WorkspaceTemplateCatalog.default()
        val left = catalog.find("left-sidebar")!!.factory().cells
        val right = catalog.find("right-sidebar")!!.factory().cells
        val three = catalog.find("three-columns")!!.factory().cells
        assertEquals(0.3f, left.first().bounds.width, EPSILON)
        assertEquals(0.7f, right.first().bounds.width, EPSILON)
        three.forEach { assertEquals(1f / 3f, it.bounds.width, EPSILON) }
    }

    private companion object { const val EPSILON = 0.000_01f }
}
