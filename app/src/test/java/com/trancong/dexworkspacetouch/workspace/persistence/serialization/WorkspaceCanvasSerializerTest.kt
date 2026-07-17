package com.trancong.dexworkspacetouch.workspace.persistence.serialization

import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.SplitDirection
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvasEditor
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell
import com.trancong.dexworkspacetouch.workspace.persistence.domain.WorkspacePersistenceException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceCanvasSerializerTest {
    private val serializer = DeterministicWorkspaceCanvasJsonSerializer()

    @Test fun `one empty cell round trips`() = roundTrip(WorkspaceCanvas.singleCell())

    @Test fun `assigned app and Vietnamese label round trip`() = roundTrip(
        WorkspaceCanvas(listOf(WorkspaceCell("cell", NormalizedBounds.FullCanvas, app))),
    )

    @Test fun `multiple cells and nested splits round trip`() {
        val editor = WorkspaceCanvasEditor()
        val two = editor.splitCell(WorkspaceCanvas.singleCell(), "cell", SplitDirection.VERTICAL)
        val nested = editor.splitCell(two, "cell_a", SplitDirection.HORIZONTAL, 0.3f)
        roundTrip(nested)
        assertEquals(3, nested.cells.size)
    }

    @Test fun `encoding is deterministic`() {
        val canvas = WorkspaceCanvas(listOf(WorkspaceCell("cell", NormalizedBounds.FullCanvas, app)))
        assertEquals(serializer.encode(canvas), serializer.encode(canvas))
    }

    @Test fun `decode then encode produces canonical JSON`() {
        val json = """ { "cells" : [ { "id":"cell", "bounds": {"left":0,"top":0,"right":1,"bottom":1}, "app":null } ] } """
        assertEquals(serializer.encode(WorkspaceCanvas.singleCell()), serializer.encode(serializer.decode(json, 1)))
    }

    @Test fun `malformed JSON is typed failure`() {
        assertThrows(WorkspacePersistenceException.SerializationFailure::class.java) {
            serializer.decode("{not-json", 1)
        }
    }

    @Test fun `unsupported schema version is typed failure`() {
        assertThrows(WorkspacePersistenceException.UnsupportedSchema::class.java) {
            serializer.decode(serializer.encode(WorkspaceCanvas.singleCell()), 2)
        }
    }

    @Test fun `nullable activity name is preserved`() {
        val nullable = app.copy(activityName = null)
        val decoded = serializer.decode(
            serializer.encode(WorkspaceCanvas(listOf(WorkspaceCell("cell", NormalizedBounds.FullCanvas, nullable)))),
            1,
        )
        assertEquals(null, decoded.cells.single().app?.activityName)
    }

    @Test fun `quotes backslashes and control characters are escaped`() {
        val special = app.copy(label = "Ứng dụng \"A\"\\B\n")
        val json = serializer.encode(WorkspaceCanvas(listOf(WorkspaceCell("cell", NormalizedBounds.FullCanvas, special))))
        assertTrue(json.contains("\\\"A\\\"\\\\B\\n"))
        assertEquals(special, serializer.decode(json, 1).cells.single().app)
    }

    @Test fun `duplicate and invalid cells are rejected`() {
        val duplicate = """{"cells":[{"id":"x","bounds":{"left":0,"top":0,"right":0.5,"bottom":1},"app":null},{"id":"x","bounds":{"left":0.5,"top":0,"right":1,"bottom":1},"app":null}]}"""
        assertThrows(WorkspacePersistenceException.SerializationFailure::class.java) {
            serializer.decode(duplicate, 1)
        }
        assertThrows(WorkspacePersistenceException.SerializationFailure::class.java) {
            serializer.encode(WorkspaceCanvas(emptyList()))
        }
    }

    @Test fun `unknown fields are rejected`() {
        val json = """{"cells":[],"future":true}"""
        assertThrows(WorkspacePersistenceException.SerializationFailure::class.java) {
            serializer.decode(json, 1)
        }
    }

    @Test fun `duplicate fields and trailing input are rejected`() {
        assertThrows(WorkspacePersistenceException.SerializationFailure::class.java) {
            serializer.decode("""{"cells":[],"cells":[]}""", 1)
        }
        assertThrows(WorkspacePersistenceException.SerializationFailure::class.java) {
            serializer.decode("""{"cells":[]} trailing""", 1)
        }
    }

    @Test fun `non finite numbers are rejected`() {
        val json = """{"cells":[{"id":"cell","bounds":{"left":0,"top":0,"right":1e999,"bottom":1},"app":null}]}"""
        assertThrows(WorkspacePersistenceException.SerializationFailure::class.java) {
            serializer.decode(json, 1)
        }
    }

    @Test fun `oversized and deeply nested payloads fail with typed error`() {
        assertThrows(WorkspacePersistenceException.SerializationFailure::class.java) {
            serializer.decode(" ".repeat(1_000_001), 1)
        }
        val deeplyNested = "[".repeat(65) + "null" + "]".repeat(65)
        assertThrows(WorkspacePersistenceException.SerializationFailure::class.java) {
            serializer.decode(deeplyNested, 1)
        }
    }

    private fun roundTrip(canvas: WorkspaceCanvas) {
        assertEquals(canvas, serializer.decode(serializer.encode(canvas), 1))
    }

    private val app = AssignedApp("com.example.app", "com.example.app.Main", "Bản đồ Việt")
}
