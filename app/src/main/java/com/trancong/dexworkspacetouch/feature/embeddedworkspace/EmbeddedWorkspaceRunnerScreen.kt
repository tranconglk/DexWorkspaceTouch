package com.trancong.dexworkspacetouch.feature.embeddedworkspace

import android.app.Activity
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedTouchEvent
import com.trancong.dexworkspacetouch.feature.embeddedapp.mapPoint
import com.trancong.dexworkspacetouch.feature.embeddedapp.pressureFor
import com.trancong.dexworkspacetouch.feature.embeddedapp.virtualAction
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlanner
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.AndroidEmbeddedWorkspaceExecutionSurface
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.AndroidEmbeddedWorkspaceSessionFactory
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspacePreflight
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunner
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.PROOF_EMBEDDED_WORKSPACE_TIMEOUTS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbeddedWorkspaceRunnerScreen(
    activity: Activity,
    onBack: () -> Unit,
) {
    val plan = remember {
        EmbeddedWorkspacePlanner().plan(EmbeddedWorkspaceProofPlan.createRequest())
    }
    val runnerScope = remember(activity) {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
    val runner = remember(activity, plan, runnerScope) {
        EmbeddedWorkspaceRunner(
            preflight = EmbeddedWorkspacePreflight(EmbeddedWorkspaceProofPlan.geometryPolicy),
            sessionFactory = AndroidEmbeddedWorkspaceSessionFactory(activity.applicationContext),
            timeoutPolicy = PROOF_EMBEDDED_WORKSPACE_TIMEOUTS,
            scope = runnerScope,
            dispatcher = Dispatchers.Main.immediate,
        )
    }
    val controller = remember(plan, runner) {
        EmbeddedWorkspaceRunnerController(plan, runner)
    }
    val state by controller.state.collectAsState()

    DisposableEffect(controller, runnerScope) {
        onDispose {
            runnerScope.launch { controller.close() }
                .invokeOnCompletion { runnerScope.cancel() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Embedded Workspace Runner (Experimental)") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Runner phase: ${state.phase}")
            Text("Start enabled: ${state.canStart} | Stop available: ${state.canStop}")
            Text("Latest result: ${state.latestResult ?: "None"}")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { runnerScope.launch { controller.start() } },
                    enabled = state.canStart,
                ) {
                    Text("Start")
                }
                OutlinedButton(
                    onClick = { runnerScope.launch { controller.stop() } },
                    enabled = state.canStop,
                ) {
                    Text("Stop")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val wazeItem = state.items.first { it.sourceCellId == EmbeddedWorkspaceProofPlan.WAZE_SOURCE_ID }
                val calculatorItem = state.items.first {
                    it.sourceCellId == EmbeddedWorkspaceProofPlan.CALCULATOR_SOURCE_ID
                }

                EmbeddedWorkspaceProofPane(
                    title = "Waze",
                    sourceCellId = EmbeddedWorkspaceProofPlan.WAZE_SOURCE_ID,
                    item = wazeItem,
                    controller = controller,
                    runnerScope = runnerScope,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                EmbeddedWorkspaceProofPane(
                    title = "Samsung Calculator",
                    sourceCellId = EmbeddedWorkspaceProofPlan.CALCULATOR_SOURCE_ID,
                    item = calculatorItem,
                    controller = controller,
                    runnerScope = runnerScope,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun EmbeddedWorkspaceProofPane(
    title: String,
    sourceCellId: String,
    item: EmbeddedWorkspaceRunnerItemState,
    controller: EmbeddedWorkspaceRunnerController,
    runnerScope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title)
        Text("sourceCellId: $sourceCellId")
        Text("order=${item.order} phase=${item.phase ?: "NONE"} displayId=${item.displayId}")

        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { context ->
                SurfaceView(context).also { view ->
                    val geometry = EmbeddedWorkspaceProofPlan.proofGeometry
                    view.holder.setFixedSize(geometry.width, geometry.height)
                    view.holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            controller.updateHostSlot(
                                sourceCellId,
                                AndroidEmbeddedWorkspaceExecutionSurface(holder.surface),
                            )
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int,
                        ) {
                            controller.updateHostSlot(
                                sourceCellId,
                                AndroidEmbeddedWorkspaceExecutionSurface(holder.surface),
                            )
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            runnerScope.launch { controller.surfaceLost(sourceCellId) }
                        }
                    })

                    view.setOnTouchListener { _, event ->
                        if (view.width <= 0 || view.height <= 0) return@setOnTouchListener false
                        val action = runCatching { virtualAction(event.actionMasked) }
                            .getOrElse { return@setOnTouchListener false }
                        val point = mapPoint(
                            x = event.x,
                            y = event.y,
                            viewWidth = view.width,
                            viewHeight = view.height,
                            geometry = geometry,
                        )
                        val touch = EmbeddedTouchEvent(
                            action = action,
                            x = point.x,
                            y = point.y,
                            pressure = pressureFor(event.actionMasked, event.pressure),
                            eventTimeNanos = event.eventTime * 1_000_000L,
                        )
                        runnerScope.launch { controller.sendTouch(sourceCellId, touch) }
                        true
                    }
                }
            },
        )
    }
}
