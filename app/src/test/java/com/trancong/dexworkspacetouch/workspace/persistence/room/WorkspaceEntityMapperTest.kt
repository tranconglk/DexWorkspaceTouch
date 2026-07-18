package com.trancong.dexworkspacetouch.workspace.persistence.room

import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import com.trancong.dexworkspacetouch.workspace.persistence.serialization.DeterministicWorkspaceCanvasJsonSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceEntityMapperTest {
    private val serializer = DeterministicWorkspaceCanvasJsonSerializer()
    private val workspace = Workspace("id", "Tên", WorkspaceCanvas.singleCell(), 9, 1, 100, 200, isPinned = true)

    @Test fun `domain maps every field to entity`() {
        val entity = workspace.toEntity(serializer)
        assertEquals(workspace.id, entity.id)
        assertEquals(workspace.name, entity.name)
        assertEquals(workspace.modifiedSequence, entity.modifiedSequence)
        assertEquals(workspace.schemaVersion, entity.schemaVersion)
        assertEquals(workspace.createdAtEpochMillis, entity.createdAtEpochMillis)
        assertEquals(workspace.updatedAtEpochMillis, entity.updatedAtEpochMillis)
        assertEquals(true, entity.isPinned)
    }

    @Test fun `entity maps every field to domain`() {
        assertEquals(workspace, workspace.toEntity(serializer).toDomain(serializer))
    }

    @Test fun `round trip preserves timestamps schema sequence and canvas`() {
        val roundTrip = workspace.toEntity(serializer).toDomain(serializer)
        assertEquals(100, roundTrip.createdAtEpochMillis)
        assertEquals(200, roundTrip.updatedAtEpochMillis)
        assertEquals(1, roundTrip.schemaVersion)
        assertEquals(9, roundTrip.modifiedSequence)
        assertEquals(workspace.canvas, roundTrip.canvas)
    }
}
