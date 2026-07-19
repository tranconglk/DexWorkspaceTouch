package com.trancong.dexworkspacetouch.workspace.librarytransfer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trancong.dexworkspacetouch.workspace.library.state.SystemWorkspaceClock
import com.trancong.dexworkspacetouch.workspace.library.state.UuidWorkspaceIdGenerator
import com.trancong.dexworkspacetouch.workspace.library.state.WorkspaceClock
import com.trancong.dexworkspacetouch.workspace.library.state.WorkspaceIdGenerator
import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import com.trancong.dexworkspacetouch.workspace.persistence.repository.WorkspaceRepository
import com.trancong.dexworkspacetouch.workspace.transfer.WorkspaceImportNamePolicy
import com.trancong.dexworkspacetouch.workspace.transfer.WorkspaceImportPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface WorkspaceLibraryTransferState {
    data object Idle : WorkspaceLibraryTransferState
    data object PreparingBackup : WorkspaceLibraryTransferState
    data class BackupWarning(
        val skippedCount: Int,
        val validCount: Int,
        val selectedExport: Boolean = false,
    ) : WorkspaceLibraryTransferState
    data class BackupReady(
        val fileName: String,
        val bytes: ByteArray,
        val workspaceCount: Int,
        val selectedExport: Boolean = false,
    ) : WorkspaceLibraryTransferState
    data object ReadingRestore : WorkspaceLibraryTransferState
    data class RestorePreview(val preview: WorkspaceLibraryImportPreview) : WorkspaceLibraryTransferState
    data object Restoring : WorkspaceLibraryTransferState
    data class Completed(val message: String) : WorkspaceLibraryTransferState
    data class Error(val failure: WorkspaceLibraryTransferFailure) : WorkspaceLibraryTransferState
}

class WorkspaceLibraryTransferViewModel(
    private val repository: WorkspaceRepository,
    private val serializer: WorkspaceLibraryBundleSerializer = DeterministicWorkspaceLibraryBundleSerializer(),
    private val clock: WorkspaceClock = SystemWorkspaceClock,
    private val idGenerator: WorkspaceIdGenerator = UuidWorkspaceIdGenerator,
    private val coroutineScope: CoroutineScope? = null,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    var state by mutableStateOf<WorkspaceLibraryTransferState>(WorkspaceLibraryTransferState.Idle)
        private set
    private data class PendingBackup(
        val workspaces: List<WorkspaceImportPayload>,
        val selectedExport: Boolean,
    )

    private var pendingBackup: PendingBackup? = null

    fun prepareBackup() {
        if (state !is WorkspaceLibraryTransferState.Idle) return
        state = WorkspaceLibraryTransferState.PreparingBackup
        launchTask {
            try {
                val snapshot = repository.observeSnapshot().first()
                val valid = snapshot.workspaces.map { WorkspaceImportPayload(it.name, it.canvas, it.schemaVersion) }
                if (valid.isEmpty()) throw WorkspaceLibraryTransferException(WorkspaceLibraryTransferFailure.EMPTY_LIBRARY)
                if (snapshot.issues.isNotEmpty()) {
                    pendingBackup = PendingBackup(valid, selectedExport = false)
                    state = WorkspaceLibraryTransferState.BackupWarning(snapshot.issues.size, valid.size)
                } else encodeBackup(valid, selectedExport = false)
            } catch (error: CancellationException) { throw error }
            catch (error: WorkspaceLibraryTransferException) { state = WorkspaceLibraryTransferState.Error(error.failure) }
            catch (_: Exception) { state = WorkspaceLibraryTransferState.Error(WorkspaceLibraryTransferFailure.READ_FAILURE) }
        }
    }

    fun prepareSelectedBackup(selectedWorkspaceIds: Set<String>) {
        if (state !is WorkspaceLibraryTransferState.Idle || selectedWorkspaceIds.isEmpty()) return
        val selectedSnapshot = selectedWorkspaceIds.toSet()
        if (selectedSnapshot.size > WorkspaceLibraryTransferFormat.MaxWorkspaces) {
            state = WorkspaceLibraryTransferState.Error(
                WorkspaceLibraryTransferFailure.TOO_MANY_SELECTED_WORKSPACES,
            )
            return
        }
        state = WorkspaceLibraryTransferState.PreparingBackup
        launchTask {
            try {
                val snapshot = repository.observeSnapshot().first()
                val valid = snapshot.workspaces
                    .asSequence()
                    .filter { it.id in selectedSnapshot }
                    .map { WorkspaceImportPayload(it.name, it.canvas, it.schemaVersion) }
                    .toList()
                if (valid.isEmpty()) {
                    throw WorkspaceLibraryTransferException(
                        WorkspaceLibraryTransferFailure.SELECTED_WORKSPACES_UNAVAILABLE,
                    )
                }
                val validIds = snapshot.workspaces.asSequence().map { it.id }.filter { it in selectedSnapshot }.toSet()
                val issueIds = snapshot.issues.asSequence().map { it.workspaceId }.filter { it in selectedSnapshot }.toSet()
                val skippedCount = (selectedSnapshot - validIds).plus(issueIds).size
                if (skippedCount > 0) {
                    pendingBackup = PendingBackup(valid, selectedExport = true)
                    state = WorkspaceLibraryTransferState.BackupWarning(
                        skippedCount = skippedCount,
                        validCount = valid.size,
                        selectedExport = true,
                    )
                } else {
                    encodeBackup(valid, selectedExport = true)
                }
            } catch (error: CancellationException) { throw error }
            catch (error: WorkspaceLibraryTransferException) {
                state = WorkspaceLibraryTransferState.Error(error.failure)
            } catch (_: Exception) {
                state = WorkspaceLibraryTransferState.Error(WorkspaceLibraryTransferFailure.READ_FAILURE)
            }
        }
    }

    fun continueBackup() {
        val pending = pendingBackup ?: return
        if (state !is WorkspaceLibraryTransferState.BackupWarning) return
        pendingBackup = null
        state = WorkspaceLibraryTransferState.PreparingBackup
        launchTask { try { encodeBackup(pending.workspaces, pending.selectedExport) } catch (error: CancellationException) { throw error }
            catch (error: WorkspaceLibraryTransferException) { state = WorkspaceLibraryTransferState.Error(error.failure) }
            catch (_: Exception) { state = WorkspaceLibraryTransferState.Error(WorkspaceLibraryTransferFailure.WRITE_FAILURE) } }
    }

    fun readRestore(bytes: ByteArray) {
        if (state !is WorkspaceLibraryTransferState.Idle) return
        state = WorkspaceLibraryTransferState.ReadingRestore
        launchTask {
            try {
                val payload = withContext(workerDispatcher) { serializer.decode(bytes) }
                val existing = repository.observeSnapshot().first().workspaces.map { it.name.trim().lowercase(java.util.Locale.ROOT) }.toSet()
                val preview = WorkspaceLibraryImportPreview(
                    payload = payload,
                    totalCellCount = payload.workspaces.sumOf { it.canvas.cells.size },
                    totalAssignedAppCount = payload.workspaces.sumOf { item -> item.canvas.cells.count { it.app != null } },
                    sampleWorkspaceNames = payload.workspaces.take(5).map { it.name },
                    conflictingNameCount = payload.workspaces.count { it.name.trim().lowercase(java.util.Locale.ROOT) in existing },
                )
                state = WorkspaceLibraryTransferState.RestorePreview(preview)
            } catch (error: CancellationException) { throw error }
            catch (error: WorkspaceLibraryTransferException) { state = WorkspaceLibraryTransferState.Error(error.failure) }
            catch (_: Exception) { state = WorkspaceLibraryTransferState.Error(WorkspaceLibraryTransferFailure.INVALID_FORMAT) }
        }
    }

    fun confirmRestore() {
        val preview = (state as? WorkspaceLibraryTransferState.RestorePreview)?.preview ?: return
        state = WorkspaceLibraryTransferState.Restoring
        launchTask {
            try {
                val snapshot = repository.observeSnapshot().first()
                val occupiedNames = snapshot.workspaces.mapTo(mutableListOf()) { it.name }
                val occupiedIds = snapshot.workspaces.mapTo(mutableSetOf()) { it.id }
                val ids = generateIds(preview.workspaceCount, occupiedIds)
                val now = clock.nowEpochMillis().coerceAtLeast(0L)
                val baseSequence = snapshot.workspaces.maxOfOrNull { it.modifiedSequence } ?: 0L
                val restored = preview.payload.workspaces.mapIndexed { index, item ->
                    val name = WorkspaceImportNamePolicy.nextName(item.name, occupiedNames).also(occupiedNames::add)
                    Workspace(ids[index], name, item.canvas, baseSequence + index + 1, 1, now, now, false)
                }
                repository.insertAllAtomically(restored)
                state = WorkspaceLibraryTransferState.Completed("Đã khôi phục ${restored.size} workspace.")
            } catch (error: CancellationException) { throw error }
            catch (error: WorkspaceLibraryTransferException) { state = WorkspaceLibraryTransferState.Error(error.failure) }
            catch (_: Exception) { state = WorkspaceLibraryTransferState.Error(WorkspaceLibraryTransferFailure.WRITE_FAILURE) }
        }
    }

    fun cancel() { if (state is WorkspaceLibraryTransferState.BackupWarning || state is WorkspaceLibraryTransferState.RestorePreview) { pendingBackup = null; state = WorkspaceLibraryTransferState.Idle } }
    fun consumeBackup() { if (state is WorkspaceLibraryTransferState.BackupReady) state = WorkspaceLibraryTransferState.Idle }
    fun complete(message: String) { state = WorkspaceLibraryTransferState.Completed(message) }
    fun fail(failure: WorkspaceLibraryTransferFailure) { state = WorkspaceLibraryTransferState.Error(failure) }
    fun dismissFeedback() { if (state is WorkspaceLibraryTransferState.Completed || state is WorkspaceLibraryTransferState.Error) state = WorkspaceLibraryTransferState.Idle }
    fun prepareForExternalRestore(): Boolean = when (state) {
        WorkspaceLibraryTransferState.PreparingBackup,
        WorkspaceLibraryTransferState.ReadingRestore,
        WorkspaceLibraryTransferState.Restoring -> false
        else -> { pendingBackup = null; state = WorkspaceLibraryTransferState.Idle; true }
    }

    private suspend fun encodeBackup(valid: List<WorkspaceImportPayload>, selectedExport: Boolean) {
        val now = clock.nowEpochMillis().coerceAtLeast(0L)
        val bytes = withContext(workerDispatcher) { serializer.encode(WorkspaceLibraryExport(valid, now)) }
        val fileName = if (selectedExport) selectedWorkspaceBundleFileName(valid.size, now)
        else libraryBackupFileName(now)
        state = WorkspaceLibraryTransferState.BackupReady(fileName, bytes, valid.size, selectedExport)
    }

    private suspend fun generateIds(count: Int, occupied: MutableSet<String>): List<String> {
        val result = mutableListOf<String>()
        repeat(count) {
            var selected: String? = null
            var attempts = 0
            while (selected == null && attempts++ < 100) {
                val candidate = idGenerator.newId()
                if (candidate.isNotBlank() && candidate !in occupied && !repository.exists(candidate)) selected = candidate
            }
            val id = selected ?: throw WorkspaceLibraryTransferException(WorkspaceLibraryTransferFailure.ID_GENERATION_FAILURE)
            occupied += id; result += id
        }
        return result
    }

    private fun launchTask(block: suspend CoroutineScope.() -> Unit) {
        (coroutineScope ?: viewModelScope).launch(block = block)
    }

    companion object {
        fun factory(repository: WorkspaceRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = WorkspaceLibraryTransferViewModel(repository) as T
        }
    }
}
