package com.trancong.dexworkspacetouch.platform.launch.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.view.Display
import android.widget.Toast
import com.trancong.dexworkspacetouch.feature.car.CarWorkflowExecutionArbiter
import com.trancong.dexworkspacetouch.feature.car.WorkspaceRepairMode
import com.trancong.dexworkspacetouch.feature.car.overlay.*
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.product.EmbeddedProductRunGate
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.product.ProductRunPhase
import com.trancong.dexworkspacetouch.platform.launch.android.DisplayTargetSingleAppLaunchPlatform
import com.trancong.dexworkspacetouch.platform.launch.bounds.DisplayWorkAreaSnapshot
import com.trancong.dexworkspacetouch.workspace.launcher.WorkspaceLaunchRequestFactory
import com.trancong.dexworkspacetouch.workspace.launcher.model.LaunchReadiness
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchRequest
import com.trancong.dexworkspacetouch.workspace.persistence.repository.WorkspaceRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import rikka.shizuku.Shizuku

/** Bounded product repair policy. Classic launch remains independent of Shizuku availability. */
class AndroidExistingWorkspaceRepair(
    private val context: Context,
    private val scope: CoroutineScope,
    private val repository: WorkspaceRepository,
    private val requestFactory: WorkspaceLaunchRequestFactory,
    private val gate: EmbeddedProductRunGate,
    private val arbiter: CarWorkflowExecutionArbiter,
    displayProvider: () -> Display?,
    private val shell: WorkspaceCommandShell = AndroidShizukuCommandTransport(context),
    private val repairMode: StateFlow<WorkspaceRepairMode>,
) : ManualWorkspaceRepairController {
    private val platform = DisplayTargetSingleAppLaunchPlatform(context, displayProvider)
    private val session = WorkspaceRepairSession(scope, arbiter.isRunning, ::assess, ::runManualRepair,
        { showResult(it.detail) }, mode = repairMode, automaticRepair = { request, assessment ->
            runRepair(request.workspaceId, request, assessment)
        })
    override val state: StateFlow<CarDockRepairState> = session.state
    @Volatile private var workspaceId: String? = null
    @Volatile private var launchedRequest: WorkspaceLaunchRequest? = null
    private var assessedSnapshot: DisplayWorkAreaSnapshot? = null
    private var resultToast: Toast? = null
    private val gateJob = scope.launch {
        gate.status.collect { if (it.phase != ProductRunPhase.IDLE) invalidateSuggestion() }
    }
    private val workspaceJob = scope.launch {
        repository.observeAll().collect { workspaces ->
            val expected = launchedRequest ?: return@collect
            val workspace = workspaces.singleOrNull { it.id == expected.workspaceId }
            val current = workspace?.let { withContext(Dispatchers.IO) {
                (requestFactory.create(it.id, it.name, it.canvas) as? LaunchReadiness.Ready)?.request
            } }
            if (launchedRequest == expected && current != expected) invalidateSuggestion()
        }
    }
    override fun selectWorkspace(workspaceId: String) {
        if (this.workspaceId == workspaceId) return
        this.workspaceId = workspaceId
        launchedRequest = null
        session.selectWorkspace(workspaceId)
    }
    override fun classicLaunchStarted(workspaceId: String) {
        this.workspaceId = workspaceId
        launchedRequest = null
        resultToast?.cancel()
        session.classicLaunchStarted(workspaceId)
    }
    override fun classicLaunchCompleted(request: WorkspaceLaunchRequest) {
        if (workspaceId != request.workspaceId) return
        launchedRequest = request
        session.classicLaunchCompleted(request)
    }
    override fun controlActionStarted() = session.controlActionStarted()
    override fun invalidateSuggestion() {
        launchedRequest = null
        assessedSnapshot = null
        session.invalidateSuggestion()
    }
    override fun repair() {
        resultToast?.cancel()
        launchedRequest = null
        session.repair()
    }
    private fun requireShizuku(requestPermission: Boolean = false) {
        check(Shizuku.pingBinder()) { "Repair không khả dụng: hãy khởi động Shizuku. Classic vẫn dùng bình thường." }
        if (requestPermission && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED &&
            !Shizuku.shouldShowRequestPermissionRationale()) {
            Shizuku.requestPermission(41009)
        }
        check(Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            "Repair không khả dụng: cấp quyền trong Shizuku rồi bấm Repair lại."
        }
        check(Shizuku.getUid() == 2000) { "Repair cần Shizuku chạy với quyền shell." }
    }
    private suspend fun assess(request: WorkspaceLaunchRequest): ExistingWorkspaceAssessmentReport {
        requireShizuku()
        val workspace = repository.getById(request.workspaceId) ?: error("Workspace unavailable")
        val current = withContext(Dispatchers.IO) {
            (requestFactory.create(workspace.id, workspace.name, workspace.canvas) as? LaunchReadiness.Ready)?.request
        }
        if (workspaceId != request.workspaceId || current != request) {
            invalidateSuggestion()
            return ExistingWorkspaceAssessmentReport(WorkspaceAssessmentStatus.UNAVAILABLE, detail = "Workspace context changed")
        }
        val snapshot = platform.currentSnapshot() ?: error("External display unavailable")
        val readOnly = WorkspaceCommandShell { command ->
            check(command == WorkspaceTaskCorrelation.DUMP_COMMAND) { "Assessment must be read-only" }
            shell.execute(command)
        }
        val report = runInterruptible(Dispatchers.IO) {
            ExistingWorkspaceRepair(gate, arbiter, readOnly).assess(request, snapshot, session::assessmentReservationChanged)
        }
        assessedSnapshot = snapshot
        if (platform.currentSnapshot() != snapshot) invalidateSuggestion()
        return report
    }
    private suspend fun runManualRepair(selectedId: String): CarDockRepairState {
        requireShizuku(requestPermission = true)
        return runRepair(selectedId)
    }
    private suspend fun runRepair(selectedId: String, expectedRequest: WorkspaceLaunchRequest? = null,
        expectedAssessment: ExistingWorkspaceAssessmentReport? = null): CarDockRepairState {
        requireShizuku()
        val workspace = repository.getById(selectedId) ?: error("Workspace unavailable")
        val request = withContext(Dispatchers.IO) {
            (requestFactory.create(workspace.id, workspace.name, workspace.canvas) as? LaunchReadiness.Ready)?.request
                ?: error("Workspace components unavailable")
        }
        val snapshot = platform.currentSnapshot() ?: error("External display unavailable")
        check(workspaceId == selectedId) { "Workspace selection changed" }
        if (expectedRequest != null) {
            check(request == expectedRequest && launchedRequest == expectedRequest &&
                snapshot == assessedSnapshot && repairMode.value == WorkspaceRepairMode.AUTOMATIC) {
                "Automatic workspace/display/policy context changed"
            }
        }
        currentCoroutineContext().ensureActive()
        val guardedShell = WorkspaceCommandShell { command ->
            if (expectedRequest != null) {
                check(!Thread.currentThread().isInterrupted && workspaceId == selectedId &&
                    launchedRequest == expectedRequest && repairMode.value == WorkspaceRepairMode.AUTOMATIC &&
                    platform.currentSnapshot() == snapshot) { "Automatic context changed before command" }
            }
            shell.execute(command)
        }
        session.repairReservationChanged(true)
        val report = try {
            runInterruptible(Dispatchers.IO) { ExistingWorkspaceRepair(gate, arbiter, guardedShell)
                .run(request, snapshot, expectedAssessment) }
        } finally { session.repairReservationChanged(false) }
        return report.toDockState()
    }
    private fun showResult(detail: String) {
        resultToast?.cancel()
        resultToast = Toast.makeText(context, detail, Toast.LENGTH_LONG).also { it.show() }
    }
    override fun dispose() {
        session.dispose()
        gateJob.cancel()
        workspaceJob.cancel()
        resultToast?.cancel()
        (shell as? AutoCloseable)?.close()
    }
}
