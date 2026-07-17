package com.trancong.dexworkspacetouch.workspace.persistence.domain

import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkspaceTest {
    @Test fun `blank id is rejected`() = rejects { workspace(id = " ") }
    @Test fun `blank trimmed name is rejected`() = rejects { workspace(name = "  ") }
    @Test fun `invalid schema version is rejected`() = rejects { workspace(schemaVersion = 0) }
    @Test fun `invalid timestamps are rejected`() {
        rejects { workspace(created = -1) }
        rejects { workspace(created = 10, updated = 9) }
    }
    @Test fun `valid workspace preserves every value`() {
        val value = workspace()
        assertEquals("id", value.id)
        assertEquals(7, value.modifiedSequence)
        assertEquals(1, value.schemaVersion)
    }

    private fun rejects(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java, block)
    }

    private fun workspace(
        id: String = "id",
        name: String = "Name",
        schemaVersion: Int = 1,
        created: Long = 10,
        updated: Long = 20,
    ) = Workspace(id, name, WorkspaceCanvas.singleCell(), 7, schemaVersion, created, updated)
}
