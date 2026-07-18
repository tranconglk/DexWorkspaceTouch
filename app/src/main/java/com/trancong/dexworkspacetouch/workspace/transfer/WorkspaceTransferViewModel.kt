package com.trancong.dexworkspacetouch.workspace.transfer

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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface WorkspaceTransferState {
    data object Idle : WorkspaceTransferState
    data object PreparingExport : WorkspaceTransferState
    data class ExportReady(val workspaceName: String, val fileName: String, val bytes: ByteArray) : WorkspaceTransferState
    data object ReadingImport : WorkspaceTransferState
    data class ImportPreview(val payload: WorkspaceImportPayload) : WorkspaceTransferState
    data object Importing : WorkspaceTransferState
    data class Completed(val message: String) : WorkspaceTransferState
    data class Error(val failure: WorkspaceTransferFailure) : WorkspaceTransferState
}

class WorkspaceTransferViewModel(
    private val repository: WorkspaceRepository,
    private val serializer: WorkspaceTransferSerializer = DeterministicWorkspaceTransferSerializer(),
    private val clock: WorkspaceClock = SystemWorkspaceClock,
    private val idGenerator: WorkspaceIdGenerator = UuidWorkspaceIdGenerator,
) : ViewModel() {
    var state by mutableStateOf<WorkspaceTransferState>(WorkspaceTransferState.Idle)
        private set
    private var domains: List<Workspace> = emptyList()

    init { viewModelScope.launch { repository.observeSnapshot().collectLatest { domains = it.workspaces } } }

    fun prepareExport(workspaceId: String) {
        if (state !is WorkspaceTransferState.Idle) return
        state = WorkspaceTransferState.PreparingExport
        viewModelScope.launch {
            try {
                val source = repository.getById(workspaceId) ?: throw WorkspaceTransferException(WorkspaceTransferFailure.INVALID_WORKSPACE)
                val bytes = withContext(Dispatchers.Default) {
                    serializer.encode(WorkspaceExport(source.name, source.canvas, clock.nowEpochMillis().coerceAtLeast(0)))
                }
                state = WorkspaceTransferState.ExportReady(source.name, sanitizeWorkspaceFileName(source.name), bytes)
            } catch (error: CancellationException) { throw error }
            catch (error: WorkspaceTransferException) { state = WorkspaceTransferState.Error(error.failure) }
            catch (_: Exception) { state = WorkspaceTransferState.Error(WorkspaceTransferFailure.READ_FAILURE) }
        }
    }

    fun readImport(bytes: ByteArray) {
        if (state !is WorkspaceTransferState.Idle) return
        state = WorkspaceTransferState.ReadingImport
        viewModelScope.launch {
            try { state = WorkspaceTransferState.ImportPreview(withContext(Dispatchers.Default) { serializer.decode(bytes) }) }
            catch (error: CancellationException) { throw error }
            catch (error: WorkspaceTransferException) { state = WorkspaceTransferState.Error(error.failure) }
            catch (_: Exception) { state = WorkspaceTransferState.Error(WorkspaceTransferFailure.INVALID_FORMAT) }
        }
    }

    fun confirmImport() {
        val preview = state as? WorkspaceTransferState.ImportPreview ?: return
        state = WorkspaceTransferState.Importing
        viewModelScope.launch {
            try {
                val now = clock.nowEpochMillis().coerceAtLeast(0)
                val workspace = Workspace(
                    id = uniqueId(),
                    name = WorkspaceImportNamePolicy.nextName(preview.payload.name, domains.map { it.name }),
                    canvas = preview.payload.canvas,
                    modifiedSequence = (domains.maxOfOrNull { it.modifiedSequence } ?: 0L) + 1,
                    schemaVersion = 1,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                    isPinned = false,
                )
                repository.insert(workspace)
                state = WorkspaceTransferState.Completed("Đã nhập workspace ${workspace.name}.")
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { state = WorkspaceTransferState.Error(WorkspaceTransferFailure.WRITE_FAILURE) }
        }
    }

    fun cancelPreview() { if (state is WorkspaceTransferState.ImportPreview) state = WorkspaceTransferState.Idle }
    fun consumeExport() { if (state is WorkspaceTransferState.ExportReady) state = WorkspaceTransferState.Idle }
    fun complete(message: String) { state = WorkspaceTransferState.Completed(message) }
    fun fail(failure: WorkspaceTransferFailure) { state = WorkspaceTransferState.Error(failure) }
    fun dismissFeedback() { if (state is WorkspaceTransferState.Completed || state is WorkspaceTransferState.Error) state = WorkspaceTransferState.Idle }

    private suspend fun uniqueId(): String {
        repeat(100) { val id = idGenerator.newId(); if (id.isNotBlank() && !repository.exists(id)) return id }
        throw IllegalStateException("Unable to generate transfer workspace ID")
    }

    companion object {
        fun factory(repository: WorkspaceRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = WorkspaceTransferViewModel(repository) as T
        }
    }
}
