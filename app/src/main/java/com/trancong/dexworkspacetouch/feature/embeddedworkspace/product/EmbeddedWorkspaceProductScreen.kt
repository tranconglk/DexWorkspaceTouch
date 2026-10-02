package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import java.time.Instant
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.trancong.dexworkspacetouch.DexWorkspaceTouchApplication
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.EmbeddedWorkspaceLayoutLoader
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.EmbeddedWorkspaceGeometrySnapshot
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.EmbeddedWorkspaceProofPlan
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.EmbeddedWorkspaceRenderer
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.EmbeddedWorkspaceRendererCoordinator
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.EmbeddedWorkspaceRunnerController
import com.trancong.dexworkspacetouch.workspace.execution.embedded.layout.EmbeddedWorkspaceLayoutMapper
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.AndroidEmbeddedWorkspaceSessionFactory
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspacePreflight
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunResult
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunner
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.PROOF_EMBEDDED_WORKSPACE_TIMEOUTS
import com.trancong.dexworkspacetouch.workspace.launcher.WorkspaceLaunchRequestFactory
import com.trancong.dexworkspacetouch.workspace.persistence.repository.WorkspaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbeddedWorkspaceProductScreen(
    activity: Activity,
    workspaceId: String,
    application: DexWorkspaceTouchApplication,
    repository: WorkspaceRepository,
    requestFactory: WorkspaceLaunchRequestFactory,
    onBack: () -> Unit,
    onOpenClassic: (String) -> Unit,
) {
    val gateStatus by application.embeddedProductRunGate.status.collectAsState()
    val gateState = gateStatus.phase
    val blockedUi = gateStatus.cleanupBlockedUi()
    if (blockedUi != null) {
        // Terminal local đã kết thúc: host mới chỉ đọc status, không tải/dựng lại execution.
        BackHandler(onBack = onBack)
        Scaffold(topBar = {
            TopAppBar(title = { Text("Embedded Workspace (Thử nghiệm)") })
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
                EmbeddedCleanupBlockedStatus(blockedUi, onBack = onBack)
                EmbeddedProductDiagnosticsCopyButton(gateStatus)
            }
        }
        return
    }
    var loaded by remember(workspaceId) { mutableStateOf<EmbeddedEligibilityResult?>(null) }
    val loader = remember(repository, requestFactory) {
        EmbeddedWorkspaceProductLoader(EmbeddedWorkspaceLayoutLoader(repository, requestFactory))
    }
    LaunchedEffect(workspaceId, loader) {
        loaded = loader.loadEligible(workspaceId, EmbeddedWorkspaceProofPlan.geometryPolicy)
    }
    BackHandler(enabled = loaded !is EmbeddedEligibilityResult.Ready) {
        if (application.embeddedProductRunGate.canEnterEmbedded()) onBack()
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Embedded Workspace (Thử nghiệm)") },
            navigationIcon = {
                if (loaded !is EmbeddedEligibilityResult.Ready) {
                    TextButton(onClick = onBack, enabled = gateState == ProductRunPhase.IDLE) { Text("Quay lại") }
                }
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (gateState != ProductRunPhase.IDLE && loaded !is EmbeddedEligibilityResult.Ready) {
                ProductPreviousRunStatus(gateStatus)
            }
            EmbeddedProductDiagnosticsCopyButton(gateStatus)
            when (val result = loaded) {
                null -> Text("Đang tải Workspace…")
                is EmbeddedEligibilityResult.Rejected -> {
                    val recovery = EmbeddedProductRecoveryMapper.eligibility(
                        result.reason, gateState, application.embeddedProductRunGate.canEnterEmbedded(),
                    )
                    Text(recovery.message())
                    if (EmbeddedRecoveryAction.OPEN_CLASSIC in recovery.permittedActions) {
                        OutlinedButton(onClick = {
                            val current = EmbeddedProductRecoveryMapper.eligibility(result.reason,
                                application.embeddedProductRunGate.state.value,
                                application.embeddedProductRunGate.canEnterEmbedded())
                            if (EmbeddedRecoveryAction.OPEN_CLASSIC in current.permittedActions) onOpenClassic(workspaceId)
                        }) { Text("Mở Classic") }
                    }
                }
                is EmbeddedEligibilityResult.Ready -> {
                    EmbeddedWorkspaceProductRunContent(activity, result.snapshot, application,
                        onBack, { onOpenClassic(workspaceId) })
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun EmbeddedProductDiagnosticsCopyButton(status: ProductRunStatus) {
    val clipboard = LocalClipboardManager.current
    OutlinedButton(onClick = {
        // Chỉ user tap materialize/copy giá trị; không cần execution/controller và không đổi gate.
        val snapshot = EmbeddedProductDiagnostics.from(status, Instant.now())
        val text = EmbeddedProductDiagnosticFormatter.format(snapshot)
        EmbeddedProductDiagnosticCopyAction(text) { clipboard.setText(AnnotatedString(it)) }.onUserTap()
    }) { Text("Sao chép chẩn đoán") }
}

@Composable
private fun EmbeddedWorkspaceProductRunContent(
    activity: Activity,
    snapshot: EmbeddedWorkspaceGeometrySnapshot,
    application: DexWorkspaceTouchApplication,
    onBack: () -> Unit,
    onOpenClassic: () -> Unit,
) {
    val gate = application.embeddedProductRunGate
    val status by gate.status.collectAsState()
    val probe = remember(activity.applicationContext) { AndroidEmbeddedCapabilityProbe(activity.applicationContext) }
    var execution by remember(activity, snapshot) { mutableStateOf<ProductRouteExecution?>(null) }
    val current = execution
    val operation = current?.product?.startOperation
    val ownsRun = operation != null && status.token === operation.token
    val terminal = current != null && ((operation != null && !ownsRun) ||
        (status.phase != ProductRunPhase.IDLE && !ownsRun) || status.phase == ProductRunPhase.CLEANUP_BLOCKED)
    DisposableEffect(current) { onDispose { current?.product?.onHostDisposed() } }
    LaunchedEffect(current, status) {
        if (terminal) execution = null
    }
    if (current != null && !terminal && (status.phase == ProductRunPhase.IDLE || ownsRun)) {
        EmbeddedWorkspaceProductExecutionContent(snapshot, application, current, onBack, onOpenClassic)
    } else {
        EmbeddedWorkspaceProductObservationContent(snapshot, gate, status, probe, onBack, onOpenClassic) {
            // Chỉ thao tác Start của người dùng đi vào factory. IDLE transition không tự khởi tạo.
            if (execution == null) {
                execution = gate.createExecutionIfIdle {
                    createProductRouteExecution(activity, snapshot, application, probe)
                }
            }
        }
    }
}

private class ProductRouteExecution(
    val scope: CoroutineScope,
    val renderer: EmbeddedWorkspaceRendererCoordinator,
    val product: EmbeddedWorkspaceProductController,
    val probe: AndroidEmbeddedCapabilityProbe,
)

private fun createProductRouteExecution(
    activity: Activity,
    snapshot: EmbeddedWorkspaceGeometrySnapshot,
    application: DexWorkspaceTouchApplication,
    probe: AndroidEmbeddedCapabilityProbe,
): ProductRouteExecution {
    val scope = application.createEmbeddedProductRunScope()
    val runner = EmbeddedWorkspaceRunner(
        preflight = EmbeddedWorkspacePreflight(snapshot.asPolicy()),
        sessionFactory = AndroidEmbeddedWorkspaceSessionFactory(activity.applicationContext),
        timeoutPolicy = PROOF_EMBEDDED_WORKSPACE_TIMEOUTS,
        scope = scope,
        dispatcher = Dispatchers.Main.immediate,
    )
    val renderer = EmbeddedWorkspaceRendererCoordinator(snapshot, EmbeddedWorkspaceLayoutMapper(),
        EmbeddedWorkspaceRunnerController(snapshot.planSnapshot, runner))
    val product = EmbeddedWorkspaceProductController(snapshot.planSnapshot.workspaceId,
        application.embeddedProductRunGate, probe,
        { ProductHostReadiness(renderer.state.value.canStart, renderer.controller.state.value.canStart) },
        object : EmbeddedProductExecution {
            override suspend fun start(): EmbeddedWorkspaceRunResult? = renderer.start()
            override suspend fun close(): EmbeddedWorkspaceRunResult? = renderer.close()
        }, scope)
    return ProductRouteExecution(scope, renderer, product, probe)
}

@Composable
private fun EmbeddedWorkspaceProductExecutionContent(
    snapshot: EmbeddedWorkspaceGeometrySnapshot,
    application: DexWorkspaceTouchApplication,
    execution: ProductRouteExecution,
    onBack: () -> Unit,
    onOpenClassic: () -> Unit,
) {
    val scope = execution.scope
    val renderer = execution.renderer
    val product = execution.product
    val probe = execution.probe
    val rendererState by renderer.state.collectAsState()
    val runnerState by renderer.controller.state.collectAsState()
    val gateState by application.embeddedProductRunGate.state.collectAsState()
    val productRecovery by product.recovery.collectAsState()
    val currentRecovery = EmbeddedProductRecoveryMapper.snapshot(gateState,
        application.embeddedProductRunGate.canEnterEmbedded(), productRecovery.issue,
        productRecovery.allocationEvidence, productRecovery.cleanupEvidence)
    val refresh = remember(renderer, probe, product) {
        EmbeddedShizukuRefresh(probe, probe,
            { ProductHostReadiness(renderer.state.value.canStart, renderer.controller.state.value.canStart) },
            { product.currentRecovery() })
    }
    val readinessSnapshot by refresh.state.collectAsState()
    val readiness = readinessSnapshot?.readiness
    val recovery = readiness?.let { EmbeddedWorkspaceReadiness.withRecovery(it, currentRecovery) } ?: currentRecovery
    val readinessRecovery = readiness?.let {
        EmbeddedProductRecoveryMapper.readiness(it, gateState, application.embeddedProductRunGate.canEnterEmbedded())
    }
    ObserveProductReadiness(refresh)
    LaunchedEffect(snapshot, rendererState.canStart, runnerState.canStart, gateState, refresh) {
        refresh.refresh()
    }
    LaunchedEffect(runnerState.latestResult) {
        val operation = product.startOperation
        if (operation != null) runnerState.latestResult?.let { product.observeResult(operation, it) }
    }
    var startSubmitted by remember(product) { mutableStateOf(false) }
    LaunchedEffect(product, rendererState.canStart, runnerState.canStart, readiness) {
        if (!startSubmitted && rendererState.canStart && runnerState.canStart && readiness == EmbeddedReadinessResult.Ready) {
            startSubmitted = true
            scope.launch { product.start(); refresh.refresh() }
        }
    }
    fun exit() { product.requestBack(onBack) }
    BackHandler { exit() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${snapshot.planSnapshot.workspaceName} — ${runnerState.phase}")
        if (recovery.issue != null || gateState != ProductRunPhase.IDLE || recovery.cleanupEvidence != CleanupEvidence.NOT_NEEDED) {
            Text(recovery.message())
        }
        if (gateState == ProductRunPhase.IDLE && readinessRecovery != null) {
            if (readinessSnapshot?.permissionDenied == true) {
                Text("Quyền Shizuku đã bị từ chối; kiểm tra quyền trong Shizuku rồi thử lại.")
            } else Text(readinessRecovery.message())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch {
                    product.start()
                    refresh.refresh()
                }
            }, enabled = EmbeddedRecoveryAction.START_EMBEDDED in recovery.permittedActions &&
                readinessRecovery?.permittedActions?.contains(EmbeddedRecoveryAction.START_EMBEDDED) == true && rendererState.canStart) {
                Text("Bắt đầu Embedded")
            }
            OutlinedButton(onClick = { refresh.refresh() },
                enabled = EmbeddedRecoveryAction.REFRESH_READINESS in recovery.permittedActions) { Text("Kiểm tra lại") }
            if (readinessSnapshot?.capability?.shizukuLaunchAvailable == true &&
                EmbeddedRecoveryAction.REFRESH_READINESS in recovery.permittedActions) {
                OutlinedButton(onClick = { probe.openShizuku(); refresh.refresh() }) { Text("Mở Shizuku") }
            }
            if (readinessRecovery?.permittedActions?.contains(EmbeddedRecoveryAction.REQUEST_SHIZUKU_PERMISSION) == true &&
                EmbeddedRecoveryAction.REFRESH_READINESS in recovery.permittedActions) {
                OutlinedButton(onClick = { refresh.requestPermission() }) { Text("Cấp quyền Shizuku") }
            }
            if (EmbeddedRecoveryAction.OPEN_CLASSIC in recovery.permittedActions) {
                OutlinedButton(onClick = { if (product.canOpenClassic()) onOpenClassic() }) { Text("Mở Classic") }
            }
            if (EmbeddedRecoveryAction.BACK in recovery.permittedActions || EmbeddedRecoveryAction.REQUEST_EXIT in recovery.permittedActions) {
                OutlinedButton(onClick = ::exit) { Text("Quay lại") }
            }
        }
        EmbeddedWorkspaceRenderer(renderer, scope, Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
internal fun EmbeddedCleanupBlockedStatus(
    ui: EmbeddedCleanupBlockedUi,
    onBack: (() -> Unit)? = null,
    onViewStatus: ((String) -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(ui.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        Text(ui.message)
        Text(ui.reason)
        Text(ui.scopeMessage)
        Text(ui.supportGuidance)
        if (EmbeddedCleanupBlockedAction.BACK in ui.permittedActions && onBack != null) {
            OutlinedButton(onClick = onBack) { Text("Quay lại") }
        }
        val workspaceId = ui.evidence.token?.workspaceId
        if (EmbeddedCleanupBlockedAction.VIEW_STATUS in ui.permittedActions &&
            !workspaceId.isNullOrBlank() && onViewStatus != null) {
            OutlinedButton(onClick = { onViewStatus(workspaceId) }) { Text("Xem trạng thái Embedded") }
        }
    }
}

@Composable
private fun ProductPreviousRunStatus(status: ProductRunStatus) {
    status.cleanupBlockedUi()?.let {
        EmbeddedCleanupBlockedStatus(it)
        return
    }
    val recovery = EmbeddedProductRecoveryMapper.snapshot(status.phase, false,
        status.issue, status.allocationEvidence, status.cleanupEvidence)
    Text(when (status.phase) {
        ProductRunPhase.STARTING -> "Lượt Embedded trước đang khởi động. Đang chờ cleanup của màn hình trước."
        ProductRunPhase.ACTIVE -> "Lượt Embedded trước còn hoạt động. Đang chờ cleanup của màn hình trước."
        ProductRunPhase.STOPPING -> "Đang dọn lượt Embedded trước…"
        ProductRunPhase.CLEANUP_BLOCKED -> recovery.message()
        ProductRunPhase.IDLE -> recovery.message()
    })
}

@Composable
private fun EmbeddedWorkspaceProductObservationContent(
    snapshot: EmbeddedWorkspaceGeometrySnapshot,
    gate: EmbeddedProductRunGate,
    status: ProductRunStatus,
    probe: AndroidEmbeddedCapabilityProbe,
    onBack: () -> Unit,
    onOpenClassic: () -> Unit,
    onStart: () -> Unit,
) {
    // Readiness môi trường chỉ đọc capability; không truy cập runtime/controller cũ.
    val refresh = remember(gate, probe) {
        EmbeddedShizukuRefresh(probe, probe,
            { ProductHostReadiness(true, true) },
            {
                val current = gate.status.value
                EmbeddedProductRecoveryMapper.snapshot(current.phase, gate.canEnterEmbedded(),
                    current.issue, current.allocationEvidence, current.cleanupEvidence)
            })
    }
    ObserveProductReadiness(refresh)
    val readiness by refresh.state.collectAsState()
    val recovery = EmbeddedProductRecoveryMapper.snapshot(status.phase, gate.canEnterEmbedded(),
        status.issue, status.allocationEvidence, status.cleanupEvidence)
    val environment = readiness?.readiness?.let {
        EmbeddedProductRecoveryMapper.readiness(it, status.phase, gate.canEnterEmbedded())
    }
    BackHandler { if (gate.canEnterEmbedded()) onBack() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(snapshot.planSnapshot.workspaceName)
        if (status.phase != ProductRunPhase.IDLE) {
            ProductPreviousRunStatus(status)
        } else {
            Text(environment?.message() ?: "Đang kiểm tra môi trường Embedded…")
            Button(onClick = {
                refresh.refresh()
                if (refresh.state.value?.readiness == EmbeddedReadinessResult.Ready && gate.canEnterEmbedded()) onStart()
            }, enabled = EmbeddedRecoveryAction.START_EMBEDDED in recovery.permittedActions &&
                environment?.permittedActions?.contains(EmbeddedRecoveryAction.START_EMBEDDED) == true) {
                Text("Bắt đầu Embedded")
            }
            OutlinedButton(onClick = { refresh.refresh() },
                enabled = EmbeddedRecoveryAction.REFRESH_READINESS in recovery.permittedActions) { Text("Kiểm tra lại") }
            if (readiness?.capability?.shizukuLaunchAvailable == true && EmbeddedRecoveryAction.REFRESH_READINESS in recovery.permittedActions) {
                OutlinedButton(onClick = { probe.openShizuku(); refresh.refresh() }) { Text("Mở Shizuku") }
            }
            if (environment?.permittedActions?.contains(EmbeddedRecoveryAction.REQUEST_SHIZUKU_PERMISSION) == true) {
                OutlinedButton(onClick = { refresh.requestPermission() }) { Text("Cấp quyền Shizuku") }
            }
            if (EmbeddedRecoveryAction.OPEN_CLASSIC in recovery.permittedActions) {
                OutlinedButton(onClick = { if (gate.canEnterEmbedded()) onOpenClassic() }) { Text("Mở Classic") }
            }
            OutlinedButton(onClick = { if (gate.canEnterEmbedded()) onBack() }) { Text("Quay lại") }
        }
    }
}

@Composable
private fun ObserveProductReadiness(refresh: EmbeddedShizukuRefresh) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(refresh, lifecycleOwner) {
        refresh.attach()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            refresh.dispose()
        }
    }
}
