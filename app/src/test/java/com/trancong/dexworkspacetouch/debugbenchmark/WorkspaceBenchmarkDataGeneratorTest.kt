package com.trancong.dexworkspacetouch.debugbenchmark

import com.trancong.dexworkspacetouch.workspace.library.model.toLibraryItem
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvasValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceBenchmarkDataGeneratorTest {
    @Test fun `generator is deterministic for all supported sizes`() {
        listOf(100, 250, 500).forEach { count ->
            assertEquals(WorkspaceBenchmarkDataGenerator.generate(count), WorkspaceBenchmarkDataGenerator.generate(count))
        }
    }

    @Test fun `five hundred workspaces have stable unique identity and valid canvases`() {
        val values = WorkspaceBenchmarkDataGenerator.generate(500)
        val validator = WorkspaceCanvasValidator()
        assertEquals(500, values.size)
        assertEquals(500, values.map { it.id }.distinct().size)
        assertEquals("benchmark-0001", values.first().id)
        assertEquals("benchmark-0500", values.last().id)
        assertTrue(values.all { validator.validate(it.canvas).isEmpty() })
        assertTrue(values.all { it.canvas.cells.size in 1..4 })
    }

    @Test fun `five hundred workspace presentation mappings preserve order and content`() {
        val values = WorkspaceBenchmarkDataGenerator.generate(500)
        val mapped = values.map { it.toLibraryItem() }
        assertEquals(values.map { it.id }, mapped.map { it.id })
        assertEquals(values.map { it.canvas }, mapped.map { it.canvas })
    }

    @Test fun `unsupported generator size is rejected`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            WorkspaceBenchmarkDataGenerator.generate(499)
        }
    }
}
