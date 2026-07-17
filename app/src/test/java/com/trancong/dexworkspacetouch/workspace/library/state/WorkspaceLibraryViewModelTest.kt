package com.trancong.dexworkspacetouch.workspace.library.state

import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.assignApp
import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import com.trancong.dexworkspacetouch.workspace.persistence.domain.WorkspacePersistenceException
import com.trancong.dexworkspacetouch.workspace.persistence.repository.WorkspaceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceLibraryViewModelTest {
    @Test fun `initial loading remains visible before first emission`() {
        val repository = FakeRepository().apply { holdObserve = true }
        val state = viewModel(repository)
        assertTrue(state.isLoading)
        assertTrue(state.workspaces.isEmpty())
    }

    @Test fun `initial state loads and empty emission finishes loading`() {
        val state = viewModel()
        assertFalse(state.isLoading)
        assertTrue(state.workspaces.isEmpty())
    }

    @Test fun `observe maps persisted workspaces without resorting`() {
        val repository = FakeRepository(listOf(workspace("b", "Second", 2), workspace("a", "First", 1)))
        val state = viewModel(repository)
        assertEquals(listOf("b", "a"), state.workspaces.map { it.id })
    }

    @Test fun `load failure is friendly and retry restores flow`() {
        val repository = FakeRepository().apply { failObserve = true }
        val state = viewModel(repository)
        assertEquals(WorkspaceLibraryPersistenceOperation.LOAD, state.persistenceError?.operation)
        repository.failObserve = false
        state.retryLoad()
        assertFalse(state.isLoading)
        assertNull(state.persistenceError)
    }

    @Test fun `create draft does not insert before save`() {
        val repository = FakeRepository()
        val state = viewModel(repository)
        assertEquals(WorkspaceCanvas.singleCell(), state.createWorkspace())
        assertEquals(0, repository.insertCalls)
    }

    @Test fun `save new workspace inserts domain with generated id and timestamps`() {
        val repository = FakeRepository()
        val state = viewModel(repository, clock = QueueClock(100), ids = QueueIds("uuid-1"))
        state.createWorkspace()
        val saved = state.saveWorkspace(canvas, "Name")
        val domain = repository.current.single()
        assertEquals("uuid-1", saved.id)
        assertEquals(100, domain.createdAtEpochMillis)
        assertEquals(100, domain.updatedAtEpochMillis)
        assertEquals(1, domain.schemaVersion)
        assertEquals(1, repository.insertCalls)
    }

    @Test fun `editing save updates without duplicate and preserves created timestamp`() {
        val original = workspace("id", "Before", 7, created = 50, updated = 60)
        val repository = FakeRepository(listOf(original))
        val state = viewModel(repository, clock = QueueClock(100))
        state.beginEditingWorkspace("id")
        state.saveWorkspace(canvas.assignApp("cell", app), "After")
        val updated = repository.current.single()
        assertEquals(0, repository.insertCalls)
        assertEquals(1, repository.updateCalls)
        assertEquals(50, updated.createdAtEpochMillis)
        assertEquals(100, updated.updatedAtEpochMillis)
        assertEquals(app, updated.canvas.cells.single().app)
    }

    @Test fun `cancel edit does not update repository`() {
        val repository = FakeRepository(listOf(workspace("id", "Name", 1)))
        val state = viewModel(repository)
        state.beginEditingWorkspace("id")
        state.finishEditing()
        assertEquals(0, repository.updateCalls)
    }

    @Test fun `rename trims name preserves canvas and advances metadata`() {
        val original = workspace("id", "Before", 3, created = 10, updated = 20)
        val repository = FakeRepository(listOf(original))
        val state = viewModel(repository, clock = QueueClock(20))
        state.selectWorkspace("id")
        state.renameWorkspace("id", "  Renamed  ")
        val renamed = repository.current.single()
        assertEquals("Renamed", renamed.name)
        assertSame(original.canvas, renamed.canvas)
        assertEquals(10, renamed.createdAtEpochMillis)
        assertEquals(21, renamed.updatedAtEpochMillis)
        assertEquals("id", state.selectedWorkspaceId)
    }

    @Test fun `delete removes persisted workspace and clears selection from emission`() {
        val repository = FakeRepository(listOf(workspace("id", "Name", 1)))
        val state = viewModel(repository)
        state.selectWorkspace("id")
        state.deleteWorkspace("id")
        assertTrue(repository.current.isEmpty())
        assertNull(state.selectedWorkspaceId)
    }

    @Test fun `external removal clears only missing selected id`() {
        val first = workspace("first", "First", 1)
        val second = workspace("second", "Second", 2)
        val repository = FakeRepository(listOf(first, second))
        val state = viewModel(repository)
        state.selectWorkspace("second")
        repository.emit(listOf(first))
        assertNull(state.selectedWorkspaceId)
    }

    @Test fun `room emission keeps selection when selected id still exists`() {
        val first = workspace("first", "First", 1)
        val repository = FakeRepository(listOf(first))
        val state = viewModel(repository)
        state.selectWorkspace("first")
        repository.emit(listOf(first.copy(name = "Updated", modifiedSequence = 2)))
        assertEquals("first", state.selectedWorkspaceId)
    }

    @Test fun `default names and modified sequence are deterministic across delete`() {
        val repository = FakeRepository(listOf(workspace("old", "Workspace 2", 8)))
        val state = viewModel(repository, ids = QueueIds("one", "two", "three"))
        state.createWorkspace()
        assertEquals("Workspace 1", state.saveWorkspace(canvas).name)
        state.finishEditing()
        state.deleteWorkspace("one")
        state.createWorkspace()
        val second = state.saveWorkspace(canvas)
        assertEquals("Workspace 3", second.name)
        assertEquals(10, repository.current.first { it.id == "two" }.modifiedSequence)
    }

    @Test fun `duplicate id maps to save error without crashing`() {
        val repository = FakeRepository().apply { forceDuplicateInsert = true }
        val state = viewModel(repository, ids = QueueIds("new-id"))
        state.createWorkspace()
        state.saveWorkspace(canvas)
        assertEquals(WorkspaceLibraryPersistenceOperation.SAVE, state.persistenceError?.operation)
    }

    @Test fun `double save while insert is active creates one repository write`() {
        val repository = FakeRepository().apply { insertGate = CompletableDeferred() }
        val state = viewModel(repository, ids = QueueIds("only-id", "unused-id"))
        state.createWorkspace()
        val first = state.saveWorkspace(canvas, "Name")
        val second = state.saveWorkspace(canvas, "Other")
        assertEquals(first, second)
        assertEquals(1, repository.insertCalls)
        repository.insertGate?.complete(Unit)
        assertEquals(1, repository.current.size)
    }

    @Test fun `persistence failures map to operation-specific UI state`() {
        val repository = FakeRepository(listOf(workspace("id", "Name", 1))).apply { failMutations = true }
        val state = viewModel(repository)
        state.renameWorkspace("id", "New")
        assertEquals(WorkspaceLibraryPersistenceOperation.RENAME, state.persistenceError?.operation)
        state.deleteWorkspace("id")
        assertEquals(WorkspaceLibraryPersistenceOperation.DELETE, state.persistenceError?.operation)
    }

    @Test fun `process style recreation restores persisted canvas and assignments`() {
        val repository = FakeRepository()
        val first = viewModel(repository, ids = QueueIds("id"))
        first.createWorkspace()
        first.saveWorkspace(canvas.assignApp("cell", app), "Persisted")
        val recreated = viewModel(repository)
        assertEquals("Persisted", recreated.workspaces.single().name)
        assertEquals(app, recreated.workspaces.single().canvas.cells.single().app)
    }

    @Test fun `modified sequence high water mark advances after external emission`() {
        val repository = FakeRepository(listOf(workspace("old", "Old", 50)))
        val state = viewModel(repository, ids = QueueIds("new"))
        state.createWorkspace()
        state.saveWorkspace(canvas, "New")
        assertEquals(51, repository.current.first { it.id == "new" }.modifiedSequence)
    }

    private fun viewModel(
        repository: FakeRepository = FakeRepository(),
        clock: WorkspaceClock = QueueClock(1000),
        ids: WorkspaceIdGenerator = QueueIds("generated"),
    ) = WorkspaceLibraryViewModel(
        repository,
        clock,
        ids,
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )

    private class FakeRepository(initial: List<Workspace> = emptyList()) : WorkspaceRepository {
        private val state = MutableStateFlow(initial)
        var failObserve = false
        var failMutations = false
        var forceDuplicateInsert = false
        var holdObserve = false
        var insertGate: CompletableDeferred<Unit>? = null
        var insertCalls = 0
        var updateCalls = 0
        val current: List<Workspace> get() = state.value

        override fun observeAll(): Flow<List<Workspace>> = when {
            failObserve -> flow { throw WorkspacePersistenceException.DatabaseFailure("load") }
            holdObserve -> flow { awaitCancellation() }
            else -> state
        }
        override suspend fun getById(id: String) = current.firstOrNull { it.id == id }
        override suspend fun insert(workspace: Workspace) {
            if (failMutations) throw WorkspacePersistenceException.DatabaseFailure("insert")
            if (forceDuplicateInsert) throw WorkspacePersistenceException.DuplicateId(workspace.id)
            if (current.any { it.id == workspace.id }) throw WorkspacePersistenceException.DuplicateId(workspace.id)
            insertCalls++
            insertGate?.await()
            state.value = listOf(workspace) + current
        }
        override suspend fun update(workspace: Workspace) {
            if (failMutations) throw WorkspacePersistenceException.DatabaseFailure("update")
            if (current.none { it.id == workspace.id }) throw WorkspacePersistenceException.DatabaseFailure("missing")
            updateCalls++
            state.value = current.map { if (it.id == workspace.id) workspace else it }
        }
        override suspend fun deleteById(id: String) {
            if (failMutations) throw WorkspacePersistenceException.DatabaseFailure("delete")
            state.value = current.filterNot { it.id == id }
        }
        override suspend fun exists(id: String) = current.any { it.id == id }
        override suspend fun count() = current.size
        fun emit(values: List<Workspace>) { state.value = values }
    }

    private class QueueClock(vararg values: Long) : WorkspaceClock {
        private val queue = ArrayDeque(values.toList())
        private var last = values.lastOrNull() ?: 0L
        override fun nowEpochMillis(): Long = queue.removeFirstOrNull()?.also { last = it } ?: last
    }

    private class QueueIds(vararg ids: String) : WorkspaceIdGenerator {
        private val queue = ArrayDeque(ids.toList())
        override fun newId(): String = queue.removeFirstOrNull() ?: "fallback-${System.nanoTime()}"
    }

    private companion object {
        val canvas = WorkspaceCanvas.singleCell()
        val app = AssignedApp("com.example.app", "com.example.app.Main", "Example")
        fun workspace(
            id: String,
            name: String,
            sequence: Long,
            created: Long = 10,
            updated: Long = 20,
        ) = Workspace(id, name, canvas, sequence, 1, created, updated)
    }
}
