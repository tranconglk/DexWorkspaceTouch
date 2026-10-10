package com.trancong.dexworkspacetouch.feature.physicalbutton

import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import com.trancong.dexworkspacetouch.workspace.persistence.repository.WorkspaceRepository
import kotlinx.coroutines.CancellationException

enum class PhysicalButtonActionResult { DISPATCHED, NOT_CONFIGURED, WORKSPACE_UNAVAILABLE, BLOCKED, FAILED }

class PhysicalButtonActionDispatcher(
    private val preferences: PhysicalButtonPreferences,
    private val repository: WorkspaceRepository,
    private val canLaunch: suspend () -> Boolean,
    private val launchWorkspace: (Workspace) -> Boolean,
) {
    suspend fun dispatch(): PhysicalButtonActionResult {
        val configuration = preferences.configuration.value
        val workspaceId = configuration.workspaceId?.takeIf(String::isNotBlank)
        if (!configuration.enabled || workspaceId == null) return PhysicalButtonActionResult.NOT_CONFIGURED
        return try {
            val workspace = repository.getById(workspaceId)?.takeIf { it.id == workspaceId }
                ?: return PhysicalButtonActionResult.WORKSPACE_UNAVAILABLE
            if (!canLaunch() || preferences.configuration.value != configuration) {
                return PhysicalButtonActionResult.BLOCKED
            }
            if (launchWorkspace(workspace)) PhysicalButtonActionResult.DISPATCHED
            else PhysicalButtonActionResult.BLOCKED
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            PhysicalButtonActionResult.FAILED
        }
    }
}
