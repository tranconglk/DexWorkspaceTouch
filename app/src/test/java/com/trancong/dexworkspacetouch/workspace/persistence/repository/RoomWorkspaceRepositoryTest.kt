package com.trancong.dexworkspacetouch.workspace.persistence.repository

import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import com.trancong.dexworkspacetouch.workspace.persistence.domain.WorkspacePersistenceException
import com.trancong.dexworkspacetouch.workspace.persistence.room.WorkspaceDao
import com.trancong.dexworkspacetouch.workspace.persistence.room.WorkspaceEntity
import com.trancong.dexworkspacetouch.workspace.persistence.room.toEntity
import com.trancong.dexworkspacetouch.workspace.persistence.serialization.DeterministicWorkspaceCanvasJsonSerializer
import com.trancong.dexworkspacetouch.workspace.persistence.serialization.WorkspaceCanvasSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomWorkspaceRepositoryTest {
    private val dao = FakeWorkspaceDao()
    private val repository = RoomWorkspaceRepository(dao, DeterministicWorkspaceCanvasJsonSerializer())

    @Test fun `observe maps entities to domain`() = runBlocking {
        repository.insert(workspace)
        assertEquals(listOf(workspace), repository.observeAll().first())
    }

    @Test fun `malformed row is isolated and reported without hiding valid row`() = runBlocking {
        dao.putRaw(workspace.toEntity(DeterministicWorkspaceCanvasJsonSerializer()))
        dao.putRaw(workspace.toEntity(DeterministicWorkspaceCanvasJsonSerializer()).copy(id = "bad", canvasJson = "{"))

        val snapshot = repository.observeSnapshot().first()

        assertEquals(listOf("id"), snapshot.workspaces.map { it.id })
        assertEquals(listOf(WorkspacePersistenceIssue.CorruptedRow("bad")), snapshot.issues)
        assertEquals(2, dao.count())
    }

    @Test fun `unsupported schema is retained and reported without hiding valid row`() = runBlocking {
        dao.putRaw(workspace.toEntity(DeterministicWorkspaceCanvasJsonSerializer()))
        dao.putRaw(
            workspace.toEntity(DeterministicWorkspaceCanvasJsonSerializer())
                .copy(id = "future", schemaVersion = 2),
        )

        val snapshot = repository.observeSnapshot().first()

        assertEquals(listOf("id"), snapshot.workspaces.map { it.id })
        assertEquals(listOf(WorkspacePersistenceIssue.UnsupportedSchema("future", 2)), snapshot.issues)
        assertTrue(dao.exists("future"))
    }

    @Test fun `insert and get by id`() = runBlocking {
        repository.insert(workspace)
        assertEquals(workspace, repository.getById(workspace.id))
        assertNull(repository.getById("missing"))
    }

    @Test fun `update preserves identity and changes values`() = runBlocking {
        repository.insert(workspace)
        val updated = workspace.copy(name = "Đã đổi", updatedAtEpochMillis = 300)
        repository.update(updated)
        assertEquals(updated, repository.getById(workspace.id))
    }

    @Test fun `delete removes row and count exists are mapped`() = runBlocking {
        repository.insert(workspace)
        assertTrue(repository.exists(workspace.id))
        assertEquals(1, repository.count())
        repository.deleteById(workspace.id)
        assertFalse(repository.exists(workspace.id))
        assertEquals(0, repository.count())
    }

    @Test fun `duplicate id has typed error`() {
        runBlocking {
            repository.insert(workspace)
            assertThrows(WorkspacePersistenceException.DuplicateId::class.java) {
                runBlocking { repository.insert(workspace) }
            }
        }
    }

    @Test fun `missing update and delete map database error`() {
        assertThrows(WorkspacePersistenceException.DatabaseFailure::class.java) {
            runBlocking { repository.update(workspace) }
        }
        assertThrows(WorkspacePersistenceException.DatabaseFailure::class.java) {
            runBlocking { repository.deleteById("missing") }
        }
    }

    @Test fun `serialization failure remains typed`() {
        val failing = RoomWorkspaceRepository(dao, FailingSerializer)
        assertThrows(WorkspacePersistenceException.SerializationFailure::class.java) {
            runBlocking { failing.insert(workspace) }
        }
    }

    @Test fun `failed update preserves previous row`() = runBlocking {
        repository.insert(workspace)
        val failing = RoomWorkspaceRepository(dao, FailingSerializer)
        assertThrows(WorkspacePersistenceException.SerializationFailure::class.java) {
            runBlocking { failing.update(workspace.copy(name = "Should not persist")) }
        }
        assertEquals(workspace, repository.getById(workspace.id))
    }

    private class FakeWorkspaceDao : WorkspaceDao {
        private val state = MutableStateFlow<List<WorkspaceEntity>>(emptyList())
        override fun observeAll(): Flow<List<WorkspaceEntity>> = state
        override suspend fun getById(id: String) = state.value.firstOrNull { it.id == id }
        override suspend fun insert(entity: WorkspaceEntity) {
            check(state.value.none { it.id == entity.id })
            state.value += entity
        }
        override suspend fun updateRows(entity: WorkspaceEntity): Int {
            if (state.value.none { it.id == entity.id }) return 0
            state.value = state.value.map { if (it.id == entity.id) entity else it }
            return 1
        }
        override suspend fun deleteRowsById(id: String): Int {
            if (state.value.none { it.id == id }) return 0
            state.value = state.value.filterNot { it.id == id }
            return 1
        }
        override suspend fun exists(id: String) = state.value.any { it.id == id }
        override suspend fun count() = state.value.size
        fun putRaw(entity: WorkspaceEntity) { state.value += entity }
    }

    private object FailingSerializer : WorkspaceCanvasSerializer {
        override fun encode(canvas: WorkspaceCanvas): String =
            throw WorkspacePersistenceException.SerializationFailure("failed")
        override fun decode(json: String, schemaVersion: Int): WorkspaceCanvas =
            throw WorkspacePersistenceException.SerializationFailure("failed")
    }

    private companion object {
        val workspace = Workspace("id", "Tên", WorkspaceCanvas.singleCell(), 1, 1, 10, 20)
    }
}
