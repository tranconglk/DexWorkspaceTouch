package com.trancong.dexworkspacetouch.feature.physicalbutton

import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import com.trancong.dexworkspacetouch.workspace.persistence.repository.WorkspaceRepository
import com.trancong.dexworkspacetouch.workspace.close.PackageCloseBackend
import com.trancong.dexworkspacetouch.workspace.close.PackageCloseRequest
import com.trancong.dexworkspacetouch.workspace.close.PackageCloseStatus
import kotlinx.coroutines.CancellationException

enum class PhysicalButtonActionResult {
    DISPATCHED, CLOSE_COMPLETE, CLOSE_NO_OP, CLOSE_PARTIAL, CLOSE_BACKEND_UNAVAILABLE, CLOSE_UNSUPPORTED,
    NOT_CONFIGURED, WORKSPACE_UNAVAILABLE, BLOCKED, FAILED,
}

class PhysicalButtonActionDispatcher(
    private val preferences: PhysicalButtonPreferences,
    private val repository: WorkspaceRepository,
    private val packageCloseBackend: PackageCloseBackend,
    private val canLaunch: suspend () -> Boolean,
    private val launchWorkspace: (Workspace) -> Boolean,
) {
    suspend fun dispatch(
        expectedDeviceDescriptor: String? = preferences.configuration.value.deviceDescriptor,
        keyCode: Int = 24,
        expectedWorkspaceId: String? = preferences.configuration.value.workspaceForKey(keyCode),
        gesture: PhysicalButtonGesture = PhysicalButtonGesture.SHORT_PRESS,
        expectedConfiguration: PhysicalButtonConfiguration? = null,
    ): PhysicalButtonActionResult {
        val configuration = preferences.configuration.value
        val workspaceId = configuration.workspaceForKey(keyCode)
        if (!configuration.enabled || workspaceId == null || configuration.deviceDescriptor.isNullOrBlank() ||
            preferences.bindingRequested.value) return PhysicalButtonActionResult.NOT_CONFIGURED
        if (configuration.deviceDescriptor != expectedDeviceDescriptor) return PhysicalButtonActionResult.BLOCKED
        if (workspaceId != expectedWorkspaceId) return PhysicalButtonActionResult.BLOCKED
        if (expectedConfiguration != null && configuration != expectedConfiguration) return PhysicalButtonActionResult.BLOCKED
        return try {
            val workspace = repository.getById(workspaceId)?.takeIf { it.id == workspaceId }
                ?: return PhysicalButtonActionResult.WORKSPACE_UNAVAILABLE
            if (preferences.configuration.value != configuration || preferences.bindingRequested.value) {
                return PhysicalButtonActionResult.BLOCKED
            }
            if (gesture == PhysicalButtonGesture.LONG_PRESS) {
                // Snapshot package distinct; backend giữ guard DWT và quyết định kết quả terminal.
                val report = packageCloseBackend.closePackages(PackageCloseRequest.fromWorkspace(workspace))
                return when (report.status) {
                    PackageCloseStatus.COMPLETE -> PhysicalButtonActionResult.CLOSE_COMPLETE
                    PackageCloseStatus.NO_OP -> PhysicalButtonActionResult.CLOSE_NO_OP
                    PackageCloseStatus.PARTIAL -> PhysicalButtonActionResult.CLOSE_PARTIAL
                    PackageCloseStatus.BACKEND_UNAVAILABLE -> PhysicalButtonActionResult.CLOSE_BACKEND_UNAVAILABLE
                    PackageCloseStatus.UNSUPPORTED -> PhysicalButtonActionResult.CLOSE_UNSUPPORTED
                }
            }
            if (!canLaunch() || preferences.configuration.value != configuration || preferences.bindingRequested.value) {
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
