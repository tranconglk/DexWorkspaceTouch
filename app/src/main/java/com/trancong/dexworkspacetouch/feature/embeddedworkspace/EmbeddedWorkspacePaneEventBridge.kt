package com.trancong.dexworkspacetouch.feature.embeddedworkspace

import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedTouchEvent
import com.trancong.dexworkspacetouch.workspace.execution.embedded.layout.EmbeddedPaneTouchMapper
import com.trancong.dexworkspacetouch.workspace.execution.embedded.layout.PaneTouchMappingResult
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceExecutionSurface
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceTouchResult

class EmbeddedWorkspacePaneEventBridge(
    private val coordinator: EmbeddedWorkspaceRendererCoordinator,
    private val sourceCellId: String,
    private val viewToken: Any,
    private val touchMapper: EmbeddedPaneTouchMapper = EmbeddedPaneTouchMapper(),
) {
    private val generationToken = coordinator.generationToken

    suspend fun onSurfaceAvailable(surfaceToken: Any, executionSurface: EmbeddedWorkspaceExecutionSurface) {
        coordinator.onSurfaceAvailable(generationToken, sourceCellId, viewToken, surfaceToken, executionSurface)
    }

    suspend fun onSurfaceDestroyed(surfaceToken: Any) {
        coordinator.onSurfaceDestroyed(generationToken, sourceCellId, viewToken, surfaceToken)
    }

    suspend fun sendLocalTouch(
        action: Int,
        x: Float,
        y: Float,
        hostWidth: Int,
        hostHeight: Int,
        pressure: Float,
        eventTimeNanos: Long,
    ): Boolean {
        if (!coordinator.state.value.touchEnabled) return false
        val geometry = coordinator.geometrySnapshot.geometryFor(sourceCellId)
        val point = touchMapper.map(x, y, hostWidth, hostHeight, geometry.width, geometry.height)
        if (point !is PaneTouchMappingResult.Mapped) return false
        return coordinator.sendTouch(
            sourceCellId,
            EmbeddedTouchEvent(action, point.x, point.y, pressure, eventTimeNanos),
        ) == EmbeddedWorkspaceTouchResult.Accepted
    }
}
