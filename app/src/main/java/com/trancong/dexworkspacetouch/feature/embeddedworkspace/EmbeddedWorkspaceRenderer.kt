package com.trancong.dexworkspacetouch.feature.embeddedworkspace

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.trancong.dexworkspacetouch.feature.embeddedapp.pressureFor
import com.trancong.dexworkspacetouch.feature.embeddedapp.virtualAction
import com.trancong.dexworkspacetouch.workspace.execution.embedded.layout.EmbeddedWorkspaceViewport
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.AndroidEmbeddedWorkspaceExecutionSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun EmbeddedWorkspaceRenderer(
    coordinator: EmbeddedWorkspaceRendererCoordinator,
    runnerScope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val state by coordinator.state.collectAsState()
    var viewport by remember(coordinator) { mutableStateOf(EmbeddedWorkspaceViewport(0, 0)) }
    LaunchedEffect(coordinator, viewport) { coordinator.onViewportChanged(viewport) }
    val panes = state.layout?.panes.orEmpty()
    Layout(
        content = {
            panes.forEach { pane ->
                key(coordinator.generationToken, pane.sourceCellId) {
                    EmbeddedWorkspaceSurfacePane(coordinator, runnerScope, pane.sourceCellId)
                }
            }
        },
        modifier = modifier.fillMaxSize().clipToBounds().onSizeChanged {
            viewport = EmbeddedWorkspaceViewport(it.width, it.height)
        },
    ) { measurables, constraints ->
        val placeables = measurables.mapIndexed { index, measurable ->
            val rect = panes[index].pixelBounds
            measurable.measure(
                constraints.copy(
                    minWidth = rect.width, maxWidth = rect.width,
                    minHeight = rect.height, maxHeight = rect.height,
                ),
            )
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                val rect = panes[index].pixelBounds
                placeable.place(rect.left, rect.top)
            }
        }
    }
}

@Composable
private fun EmbeddedWorkspaceSurfacePane(
    coordinator: EmbeddedWorkspaceRendererCoordinator,
    runnerScope: CoroutineScope,
    sourceCellId: String,
) {
    val context = LocalContext.current
    val host = remember(coordinator.generationToken, sourceCellId, context) {
        PaneSurfaceHost(context, coordinator, runnerScope, sourceCellId)
    }
    DisposableEffect(host) { onDispose { host.dispose() } }
    AndroidView(factory = { host.view }, modifier = Modifier.fillMaxSize())
}

private class PaneSurfaceHost(
    context: android.content.Context,
    coordinator: EmbeddedWorkspaceRendererCoordinator,
    private val runnerScope: CoroutineScope,
    sourceCellId: String,
) {
    val view = SurfaceView(context)
    private val bridge = EmbeddedWorkspacePaneEventBridge(coordinator, sourceCellId, view)
    private var currentSurface: Surface? = null
    private val callback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) = available(holder)

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) =
            available(holder)

        private fun available(holder: SurfaceHolder) {
            val surface = holder.surface
            currentSurface = surface
            runnerScope.launch {
                bridge.onSurfaceAvailable(surface, AndroidEmbeddedWorkspaceExecutionSurface(surface))
            }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            val surface = currentSurface ?: holder.surface
            currentSurface = null
            runnerScope.launch { bridge.onSurfaceDestroyed(surface) }
        }
    }

    init {
        val geometry = coordinator.geometrySnapshot.geometryFor(sourceCellId)
        view.holder.setFixedSize(geometry.width, geometry.height)
        view.holder.addCallback(callback)
        view.setOnTouchListener { touchedView, event ->
            if (!coordinator.state.value.touchEnabled || touchedView.width <= 0 || touchedView.height <= 0) {
                return@setOnTouchListener false
            }
            val action = runCatching { virtualAction(event.actionMasked) }
                .getOrElse { return@setOnTouchListener false }
            val x = event.x
            val y = event.y
            val width = touchedView.width
            val height = touchedView.height
            val pressure = pressureFor(event.actionMasked, event.pressure)
            val timeNanos = event.eventTime * 1_000_000L
            runnerScope.launch { bridge.sendLocalTouch(action, x, y, width, height, pressure, timeNanos) }
            true
        }
    }

    fun dispose() {
        view.holder.removeCallback(callback)
        view.setOnTouchListener(null)
        currentSurface?.let { surface -> runnerScope.launch { bridge.onSurfaceDestroyed(surface) } }
        currentSurface = null
    }
}
