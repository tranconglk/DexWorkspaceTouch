package com.trancong.dexworkspacetouch.golden

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell
import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import com.trancong.dexworkspacetouch.workspace.persistence.repository.RoomWorkspaceRepository
import com.trancong.dexworkspacetouch.workspace.persistence.repository.WorkspacePersistenceIssue
import com.trancong.dexworkspacetouch.workspace.persistence.room.DexWorkspaceDatabase
import com.trancong.dexworkspacetouch.workspace.persistence.room.WorkspaceEntity
import com.trancong.dexworkspacetouch.workspace.persistence.room.WorkspaceRowNotFoundException
import com.trancong.dexworkspacetouch.workspace.persistence.serialization.DeterministicWorkspaceCanvasJsonSerializer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoldenWorkspacePersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val serializer = DeterministicWorkspaceCanvasJsonSerializer()
    private var database: DexWorkspaceDatabase? = null

    @Before fun cleanBefore() { context.deleteDatabase(DATABASE_NAME) }

    @After fun cleanAfter() {
        database?.close()
        database = null
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test fun insertUpdateRenameDeleteSurviveFileDatabaseReopen() = runBlocking {
        var repository = repository()
        repository.insert(workspace("id", "Tên Việt", 1))
        reopen()
        repository = repository(reuseOpenDatabase = true)
        assertEquals("Tên Việt", repository.getById("id")?.name)

        val updated = workspace("id", "Đã đổi tên", 2).copy(updatedAtEpochMillis = 30)
        repository.update(updated)
        reopen()
        repository = repository(reuseOpenDatabase = true)
        assertEquals(updated, repository.getById("id"))

        repository.deleteById("id")
        reopen()
        repository = repository(reuseOpenDatabase = true)
        assertEquals(null, repository.getById("id"))
    }

    @Test fun malformedRowDoesNotHideValidRowAndIsNotDeleted() = runBlocking {
        val db = openDatabase()
        db.workspaceDao().insert(workspace("valid", "Valid", 1).toEntity())
        db.workspaceDao().insert(workspace("bad", "Bad", 2).toEntity().copy(canvasJson = "malformed"))
        val snapshot = RoomWorkspaceRepository(db.workspaceDao(), serializer).observeSnapshot().first()
        assertEquals(listOf("valid"), snapshot.workspaces.map { it.id })
        assertEquals(listOf(WorkspacePersistenceIssue.CorruptedRow("bad")), snapshot.issues)
        assertEquals(2, db.workspaceDao().count())
    }

    @Test fun unsupportedSchemaIsRetainedWithoutHidingValidWorkspace() = runBlocking {
        val db = openDatabase()
        db.workspaceDao().insert(workspace("valid", "Valid", 1).toEntity())
        db.workspaceDao().insert(workspace("future", "Future", 2).toEntity().copy(schemaVersion = 2))
        val snapshot = RoomWorkspaceRepository(db.workspaceDao(), serializer).observeSnapshot().first()
        assertEquals(listOf("valid"), snapshot.workspaces.map { it.id })
        assertEquals(listOf(WorkspacePersistenceIssue.UnsupportedSchema("future", 2)), snapshot.issues)
        assertEquals(2, db.workspaceDao().count())
    }

    @Test fun unicodeLargeCanvasAndSortRemainDeterministicAfterReopen() = runBlocking {
        val db = openDatabase()
        val largeLabel = "Ứng dụng Việt " + "x".repeat(100_000)
        val largeCanvas = WorkspaceCanvas(
            listOf(
                WorkspaceCell(
                    "cell",
                    NormalizedBounds.FullCanvas,
                    AssignedApp("golden.unicode", "golden.unicode.Main", largeLabel),
                ),
            ),
        )
        db.workspaceDao().insert(workspace("b", "Á Unicode", 2).copy(canvas = largeCanvas).toEntity())
        db.workspaceDao().insert(workspace("a", "Á Unicode", 2).copy(canvas = largeCanvas).toEntity())
        reopen()
        val snapshot = repository(reuseOpenDatabase = true).observeSnapshot().first()
        assertEquals(listOf("a", "b"), snapshot.workspaces.map { it.id })
        assertEquals(largeLabel, snapshot.workspaces.first().canvas.cells.single().app?.label)
    }

    @Test fun failedUpdateRollsBackAndKeepsExistingRow() = runBlocking {
        val db = openDatabase()
        db.workspaceDao().insert(workspace("id", "Before", 1).toEntity())
        assertThrows(WorkspaceRowNotFoundException::class.java) {
            runBlocking { db.workspaceDao().update(workspace("missing", "After", 2).toEntity()) }
        }
        assertEquals("Before", db.workspaceDao().getById("id")?.name)
    }

    @Test fun duplicatedWorkspaceRowsRemainIndependentAfterReopen() = runBlocking {
        var repository = repository()
        val assignedCanvas = WorkspaceCanvas(
            listOf(
                WorkspaceCell(
                    "cell",
                    NormalizedBounds.FullCanvas,
                    AssignedApp("golden.maps", "golden.maps.Main", "Maps"),
                ),
            ),
        )
        val source = workspace("source", "Đi đường", 1).copy(canvas = assignedCanvas)
        val copy = source.copy(
            id = "copy",
            name = "Đi đường (Bản sao)",
            modifiedSequence = 2,
            createdAtEpochMillis = 30,
            updatedAtEpochMillis = 30,
        )
        repository.insert(source)
        repository.insert(copy)
        reopen()
        repository = repository(reuseOpenDatabase = true)
        assertEquals(source.canvas, repository.getById("copy")?.canvas)
        repository.update(copy.copy(name = "Bản sao riêng", modifiedSequence = 3, updatedAtEpochMillis = 40))
        assertEquals("Đi đường", repository.getById("source")?.name)
        assertEquals("Bản sao riêng", repository.getById("copy")?.name)
    }

    private fun repository(reuseOpenDatabase: Boolean = false): RoomWorkspaceRepository {
        val db = if (reuseOpenDatabase) requireNotNull(database) else openDatabase()
        return RoomWorkspaceRepository(db.workspaceDao(), serializer)
    }

    private fun openDatabase(): DexWorkspaceDatabase = Room.databaseBuilder(
        context,
        DexWorkspaceDatabase::class.java,
        DATABASE_NAME,
    ).build().also { database = it }

    private fun reopen() {
        database?.close()
        database = openDatabase()
    }

    private fun Workspace.toEntity() = WorkspaceEntity(
        id, name, serializer.encode(canvas), modifiedSequence, schemaVersion,
        createdAtEpochMillis, updatedAtEpochMillis,
    )

    private fun workspace(id: String, name: String, sequence: Long) = Workspace(
        id, name, WorkspaceCanvas.singleCell(), sequence, 1, 10, 20,
    )

    private companion object { const val DATABASE_NAME = "golden-regression.db" }
}
