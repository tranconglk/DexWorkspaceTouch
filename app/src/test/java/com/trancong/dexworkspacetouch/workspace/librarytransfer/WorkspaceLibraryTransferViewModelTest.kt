package com.trancong.dexworkspacetouch.workspace.librarytransfer

import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.library.state.WorkspaceClock
import com.trancong.dexworkspacetouch.workspace.library.state.WorkspaceIdGenerator
import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import com.trancong.dexworkspacetouch.workspace.persistence.repository.WorkspaceRepository
import com.trancong.dexworkspacetouch.workspace.persistence.repository.WorkspaceRepositorySnapshot
import com.trancong.dexworkspacetouch.workspace.transfer.WorkspaceImportPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceLibraryTransferViewModelTest {
    @Test fun backupReadsLatestSnapshotAndProducesReadyBundle() {
        val repository = FakeRepository(listOf(workspace("old", "B", 1), workspace("new", "A", 2)))
        val viewModel = viewModel(repository)
        viewModel.prepareBackup()
        val ready = viewModel.state as WorkspaceLibraryTransferState.BackupReady
        assertEquals(listOf("A", "B"), DeterministicWorkspaceLibraryBundleSerializer().decode(ready.bytes).workspaces.map { it.name })
    }

    @Test fun previewDoesNotInsertAndConfirmRestoresOneAtomicUnpinnedBatch() {
        val existing = workspace("existing", "Đi đường", 7).copy(isPinned = true)
        val repository = FakeRepository(listOf(existing))
        val serializer = DeterministicWorkspaceLibraryBundleSerializer()
        val bytes = serializer.encode(WorkspaceLibraryExport(listOf(
            WorkspaceImportPayload("Đi đường", WorkspaceCanvas.singleCell(), 1),
            WorkspaceImportPayload("Đi đường", WorkspaceCanvas.singleCell(), 1),
        ), 1))
        val viewModel = viewModel(repository)
        viewModel.readRestore(bytes)
        assertTrue(viewModel.state is WorkspaceLibraryTransferState.RestorePreview)
        assertEquals(0, repository.batchCalls)

        viewModel.confirmRestore()
        assertEquals(1, repository.batchCalls)
        assertEquals(listOf("Đi đường (Đã nhập)", "Đi đường (Đã nhập 2)"), repository.lastBatch.map { it.name })
        assertEquals(listOf("new-1", "new-2"), repository.lastBatch.map { it.id })
        assertTrue(repository.lastBatch.all { !it.isPinned && it.createdAtEpochMillis == 500L && it.updatedAtEpochMillis == 500L })
        assertEquals(listOf(8L, 9L), repository.lastBatch.map { it.modifiedSequence })
        assertEquals("Đi đường", repository.items.value.single().name)

        viewModel.confirmRestore()
        assertEquals(1, repository.batchCalls)
    }

    @Test fun emptyLibraryBackupFailsClearly() {
        val viewModel = viewModel(FakeRepository(emptyList()))
        viewModel.prepareBackup()
        assertEquals(WorkspaceLibraryTransferFailure.EMPTY_LIBRARY, (viewModel.state as WorkspaceLibraryTransferState.Error).failure)
    }

    private fun viewModel(repository: FakeRepository): WorkspaceLibraryTransferViewModel {
        var id = 0
        return WorkspaceLibraryTransferViewModel(
            repository = repository,
            clock = WorkspaceClock { 500L },
            idGenerator = WorkspaceIdGenerator { "new-${++id}" },
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
            workerDispatcher = Dispatchers.Unconfined,
        )
    }

    private fun workspace(id: String, name: String, sequence: Long) = Workspace(id, name, WorkspaceCanvas.singleCell(), sequence, 1, 10, 20)

    private class FakeRepository(initial: List<Workspace>) : WorkspaceRepository {
        val items = MutableStateFlow(initial)
        var batchCalls = 0
        var lastBatch = emptyList<Workspace>()
        override fun observeAll(): Flow<List<Workspace>> = items
        override fun observeSnapshot(): Flow<WorkspaceRepositorySnapshot> = MutableStateFlow(WorkspaceRepositorySnapshot(items.value))
        override suspend fun getById(id: String) = items.value.firstOrNull { it.id == id }
        override suspend fun insert(workspace: Workspace) { items.value += workspace }
        override suspend fun insertAllAtomically(workspaces: List<Workspace>) { batchCalls++; lastBatch = workspaces }
        override suspend fun update(workspace: Workspace) = Unit
        override suspend fun deleteById(id: String) = Unit
        override suspend fun exists(id: String) = items.value.any { it.id == id }
        override suspend fun count() = items.value.size
    }
}
