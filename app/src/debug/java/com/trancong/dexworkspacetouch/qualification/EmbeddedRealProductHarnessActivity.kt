package com.trancong.dexworkspacetouch.qualification

import android.os.*
import android.util.Log
import android.view.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.trancong.dexworkspacetouch.DexWorkspaceTouchApplication
import com.trancong.dexworkspacetouch.feature.embeddedcalculator.CALCULATOR_EMBEDDED_TARGET
import com.trancong.dexworkspacetouch.feature.embeddedwaze.WAZE_EMBEDDED_TARGET
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.product.*
import com.trancong.dexworkspacetouch.workspace.apppicker.infrastructure.AndroidInstalledAppDataSource
import com.trancong.dexworkspacetouch.workspace.apppicker.model.DefaultInstalledAppCatalog
import com.trancong.dexworkspacetouch.workspace.designer.model.*
import com.trancong.dexworkspacetouch.workspace.launcher.WorkspaceLaunchRequestFactory
import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import com.trancong.dexworkspacetouch.workspace.persistence.repository.WorkspaceRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicLong

/** Category C: unmodified product Composable/remote factory, isolated package. */
class EmbeddedRealProductHarnessActivity : ComponentActivity() {
    val hostId = identities.incrementAndGet()
    val pid = Process.myPid()
    val gate get() = (application as DexWorkspaceTouchApplication).embeddedProductRunGate
    var navigationCallbacks = 0
        private set
    var destroyed = false
        private set
    var shown by mutableStateOf(true)
    val events = mutableListOf<String>()
    private val observations = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    override fun onCreate(savedInstanceState: Bundle?) {
        require(packageName == VDM010_HARNESS_PACKAGE)
        super.onCreate(savedInstanceState)
        val independentRun = requireNotNull(intent.getStringExtra("independentRunId"))
        QualificationDiagnostics.attach(independentRun, System.identityHashCode(this), hostId, gate)
        record("NEW_INDEPENDENT_TESTBED run=$independentRun historical_release=NOT_VERIFIED")
        val dual = intent.getStringExtra("scenario") == "calculator-waze"
        val calc = CALCULATOR_EMBEDDED_TARGET
        val waze = WAZE_EMBEDDED_TARGET
        val cells = listOf(WorkspaceCell("calc", if (dual) NormalizedBounds(0f, 0f, .5f, 1f) else NormalizedBounds.FullCanvas,
            AssignedApp(calc.packageName, calc.componentName, "Calculator"))) + if (dual) listOf(
                WorkspaceCell("waze", NormalizedBounds(.5f, 0f, 1f, 1f), AssignedApp(waze.packageName, waze.componentName, "Waze"))) else emptyList()
        val workspace = Workspace("vdm010-real", "DWT 010 real qualification", WorkspaceCanvas(cells), 1L, 1, 0L, 0L)
        val repository = ReadOnlyQualificationRepository(workspace)
        val factory = WorkspaceLaunchRequestFactory(DefaultInstalledAppCatalog(AndroidInstalledAppDataSource.create(applicationContext)))
        record("host_create scenario=${if (dual) "calculator-waze" else "calculator"}")
        observations.launch { gate.status.collect { record("gate_status") } }
        setContent {
            MaterialTheme {
                if (shown) EmbeddedWorkspaceProductScreen(this, workspace.id,
                    application as DexWorkspaceTouchApplication, repository, factory,
                    onBack = { navigationCallbacks++; shown = false; record("product_back") },
                    onOpenClassic = { error("Classic forbidden in bounded real qualification") })
                else TextButton(onClick = { shown = true }) { Text("Open qualification workspace") }
            }
        }
    }
    fun surfaces(): List<SurfaceView> {
        fun collect(view: View): List<SurfaceView> = when (view) {
            is SurfaceView -> listOf(view)
            is ViewGroup -> (0 until view.childCount).flatMap { collect(view.getChildAt(it)) }
            else -> emptyList()
        }
        return collect(window.decorView)
    }
    fun observeSurfaces(): List<SurfaceView> = surfaces().onEach { view ->
        record("surface_observed view=${System.identityHashCode(view)} surface=${System.identityHashCode(view.holder.surface)} valid=${view.holder.surface.isValid}")
        view.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) { record("surface_created valid=${holder.surface.isValid}") }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { record("surface_changed width=$width height=$height") }
            override fun surfaceDestroyed(holder: SurfaceHolder) { record("surface_destroyed") }
        })
    }
    fun record(event: String) {
        val status = gate.status.value
        val line = "category=C time_nanos=${SystemClock.elapsedRealtimeNanos()} host=$hostId identity=${System.identityHashCode(this)} pid=$pid display=${windowManager.defaultDisplay.displayId} event=$event phase=${status.phase} token=${status.token?.let(System::identityHashCode)} generation=${status.generation} start=${status.startOperationId} cleanup=${status.cleanupOperationId} result=${status.resultKind} issue=${status.issue?.code} evidence=${status.cleanupEvidence} items=${status.items} outcomes=${status.cleanupOutcomes}"
        events += line
        Log.i("DWT-VDM-010", line)
    }
    override fun onDestroy() { destroyed = true; record("host_destroy"); observations.cancel(); super.onDestroy() }
    companion object { private val identities = AtomicLong() }
}
private class ReadOnlyQualificationRepository(private val workspace: Workspace) : WorkspaceRepository {
    override fun observeAll(): Flow<List<Workspace>> = flowOf(listOf(workspace))
    override suspend fun getById(id: String) = workspace.takeIf { it.id == id }
    override suspend fun exists(id: String) = id == workspace.id
    override suspend fun count() = 1
    override suspend fun insert(workspace: Workspace): Unit = error("Read-only fixture")
    override suspend fun update(workspace: Workspace): Unit = error("Read-only fixture")
    override suspend fun deleteById(id: String): Unit = error("Read-only fixture")
}
