package com.trancong.dexworkspacetouch.workspace.library.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.library.model.WorkspaceLibraryItem

class WorkspaceLibraryViewModel : ViewModel() {
    var workspaces by mutableStateOf<List<WorkspaceLibraryItem>>(emptyList())
        private set

    var selectedWorkspaceId by mutableStateOf<String?>(null)
        private set

    var editingWorkspaceId by mutableStateOf<String?>(null)
        private set

    private var nextId = 1
    private var nextDefaultName = 1
    private var nextModifiedSequence = 1L

    val isCreatingWorkspace: Boolean get() = editingWorkspaceId == null

    fun selectWorkspace(id: String) {
        require(workspaces.any { it.id == id }) { "Workspace '$id' does not exist" }
        selectedWorkspaceId = id
    }

    fun clearSelection() {
        selectedWorkspaceId = null
    }

    fun selectedWorkspace(): WorkspaceLibraryItem? =
        workspaces.firstOrNull { it.id == selectedWorkspaceId }

    fun createWorkspace(): WorkspaceCanvas {
        editingWorkspaceId = null
        return WorkspaceCanvas.singleCell()
    }

    fun beginEditingWorkspace(id: String): WorkspaceCanvas {
        val workspace = workspaces.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Workspace '$id' does not exist")
        selectedWorkspaceId = id
        editingWorkspaceId = id
        return workspace.canvas
    }

    fun saveWorkspace(canvas: WorkspaceCanvas, requestedName: String? = null): WorkspaceLibraryItem {
        val editingId = editingWorkspaceId
        val existing = editingId?.let { id -> workspaces.firstOrNull { it.id == id } }
        val saved = if (existing == null) {
            WorkspaceLibraryItem(
                id = "workspace-${nextId++}",
                name = requestedName?.trim()?.takeIf(String::isNotEmpty) ?: defaultName(),
                canvas = canvas,
                modifiedSequence = nextModifiedSequence++,
            ).also { item -> workspaces = (workspaces + item).sortedByDescending { it.modifiedSequence } }
        } else {
            existing.copy(
                name = requestedName?.trim()?.takeIf(String::isNotEmpty) ?: existing.name,
                canvas = canvas,
                modifiedSequence = nextModifiedSequence++,
            ).also { item ->
                workspaces = workspaces
                    .map { current -> if (current.id == item.id) item else current }
                    .sortedByDescending { it.modifiedSequence }
            }
        }
        selectedWorkspaceId = saved.id
        editingWorkspaceId = saved.id
        return saved
    }

    private fun defaultName(): String = "Workspace ${nextDefaultName++}"
}
