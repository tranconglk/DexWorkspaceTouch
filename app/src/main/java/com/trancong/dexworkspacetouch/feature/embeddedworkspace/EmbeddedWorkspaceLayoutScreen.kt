package com.trancong.dexworkspacetouch.feature.embeddedworkspace

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trancong.dexworkspacetouch.workspace.execution.embedded.layout.EmbeddedWorkspaceLayoutMapper
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.AndroidEmbeddedWorkspaceSessionFactory
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspacePreflight
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunner
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.PROOF_EMBEDDED_WORKSPACE_TIMEOUTS
import com.trancong.dexworkspacetouch.workspace.launcher.WorkspaceLaunchRequestFactory
import com.trancong.dexworkspacetouch.workspace.persistence.repository.WorkspaceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbeddedWorkspaceLayoutScreen(
    activity: Activity,
    workspaceId: String,
    repository: WorkspaceRepository,
    requestFactory: WorkspaceLaunchRequestFactory,
    onBack: () -> Unit,
) {
    var loadResult by remember(workspaceId) { mutableStateOf<EmbeddedWorkspaceLayoutLoadResult?>(null) }
    LaunchedEffect(workspaceId, repository, requestFactory) {
        loadResult = EmbeddedWorkspaceLayoutLoader(repository, requestFactory).load(workspaceId)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Embedded Workspace Layout (Experimental/Frozen)") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        when (val result = loadResult) {
            null -> Text("Loading workspace $workspaceId", Modifier.padding(padding))
            is EmbeddedWorkspaceLayoutLoadResult.MissingWorkspace ->
                Text("Workspace not found: ${result.workspaceId}", Modifier.padding(padding))
            is EmbeddedWorkspaceLayoutLoadResult.LaunchNotReady ->
                Text("Workspace not ready: ${result.reason}", Modifier.padding(padding))
            is EmbeddedWorkspaceLayoutLoadResult.Ready -> {
                val geometry = remember(result.plan) {
                    EmbeddedWorkspaceGeometrySnapshot.resolve(result.plan, EmbeddedWorkspaceProofPlan.geometryPolicy)
                }
                when (geometry) {
                    is GeometrySnapshotResult.GeometryRejected ->
                        Text("Geometry rejected for ${geometry.sourceCellId}: ${geometry.message}", Modifier.padding(padding))
                    is GeometrySnapshotResult.Ready ->
                        EmbeddedWorkspaceLayoutRunContent(
                            activity, geometry.snapshot, Modifier.padding(padding),
                        )
                }
            }
        }
    }
}

@Composable
private fun EmbeddedWorkspaceLayoutRunContent(
    activity: Activity,
    snapshot: EmbeddedWorkspaceGeometrySnapshot,
    modifier: Modifier,
) {
    val scope = remember(activity, snapshot) { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val coordinator = remember(activity, snapshot, scope) {
        val runner = EmbeddedWorkspaceRunner(
            preflight = EmbeddedWorkspacePreflight(snapshot.asPolicy()),
            sessionFactory = AndroidEmbeddedWorkspaceSessionFactory(activity.applicationContext),
            timeoutPolicy = PROOF_EMBEDDED_WORKSPACE_TIMEOUTS,
            scope = scope,
            dispatcher = Dispatchers.Main.immediate,
        )
        EmbeddedWorkspaceRendererCoordinator(
            snapshot,
            EmbeddedWorkspaceLayoutMapper(),
            EmbeddedWorkspaceRunnerController(snapshot.planSnapshot, runner),
        )
    }
    val rendererState by coordinator.state.collectAsState()
    val controllerState by coordinator.controller.state.collectAsState()
    DisposableEffect(coordinator, scope) {
        onDispose { scope.launch { coordinator.close() }.invokeOnCompletion { scope.cancel() } }
    }
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Workspace: ${snapshot.planSnapshot.workspaceName} (${snapshot.planSnapshot.workspaceId})")
        Text("Runner: ${controllerState.phase} | Latest: ${controllerState.latestResult ?: "None"}")
        Text("Viewport: ${rendererState.layout?.viewport ?: "Not measured"} | Failure: ${rendererState.failure ?: "None"}")
        rendererState.layout?.panes?.forEach { pane ->
            Text("${pane.sourceCellId}: ${pane.pixelBounds} | Surface: ${rendererState.surfaceValidity[pane.sourceCellId]}")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { scope.launch { coordinator.start() } },
                enabled = rendererState.canStart,
            ) { Text("Start") }
            OutlinedButton(
                onClick = { scope.launch { coordinator.stop() } },
                enabled = controllerState.canStop,
            ) { Text("Stop") }
        }
        EmbeddedWorkspaceRenderer(coordinator, scope, Modifier.fillMaxWidth().weight(1f))
    }
}
