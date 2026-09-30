package com.trancong.dexworkspacetouch.feature.embeddedworkspace

import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedTouchEvent
import com.trancong.dexworkspacetouch.workspace.execution.embedded.layout.EmbeddedWorkspaceLayoutMapper
import com.trancong.dexworkspacetouch.workspace.execution.embedded.layout.EmbeddedWorkspaceLayoutRejection
import com.trancong.dexworkspacetouch.workspace.execution.embedded.layout.EmbeddedWorkspaceLayoutResult
import com.trancong.dexworkspacetouch.workspace.execution.embedded.layout.EmbeddedWorkspaceViewport
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceExecutionSurface
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunResult
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunnerPhase
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceTouchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EmbeddedWorkspaceRendererState(
    val layout: EmbeddedWorkspaceLayoutResult.Mapped?,
    val failure: EmbeddedWorkspaceLayoutRejection?,
    val transientUnmeasured: Boolean,
    val touchEnabled: Boolean,
    val surfaceValidity: Map<String, Boolean>,
    val canStart: Boolean,
)

class EmbeddedWorkspaceRendererCoordinator(
    val geometrySnapshot: EmbeddedWorkspaceGeometrySnapshot,
    private val mapper: EmbeddedWorkspaceLayoutMapper,
    val controller: EmbeddedWorkspaceRunnerController,
) {
    val generationToken: Any = Any()
    private val sourceIds = geometrySnapshot.planSnapshot.items.map { it.sourceCellId }.toSet()
    private val surfaces = mutableMapOf<String, TrackedSurface>()
    private var layout: EmbeddedWorkspaceLayoutResult.Mapped? = null
    private var failure: EmbeddedWorkspaceLayoutRejection? = null
    private var transientUnmeasured = false
    private var started = false
    private var stopIssuedForLayout = false
    private var closed = false

    private val mutableState = MutableStateFlow(snapshot())
    val state: StateFlow<EmbeddedWorkspaceRendererState> = mutableState.asStateFlow()

    suspend fun onViewportChanged(viewport: EmbeddedWorkspaceViewport) {
        if (closed) return
        if (viewport.widthPx <= 0 || viewport.heightPx <= 0) {
            transientUnmeasured = started && layout != null
            if (!transientUnmeasured) {
                layout = null
                failure = EmbeddedWorkspaceLayoutRejection.InvalidViewport(viewport.widthPx, viewport.heightPx)
            }
            publish()
            return
        }
        acceptLayout(generationToken, mapper.map(geometrySnapshot.planSnapshot, viewport))
    }

    suspend fun acceptLayout(token: Any, result: EmbeddedWorkspaceLayoutResult) {
        if (closed || token !== generationToken) return
        when (result) {
            is EmbeddedWorkspaceLayoutResult.Mapped -> {
                if (result.planSnapshot != geometrySnapshot.planSnapshot ||
                    result.panes.map { it.sourceCellId }.toSet() != sourceIds ||
                    result.panes.size != sourceIds.size
                ) {
                    reject(EmbeddedWorkspaceLayoutRejection.MissingPlanItemCorrelation("plan"))
                    publish()
                    return
                }
                layout = result
                failure = null
                transientUnmeasured = false
            }
            is EmbeddedWorkspaceLayoutResult.Rejected -> reject(result.cause)
        }
        publish()
    }

    suspend fun onSurfaceAvailable(
        token: Any,
        sourceCellId: String,
        viewToken: Any,
        surfaceToken: Any,
        executionSurface: EmbeddedWorkspaceExecutionSurface,
    ) {
        if (closed || token !== generationToken || sourceCellId !in sourceIds || layout == null || failure != null) return
        val previous = surfaces[sourceCellId]
        if (previous != null) {
            if (previous.viewToken !== viewToken || previous.surfaceToken !== surfaceToken ||
                !previous.executionSurface.isValid || !executionSurface.isValid
            ) {
                surfaces.remove(sourceCellId)
                controller.surfaceLost(sourceCellId)
                publish()
            }
            return
        }
        if (!executionSurface.isValid) return
        surfaces[sourceCellId] = TrackedSurface(viewToken, surfaceToken, executionSurface)
        controller.updateHostSlot(sourceCellId, executionSurface)
        publish()
    }

    suspend fun onSurfaceDestroyed(token: Any, sourceCellId: String, viewToken: Any, surfaceToken: Any) {
        if (closed || token !== generationToken) return
        val current = surfaces[sourceCellId] ?: return
        if (current.viewToken !== viewToken || current.surfaceToken !== surfaceToken) return
        surfaces.remove(sourceCellId)
        controller.surfaceLost(sourceCellId)
        publish()
    }

    suspend fun start(): EmbeddedWorkspaceRunResult? {
        if (!state.value.canStart) return null
        started = true
        publish()
        val result = controller.start()
        publish()
        return result
    }

    suspend fun stop(): EmbeddedWorkspaceRunResult? {
        val result = controller.stop()
        publish()
        return result
    }

    suspend fun sendTouch(sourceCellId: String, event: EmbeddedTouchEvent): EmbeddedWorkspaceTouchResult {
        if (!state.value.touchEnabled || sourceCellId !in sourceIds || surfaces[sourceCellId]?.executionSurface?.isValid != true) {
            return EmbeddedWorkspaceTouchResult.Rejected(sourceCellId, "RENDERER_NOT_READY")
        }
        return controller.sendTouch(sourceCellId, event)
    }

    suspend fun close(): EmbeddedWorkspaceRunResult? {
        closed = true
        publish()
        return controller.close()
    }

    private suspend fun reject(cause: EmbeddedWorkspaceLayoutRejection) {
        failure = cause
        transientUnmeasured = false
        if (started && !stopIssuedForLayout) {
            stopIssuedForLayout = true
            publish()
            controller.stop()
        } else if (!started) {
            layout = null
        }
    }

    private fun publish() { mutableState.value = snapshot() }

    private fun snapshot(): EmbeddedWorkspaceRendererState = EmbeddedWorkspaceRendererState(
        layout = layout,
        failure = failure,
        transientUnmeasured = transientUnmeasured,
        touchEnabled = !closed && started && !transientUnmeasured && failure == null && layout != null &&
            controller.state.value.phase == EmbeddedWorkspaceRunnerPhase.ACTIVE,
        surfaceValidity = sourceIds.associateWith { surfaces[it]?.executionSurface?.isValid == true },
        canStart = !closed && !started && !transientUnmeasured && failure == null && layout != null &&
            sourceIds.all { surfaces[it]?.executionSurface?.isValid == true } && controller.state.value.canStart,
    )

    private class TrackedSurface(
        val viewToken: Any,
        val surfaceToken: Any,
        val executionSurface: EmbeddedWorkspaceExecutionSurface,
    )
}
