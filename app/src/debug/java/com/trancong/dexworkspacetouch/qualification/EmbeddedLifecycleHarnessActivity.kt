package com.trancong.dexworkspacetouch.qualification

import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trancong.dexworkspacetouch.DexWorkspaceTouchApplication
import com.trancong.dexworkspacetouch.feature.embeddedapp.*
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.*
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.product.*
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.execution.embedded.*
import com.trancong.dexworkspacetouch.workspace.execution.embedded.layout.*
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicLong

const val VDM010_HARNESS_PACKAGE = "com.trancong.dexworkspacetouch.vdm010harness"

/** Debug-only, category B. No real remote failure/ownership claim. */
class EmbeddedLifecycleHarnessActivity : ComponentActivity() {
    val hostId = identities.incrementAndGet()
    val pid: Int = Process.myPid()
    val gate get() = (application as DexWorkspaceTouchApplication).embeddedProductRunGate
    var route by mutableStateOf<HarnessRoute?>(null)
        private set
    var allocations = 0
        private set
    var navigationCallbacks = 0
        private set
    var destroyed = false
        private set
    val events = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        require(packageName == VDM010_HARNESS_PACKAGE) { "Use isolated-app-id.init.gradle; production package forbidden" }
        super.onCreate(savedInstanceState)
        record("host_create")
        setContent {
            val status by gate.status.collectAsState()
            MaterialTheme {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text("DWT-VDM-010 controlled harness (Experimental)")
                    Text("host=$hostId pid=$pid phase=${status.phase}")
                    Text("Synthetic sessions; no remote cleanup proof")
                    val owned = route
                    DisposableEffect(owned) { onDispose { owned?.product?.onHostDisposed() } }
                    LaunchedEffect(status, owned) {
                        val operation = owned?.product?.startOperation
                        if (owned != null && operation != null &&
                            (status.token !== operation.token || status.phase == ProductRunPhase.CLEANUP_BLOCKED)) {
                            route = null
                        }
                    }
                    val blocked = status.cleanupBlockedUi()
                    if (blocked != null) {
                        Text(blocked.title)
                        Text(blocked.reason)
                    }
                    Button(onClick = { explicitStart(HarnessFixture()) },
                        enabled = owned == null && gate.canEnterEmbedded()) { Text("Start controlled run") }
                    Button(onClick = { requestBackFromUserAction() },
                        enabled = owned != null && status.phase != ProductRunPhase.CLEANUP_BLOCKED) { Text("Back") }
                    if (owned != null) EmbeddedWorkspaceRenderer(owned.renderer, owned.scope,
                        Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }

    /** Called only by an explicit harness UI action or instrumentation action. */
    fun explicitStart(fixture: HarnessFixture): HarnessRoute? {
        if (destroyed || route != null) return null
        val created = gate.createExecutionIfIdle {
            allocations++
            val runScope = (application as DexWorkspaceTouchApplication).createEmbeddedProductRunScope()
            val plan = EmbeddedWorkspacePlan("vdm010", "Controlled 010", listOf(
                EmbeddedWorkspacePlanItem("calc", "controlled.calc", "controlled.calc.Main", NormalizedBounds.FullCanvas, 0)))
            val geometry = (EmbeddedWorkspaceGeometrySnapshot.resolve(plan,
                EmbeddedGeometryPolicy { EmbeddedAppGeometry(900, 675, 320) }) as GeometrySnapshotResult.Ready).snapshot
            val factory = ControlledSessions(fixture)
            val runner = EmbeddedWorkspaceRunner(EmbeddedWorkspacePreflight(geometry.asPolicy()), factory,
                PROOF_EMBEDDED_WORKSPACE_TIMEOUTS, runScope, Dispatchers.Main.immediate)
            val renderer = EmbeddedWorkspaceRendererCoordinator(geometry, EmbeddedWorkspaceLayoutMapper(),
                EmbeddedWorkspaceRunnerController(plan, runner))
            val product = EmbeddedWorkspaceProductController(plan.workspaceId, gate,
                EmbeddedCapabilityProbe { EmbeddedCapabilitySnapshot(true, true, true) },
                { ProductHostReadiness(renderer.state.value.canStart, renderer.controller.state.value.canStart) },
                object : EmbeddedProductExecution {
                    override suspend fun start(): EmbeddedWorkspaceRunResult? {
                        fixture.startCalls++
                        return renderer.start()
                    }
                    override suspend fun close(): EmbeddedWorkspaceRunResult? {
                        fixture.closeCalls++
                        val result = renderer.close()
                        fixture.cleanupWaiting.complete(Unit)
                        fixture.cleanupPermit.await()
                        return when (fixture.mode) {
                            HarnessCleanupMode.CLEAN -> result
                            HarnessCleanupMode.UNCERTAIN -> null
                            HarnessCleanupMode.INCOMPLETE -> {
                                val receipts = when (result) {
                                    is EmbeddedWorkspaceRunResult.Stopped -> result.receipts
                                    is EmbeddedWorkspaceRunResult.StartFailed -> result.receipts + listOfNotNull(result.partialReceipt)
                                    else -> error("Controlled runner must provide owned terminal receipts")
                                }
                                EmbeddedWorkspaceRunResult.CleanupIncomplete(receipts, receipts.map {
                                    EmbeddedWorkspaceCleanupOutcome.Incomplete(it.sourceCellId,
                                        EmbeddedSessionFailure("CONTROLLED_010", "Synthetic test result"))
                                })
                            }
                        }
                    }
                }, runScope)
            HarnessRoute(runScope, renderer, product, factory, fixture)
        } ?: return null
        route = created
        record("explicit_start")
        created.scope.launch {
            withTimeout(15_000) { created.renderer.state.first { it.canStart } }
            created.product.start()
            record("start_result")
        }
        return created
    }

    fun requestBackFromUserAction() {
        route?.product?.requestBack { navigationCallbacks++ }
    }

    fun hasSurface(): Boolean = findSurface(window.decorView) != null

    fun observeSurface(): SurfaceView {
        val view = findSurface(window.decorView) ?: error("Actual renderer SurfaceView not found")
        record("surface_observed view=${System.identityHashCode(view)} surface=${System.identityHashCode(view.holder.surface)} valid=${view.holder.surface.isValid}")
        view.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) { record("surface_created valid=${holder.surface.isValid}") }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { record("surface_changed width=$width height=$height") }
            override fun surfaceDestroyed(holder: SurfaceHolder) { record("surface_destroyed") }
        })
        return view
    }

    override fun onDestroy() {
        destroyed = true
        record("host_destroy")
        route?.product?.onHostDisposed()
        route = null
        super.onDestroy()
    }

    fun record(event: String) {
        val status = gate.status.value
        val line = "category=B time_nanos=${SystemClock.elapsedRealtimeNanos()} host=$hostId identity=${System.identityHashCode(this)} pid=$pid display=${windowManager.defaultDisplay.displayId} event=$event phase=${status.phase} token=${status.token?.let(System::identityHashCode)} generation=${status.generation} start=${status.startOperationId} cleanup=${status.cleanupOperationId}"
        events += line
        Log.i("DWT-VDM-010", line)
    }

    private fun findSurface(view: View): SurfaceView? {
        if (view is SurfaceView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findSurface(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    companion object { private val identities = AtomicLong() }
}

enum class HarnessCleanupMode { CLEAN, INCOMPLETE, UNCERTAIN }
class HarnessFixture(val mode: HarnessCleanupMode = HarnessCleanupMode.CLEAN) {
    val cleanupWaiting = CompletableDeferred<Unit>()
    val cleanupPermit = CompletableDeferred<Unit>()
    var startCalls = 0
    var closeCalls = 0
    var sessionStops = 0
    var sessionCloses = 0
}
class HarnessRoute(val scope: CoroutineScope, val renderer: EmbeddedWorkspaceRendererCoordinator,
    val product: EmbeddedWorkspaceProductController, val sessions: ControlledSessions, val fixture: HarnessFixture)

class ControlledSessions(private val fixture: HarnessFixture) : EmbeddedWorkspaceSessionFactory {
    private var observer: ((EmbeddedSessionSnapshot) -> Unit)? = null
    // The test retains a late producer explicitly; Application never receives it.
    var lateCallback: ((EmbeddedSessionSnapshot) -> Unit)? = null
        private set
    override fun create(target: EmbeddedAppTarget, observer: (EmbeddedSessionSnapshot) -> Unit): EmbeddedWorkspaceSessionHandle {
        this.observer = observer
        lateCallback = observer
        return object : EmbeddedWorkspaceSessionHandle {
            override val sessionId = EmbeddedAppSessionId("controlled-010-${identities.incrementAndGet()}")
            override fun connect() { emit(EmbeddedSessionSnapshot(EmbeddedSessionPhase.READY)) }
            override fun start(surface: EmbeddedWorkspaceExecutionSurface) {
                check(surface.isValid) { "Android Surface must be valid at controlled Start" }
                emit(EmbeddedSessionSnapshot(EmbeddedSessionPhase.ACTIVE, 1010))
            }
            override fun sendTouch(event: EmbeddedTouchEvent) = true
            override fun stop() { fixture.sessionStops++ }
            override fun close() { fixture.sessionCloses++; emit(EmbeddedSessionSnapshot(EmbeddedSessionPhase.STOPPED)) }
            override fun detachNotifications() { this@ControlledSessions.observer = null }
        }
    }
    private fun emit(snapshot: EmbeddedSessionSnapshot) { observer?.invoke(snapshot) }
    companion object { private val identities = AtomicLong() }
}
