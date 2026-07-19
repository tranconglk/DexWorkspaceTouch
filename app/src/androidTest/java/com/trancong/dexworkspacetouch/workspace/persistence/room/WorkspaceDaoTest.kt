package com.trancong.dexworkspacetouch.workspace.persistence.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceDaoTest {
    private lateinit var database: DexWorkspaceDatabase
    private lateinit var dao: WorkspaceDao

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            DexWorkspaceDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.workspaceDao()
    }

    @After fun tearDown() = database.close()

    @Test fun insertGetCountAndExists() = runBlocking {
        dao.insert(entity("id", "Tên Unicode", 1, 10))
        assertNotNull(dao.getById("id"))
        assertTrue(dao.exists("id"))
        assertEquals(1, dao.count())
    }

    @Test fun observeAllEmitsAndSortsDeterministically() = runBlocking {
        dao.insert(entity("d", "Z", 1, 10))
        dao.insert(entity("c", "B", 2, 20))
        dao.insert(entity("b", "A", 2, 20))
        dao.insert(entity("a", "A", 2, 20))
        assertEquals(listOf("a", "b", "c", "d"), dao.observeAll().first().map { it.id })
    }

    @Test fun updateRequiresExistingRow() {
        runBlocking {
            dao.insert(entity("id", "Before", 1, 10))
            dao.update(entity("id", "After", 2, 20))
            assertEquals("After", dao.getById("id")?.name)
            assertThrows(WorkspaceRowNotFoundException::class.java) {
                runBlocking { dao.update(entity("missing", "Name", 1, 10)) }
            }
            assertEquals("After", dao.getById("id")?.name)
        }
    }

    @Test fun deleteRequiresExistingRow() {
        runBlocking {
            dao.insert(entity("id", "Name", 1, 10))
            dao.deleteById("id")
            assertFalse(dao.exists("id"))
            assertThrows(WorkspaceRowNotFoundException::class.java) {
                runBlocking { dao.deleteById("missing") }
            }
        }
    }

    @Test fun duplicateIdFailsClearly() = runBlocking {
        dao.insert(entity("id", "First", 1, 10))
        assertThrows(Exception::class.java) {
            runBlocking { dao.insert(entity("id", "Duplicate", 2, 20)) }
        }
        assertEquals("First", dao.getById("id")?.name)
    }

    @Test fun batchInsertIsAtomicWhenOneIdConflicts() = runBlocking {
        dao.insert(entity("existing", "Existing", 1, 10))
        assertThrows(Exception::class.java) {
            runBlocking {
                dao.insertAllAtomically(listOf(
                    entity("new", "New", 2, 20),
                    entity("existing", "Conflict", 3, 30),
                ))
            }
        }
        assertFalse(dao.exists("new"))
        assertEquals("Existing", dao.getById("existing")?.name)
    }

    @Test fun setPinnedPreservesWorkspaceMetadata() = runBlocking {
        val original = entity("id", "Name", 7, 20)
        dao.insert(original)

        dao.setPinned("id", true)

        assertEquals(original.copy(isPinned = true), dao.getById("id"))
        dao.setPinned("id", false)
        assertEquals(original, dao.getById("id"))
    }

    @Test fun batchPinAndUnpinAreAtomicAndPreserveMetadata() = runBlocking {
        val first = entity("one", "One", 7, 20)
        val second = entity("two", "Two", 8, 30)
        dao.insert(first)
        dao.insert(second)

        dao.setPinnedForIds(setOf("two", "one"), true)
        assertEquals(first.copy(isPinned = true), dao.getById("one"))
        assertEquals(second.copy(isPinned = true), dao.getById("two"))
        dao.setPinnedForIds(setOf("one", "two"), false)
        assertEquals(first, dao.getById("one"))
        assertEquals(second, dao.getById("two"))
    }

    @Test fun missingBatchPinAndDeleteRollBackEveryRow() = runBlocking {
        val first = entity("one", "One", 1, 10)
        val second = entity("two", "Two", 2, 20)
        dao.insert(first)
        dao.insert(second)

        assertThrows(WorkspaceBatchRowCountException::class.java) {
            runBlocking { dao.setPinnedForIds(setOf("one", "missing"), true) }
        }
        assertEquals(first, dao.getById("one"))
        assertThrows(WorkspaceBatchRowCountException::class.java) {
            runBlocking { dao.deleteByIdsAtomically(setOf("one", "missing")) }
        }
        assertEquals(first, dao.getById("one"))
        assertEquals(second, dao.getById("two"))
    }

    @Test fun batchDeleteRemovesAllRowsAndEmptyInputIsRejected() = runBlocking {
        dao.insert(entity("one", "One", 1, 10))
        dao.insert(entity("two", "Two", 2, 20))
        dao.deleteByIdsAtomically(setOf("one", "two"))
        assertEquals(0, dao.count())
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { dao.deleteByIdsAtomically(emptySet()) }
        }
        Unit
    }

    @Test fun largeCanvasJsonIsPreserved() = runBlocking {
        val largeJson = "x".repeat(100_000)
        dao.insert(entity("large", "Lớn", 1, 10).copy(canvasJson = largeJson))
        assertEquals(largeJson, dao.getById("large")?.canvasJson)
    }

    private fun entity(id: String, name: String, sequence: Long, updated: Long) = WorkspaceEntity(
        id = id,
        name = name,
        canvasJson = "{\"cells\":[]}",
        modifiedSequence = sequence,
        schemaVersion = 1,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = updated,
    )
}
