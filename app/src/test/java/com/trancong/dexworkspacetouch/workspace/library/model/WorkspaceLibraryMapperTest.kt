package com.trancong.dexworkspacetouch.workspace.library.model

import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceLibraryMapperTest {
    @Test fun `domain maps to UI without changing identity canvas or sequence`() {
        val domain = Workspace("id", "Name", WorkspaceCanvas.singleCell(), 7, 1, 100, 200)
        val item = domain.toLibraryItem()
        assertEquals(domain.id, item.id)
        assertEquals(domain.name, item.name)
        assertEquals(domain.canvas, item.canvas)
        assertEquals(domain.modifiedSequence, item.modifiedSequence)
        assertEquals(domain.createdAtEpochMillis, item.createdAtEpochMillis)
        assertEquals(domain.updatedAtEpochMillis, item.updatedAtEpochMillis)
        assertEquals(1, domain.schemaVersion)
        assertEquals(100, domain.createdAtEpochMillis)
        assertEquals(200, domain.updatedAtEpochMillis)
    }

    @Test fun `UI editing data maps back with explicit domain metadata`() {
        val item = WorkspaceLibraryItem("id", "Name", WorkspaceCanvas.singleCell(), 8)
        val domain = item.toDomainWorkspace(1, 100, 300)
        assertEquals(item.id, domain.id)
        assertEquals(item.name, domain.name)
        assertEquals(item.canvas, domain.canvas)
        assertEquals(item.modifiedSequence, domain.modifiedSequence)
        assertEquals(1, domain.schemaVersion)
        assertEquals(100, domain.createdAtEpochMillis)
        assertEquals(300, domain.updatedAtEpochMillis)
    }
}
