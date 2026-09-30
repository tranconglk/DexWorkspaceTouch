package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

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
    val gateState by application.embeddedProductRunGate.state.collectAsState()
    var loaded by remember(workspaceId) { mutableStateOf<EmbeddedEligibilityResult?>(null) }
    val loader = remember(repository, requestFactory) {
        EmbeddedWorkspaceProductLoader(EmbeddedWorkspaceLayoutLoader(repository, requestFactory))
    }
    LaunchedEffect(workspaceId, loader) {
        loaded = loader.loadEligible(workspaceId, EmbeddedWorkspaceProofPlan.geometryPolicy)
    }
    var status by remember(workspaceId) { mutableStateOf("") }
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
            if (gateState == ProductRunPhase.CLEANUP_BLOCKED) {
                Text("Cleanup Embedded chưa được xác nhận. Tạm khóa mở Workspace khác.")
            }
            when (val result = loaded) {
                null -> Text("Đang tải Workspace…")
                is EmbeddedEligibilityResult.Rejected -> {
                    Text(eligibilityMessage(result.reason))
                    OutlinedButton(onClick = { onOpenClassic(workspaceId) },
                        enabled = gateState == ProductRunPhase.IDLE) { Text("Mở Classic") }
                }
                is EmbeddedEligibilityResult.Ready -> {
                    EmbeddedWorkspaceProductRunContent(activity, result.snapshot, application,
                        onBack, { onOpenClassic(workspaceId) }, { status = it })
                }
            }
            if (status.isNotBlank()) Text(status)
        }
    }
}

@Composable
private fun EmbeddedWorkspaceProductRunContent(
    activity: Activity,
    snapshot: EmbeddedWorkspaceGeometrySnapshot,
    application: DexWorkspaceTouchApplication,
    onBack: () -> Unit,
    onOpenClassic: () -> Unit,
    onStatus: (String) -> Unit,
) {
    val scope = remember(snapshot) { application.createEmbeddedProductRunScope() }
    val renderer = remember(activity, snapshot, scope) {
        val runner = EmbeddedWorkspaceRunner(
            preflight = EmbeddedWorkspacePreflight(snapshot.asPolicy()),
            sessionFactory = AndroidEmbeddedWorkspaceSessionFactory(activity.applicationContext),
            timeoutPolicy = PROOF_EMBEDDED_WORKSPACE_TIMEOUTS,
            scope = scope,
            dispatcher = Dispatchers.Main.immediate,
        )
        EmbeddedWorkspaceRendererCoordinator(snapshot, EmbeddedWorkspaceLayoutMapper(),
            EmbeddedWorkspaceRunnerController(snapshot.planSnapshot, runner))
    }
    val rendererState by renderer.state.collectAsState()
    val runnerState by renderer.controller.state.collectAsState()
    val gateState by application.embeddedProductRunGate.state.collectAsState()
    val probe = remember { AndroidEmbeddedCapabilityProbe() }
    val product = remember(renderer, scope) {
        EmbeddedWorkspaceProductController(snapshot.planSnapshot.workspaceId,
            application.embeddedProductRunGate, probe,
            { ProductHostReadiness(renderer.state.value.canStart, renderer.controller.state.value.canStart) },
            object : EmbeddedProductExecution {
                override suspend fun start(): EmbeddedWorkspaceRunResult? = renderer.start()
                override suspend fun close(): EmbeddedWorkspaceRunResult? = renderer.close()
            }, scope)
    }
    var readiness by remember(snapshot) { mutableStateOf<EmbeddedReadinessResult?>(null) }
    LaunchedEffect(snapshot, rendererState.canStart, runnerState.canStart, gateState) {
        readiness = if (gateState == ProductRunPhase.IDLE) {
            EmbeddedWorkspaceReadiness.evaluate(probe.snapshot(), true,
                rendererState.canStart, runnerState.canStart)
        } else null
    }
    LaunchedEffect(runnerState.latestResult) {
        runnerState.latestResult?.let { product.observeResult(it) }
    }
    DisposableEffect(product) { onDispose { product.onHostDisposed() } }

    fun exit() {
        scope.launch {
            if (product.requestExit()) onBack()
            else onStatus("Dọn Embedded chưa hoàn tất; đang chặn lần mở tiếp theo.")
        }
    }
    BackHandler { exit() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${snapshot.planSnapshot.workspaceName} — ${runnerState.phase}")
        if (readiness != null && readiness != EmbeddedReadinessResult.Ready) {
            Text(readinessMessage(readiness!!))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch {
                    when (val outcome = product.start()) {
                        is ProductStartOutcome.NotReady -> { readiness = outcome.reason; onStatus(readinessMessage(outcome.reason)) }
                        is ProductStartOutcome.RunResult -> onStatus(runtimeMessage(outcome.result))
                        ProductStartOutcome.Busy -> onStatus("Một Workspace Embedded khác đang chạy.")
                        ProductStartOutcome.RendererChanged -> onStatus("Màn hình chưa sẵn sàng; kiểm tra lại.")
                        ProductStartOutcome.Uncertain -> onStatus("Trạng thái cleanup chưa xác nhận.")
                    }
                }
            }, enabled = gateState == ProductRunPhase.IDLE && rendererState.canStart) {
                Text("Bắt đầu Embedded")
            }
            OutlinedButton(onClick = {
                readiness = EmbeddedWorkspaceReadiness.evaluate(probe.snapshot(), true,
                    renderer.state.value.canStart, renderer.controller.state.value.canStart)
            }) { Text("Kiểm tra lại") }
            if (readiness == EmbeddedReadinessResult.ShizukuPermissionMissing) {
                OutlinedButton(onClick = { Shizuku.requestPermission(41008) }) { Text("Cấp quyền Shizuku") }
            }
            OutlinedButton(onClick = { scope.launch { if (product.requestExit()) onOpenClassic() } },
                enabled = gateState == ProductRunPhase.IDLE || gateState == ProductRunPhase.ACTIVE) {
                Text("Mở Classic")
            }
            OutlinedButton(onClick = ::exit) { Text("Quay lại") }
        }
        EmbeddedWorkspaceRenderer(renderer, scope, Modifier.fillMaxWidth().weight(1f))
    }
}

private fun eligibilityMessage(reason: EmbeddedEligibilityFailure): String = when (reason) {
    is EmbeddedEligibilityFailure.MissingWorkspace -> "Không tìm thấy Workspace đã chọn."
    is EmbeddedEligibilityFailure.LaunchNotReady -> "Workspace chưa sẵn sàng; hãy kiểm tra bố cục và ứng dụng."
    is EmbeddedEligibilityFailure.UnsupportedEmbeddedItemCount -> "Embedded thử nghiệm hỗ trợ 1–2 ứng dụng; hiện có ${reason.count}."
    is EmbeddedEligibilityFailure.DuplicateTarget -> "Embedded chưa hỗ trợ mở trùng ứng dụng/hoạt động."
    EmbeddedEligibilityFailure.UnsupportedLayout -> "Bố cục Workspace chưa được Embedded hỗ trợ."
    is EmbeddedEligibilityFailure.GeometryUnavailable -> "Không xác định được kích thước cho ô ${reason.sourceCellId}."
}

private fun readinessMessage(reason: EmbeddedReadinessResult): String = when (reason) {
    EmbeddedReadinessResult.Ready -> "Embedded đã sẵn sàng."
    EmbeddedReadinessResult.UnsupportedPlatform -> "Thiết bị chưa có khả năng chạy Embedded đã kiểm chứng."
    EmbeddedReadinessResult.ShizukuUnavailable -> "Shizuku chưa chạy hoặc chưa kết nối; Classic vẫn dùng được."
    EmbeddedReadinessResult.ShizukuPermissionMissing -> "Chưa cấp quyền Shizuku cho Embedded."
    EmbeddedReadinessResult.GeometryUnavailable -> "Không xác định được kích thước màn hình khách."
    EmbeddedReadinessResult.RendererNotReady -> "Vùng hiển thị Embedded chưa sẵn sàng."
}

private fun runtimeMessage(result: EmbeddedWorkspaceRunResult): String = when (result) {
    is EmbeddedWorkspaceRunResult.Started -> "Embedded đang chạy."
    is EmbeddedWorkspaceRunResult.PreflightRejected -> "Embedded chưa sẵn sàng để bắt đầu."
    is EmbeddedWorkspaceRunResult.StartFailed -> "Không thể khởi động Embedded; hãy kiểm tra Shizuku và thử lại sau khi cleanup hoàn tất."
    is EmbeddedWorkspaceRunResult.CleanupIncomplete, is EmbeddedWorkspaceRunResult.RecoveryRequired ->
        "Cleanup Embedded chưa xác nhận; đang chặn lần mở tiếp theo."
    is EmbeddedWorkspaceRunResult.Stopped -> "Đã dừng Embedded."
    EmbeddedWorkspaceRunResult.DuplicateCall -> "Yêu cầu Embedded đang được xử lý."
}
