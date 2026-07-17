package com.trancong.dexworkspacetouch.workspace.library.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.library.model.WorkspaceLibraryItem
import com.trancong.dexworkspacetouch.workspace.library.model.toLibraryItem
import com.trancong.dexworkspacetouch.workspace.library.model.toDomainWorkspace
import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import com.trancong.dexworkspacetouch.workspace.persistence.repository.WorkspaceRepository
import com.trancong.dexworkspacetouch.workspace.persistence.repository.WorkspacePersistenceIssue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

class WorkspaceLibraryViewModel(
    private val repository: WorkspaceRepository,
    private val clock: WorkspaceClock = SystemWorkspaceClock,
    private val idGenerator: WorkspaceIdGenerator = UuidWorkspaceIdGenerator,
    private val suppliedScope: CoroutineScope? = null,
) : ViewModel() {
    var workspaces by mutableStateOf<List<WorkspaceLibraryItem>>(emptyList())
        private set
    var selectedWorkspaceId by mutableStateOf<String?>(null)
        private set
    var editingWorkspaceId by mutableStateOf<String?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var persistenceError by mutableStateOf<WorkspaceLibraryPersistenceError?>(null)
        private set
    var persistenceIssues by mutableStateOf<List<WorkspacePersistenceIssue>>(emptyList())
        private set

    private val scope: CoroutineScope get() = suppliedScope ?: viewModelScope
    private var observedDomains: List<Workspace> = emptyList()
    private var observationJob: Job? = null
    private var mutationJob: Job? = null
    private var nextModifiedSequence = 1L
    private var pendingSave: WorkspaceLibraryItem? = null
    private val writeMutex = Mutex()
    private val issuedDefaultNameNumbers = mutableSetOf<Int>()

    val isCreatingWorkspace: Boolean get() = editingWorkspaceId == null

    init {
        observeLibrary()
    }

    fun retryLoad() {
        persistenceError = null
        isLoading = true
        observeLibrary()
    }

    fun dismissPersistenceError() {
        persistenceError = null
    }

    fun selectWorkspace(id: String) {
        require(workspaces.any { it.id == id }) { "Workspace '$id' does not exist" }
        selectedWorkspaceId = id
    }

    fun clearSelection() {
        selectedWorkspaceId = null
    }

    fun finishEditing() {
        editingWorkspaceId = null
    }

    fun selectedWorkspace(): WorkspaceLibraryItem? =
        workspaces.firstOrNull { it.id == selectedWorkspaceId }

    fun createWorkspace(): WorkspaceCanvas {
        editingWorkspaceId = null
        return WorkspaceCanvas.singleCell()
    }

    fun beginEditingWorkspace(id: String): WorkspaceCanvas {
        val workspace = requireWorkspace(id)
        selectedWorkspaceId = id
        editingWorkspaceId = id
        return workspace.canvas
    }

    fun renameWorkspace(id: String, newName: String): WorkspaceLibraryItem {
        val existing = requireDomain(id)
        val trimmedName = newName.trim()
        require(trimmedName.isNotEmpty()) { "Workspace name must not be blank" }
        val renamed = existing.toLibraryItem().copy(
            name = trimmedName,
            modifiedSequence = takeModifiedSequence(),
        ).toDomainWorkspace(
            schemaVersion = existing.schemaVersion,
            createdAtEpochMillis = existing.createdAtEpochMillis,
            updatedAtEpochMillis = nextUpdatedAt(existing),
        )
        runMutation(WorkspaceLibraryPersistenceOperation.RENAME) { repository.update(renamed) }
        return renamed.toLibraryItem()
    }

    fun deleteWorkspace(id: String) {
        requireDomain(id)
        check(editingWorkspaceId != id) { "Workspace '$id' is currently being edited" }
        runMutation(WorkspaceLibraryPersistenceOperation.DELETE) { repository.deleteById(id) }
    }

    fun saveWorkspace(
        canvas: WorkspaceCanvas,
        requestedName: String? = null,
        onPersisted: () -> Unit = {},
    ): WorkspaceLibraryItem {
        if (mutationJob?.isActive == true && pendingSave != null) return requireNotNull(pendingSave)
        val editingId = editingWorkspaceId
        val existing = editingId?.let(::domainOrNull)
        val now = nonNegativeNow()
        val saved = if (existing == null) {
            WorkspaceLibraryItem(
                id = uniqueId(),
                name = requestedName?.trim()?.takeIf(String::isNotEmpty) ?: defaultName(),
                canvas = canvas,
                modifiedSequence = takeModifiedSequence(),
            ).toDomainWorkspace(
                schemaVersion = CURRENT_SCHEMA_VERSION,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            )
        } else {
            existing.toLibraryItem().copy(
                name = requestedName?.trim()?.takeIf(String::isNotEmpty) ?: existing.name,
                canvas = canvas,
                modifiedSequence = takeModifiedSequence(),
            ).toDomainWorkspace(
                schemaVersion = existing.schemaVersion,
                createdAtEpochMillis = existing.createdAtEpochMillis,
                updatedAtEpochMillis = nextUpdatedAt(existing),
            )
        }
        val item = saved.toLibraryItem()
        pendingSave = item
        selectedWorkspaceId = item.id
        editingWorkspaceId = item.id
        runMutation(WorkspaceLibraryPersistenceOperation.SAVE, onSuccess = onPersisted) {
            if (existing == null) repository.insert(saved) else repository.update(saved)
        }
        return item
    }

    private fun observeLibrary() {
        observationJob?.cancel()
        observationJob = scope.launch {
            repository.observeSnapshot()
                .catch { error ->
                    if (error is CancellationException) throw error
                    isLoading = false
                    persistenceError = WorkspaceLibraryPersistenceError(
                        WorkspaceLibraryPersistenceOperation.LOAD,
                    )
                }
                .collect { snapshot ->
                    val domains = snapshot.workspaces
                    observedDomains = domains
                    persistenceIssues = snapshot.issues
                    workspaces = domains.map(Workspace::toLibraryItem)
                    val highWaterMark = domains.maxOfOrNull(Workspace::modifiedSequence) ?: 0L
                    nextModifiedSequence = max(nextModifiedSequence, highWaterMark + 1)
                    domains.mapNotNullTo(issuedDefaultNameNumbers) { workspace ->
                        DEFAULT_NAME_PATTERN.matchEntire(workspace.name)
                            ?.groupValues?.get(1)?.toIntOrNull()
                    }
                    if (selectedWorkspaceId !in domains.map(Workspace::id)) selectedWorkspaceId = null
                    isLoading = false
                    if (persistenceError?.operation == WorkspaceLibraryPersistenceOperation.LOAD) {
                        persistenceError = null
                    }
                }
        }
    }

    private fun runMutation(
        operation: WorkspaceLibraryPersistenceOperation,
        onSuccess: () -> Unit = {},
        block: suspend () -> Unit,
    ) {
        persistenceError = null
        mutationJob = scope.launch {
            try {
                writeMutex.withLock { block() }
                onSuccess()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                persistenceError = WorkspaceLibraryPersistenceError(operation)
            } finally {
                pendingSave = null
            }
        }
    }

    private fun uniqueId(): String {
        repeat(MAX_ID_ATTEMPTS) {
            val id = idGenerator.newId()
            require(id.isNotBlank()) { "Generated workspace ID must not be blank" }
            if (observedDomains.none { it.id == id }) return id
        }
        throw IllegalStateException("Unable to generate a unique workspace ID")
    }

    private fun defaultName(): String {
        val used = observedDomains.mapNotNull { workspace ->
            DEFAULT_NAME_PATTERN.matchEntire(workspace.name)?.groupValues?.get(1)?.toIntOrNull()
        }.toSet() + issuedDefaultNameNumbers
        val number = (used.maxOrNull() ?: 0) + 1
        issuedDefaultNameNumbers += number
        return "Workspace $number"
    }

    private fun takeModifiedSequence(): Long = nextModifiedSequence++
    private fun nonNegativeNow(): Long = clock.nowEpochMillis().coerceAtLeast(0L)
    private fun nextUpdatedAt(existing: Workspace): Long = max(
        nonNegativeNow(),
        max(existing.createdAtEpochMillis, existing.updatedAtEpochMillis + 1),
    )

    private fun requireWorkspace(id: String): WorkspaceLibraryItem = requireDomain(id).toLibraryItem()
    private fun requireDomain(id: String): Workspace = domainOrNull(id)
        ?: throw IllegalArgumentException("Workspace '$id' does not exist")
    private fun domainOrNull(id: String): Workspace? = observedDomains.firstOrNull { it.id == id }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        private const val MAX_ID_ATTEMPTS = 100
        private val DEFAULT_NAME_PATTERN = Regex("Workspace ([1-9][0-9]*)")

        fun factory(repository: WorkspaceRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(WorkspaceLibraryViewModel::class.java))
                    return WorkspaceLibraryViewModel(repository) as T
                }
            }
    }
}
