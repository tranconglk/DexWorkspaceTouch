package com.trancong.dexworkspacetouch.feature.car.overlay

import com.trancong.dexworkspacetouch.platform.launch.shizuku.*
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Bounded repair policy after Classic success. All mutation remains delegated to SWC-001. */
class WorkspaceRepairSession(private val scope: CoroutineScope, private val controlBusy: StateFlow<Boolean>,
    private val assess: suspend (WorkspaceLaunchRequest) -> ExistingWorkspaceAssessmentReport,
    private val manualRepair: suspend (String) -> CarDockRepairState,
    private val onManualResult: (CarDockRepairState) -> Unit = {},
    private val stabilizationMs: Long = 1000, private val deadlineMs: Long = 5000,
    private val autoRepairEnabled: StateFlow<Boolean> = MutableStateFlow(false),
    private val capability: () -> ShizukuRuntimeState = { ShizukuRuntimeState.READY },
    private val automaticRepair: suspend (WorkspaceLaunchRequest, ExistingWorkspaceAssessmentReport) -> CarDockRepairState =
        { request, _ -> manualRepair(request.workspaceId) }) : ManualWorkspaceRepairController {
    private val mutableState = MutableStateFlow(CarDockRepairState())
    override val state: StateFlow<CarDockRepairState> = mutableState.asStateFlow()
    private var selectedId: String? = null
    private var generation = 0L
    private var suggestionJob: Job? = null
    private var repairJob: Job? = null
    @Volatile private var assessmentOwnsControl = false
    @Volatile private var repairOwnsControl = false
    private var automaticAttempt = false
    private var completedGeneration: Long? = null
    private var startedGeneration: Long? = null
    init { require(stabilizationMs >= 0 && deadlineMs > stabilizationMs) }
    private val preferenceJob = scope.launch(start=CoroutineStart.UNDISPATCHED) {
        var previous = autoRepairEnabled.value
        autoRepairEnabled.collect { current ->
            if (current != previous) {
                previous = current
                // Manual work is independent of the preference. A launch in progress retains its identity.
                if (repairJob?.isActive != true || automaticAttempt) {
                    val classicPending = startedGeneration == generation && completedGeneration != generation
                    invalidateSuggestion()
                    if (classicPending) startedGeneration = generation
                }
            }
        }
    }
    override fun selectWorkspace(workspaceId: String) {
        if (selectedId == workspaceId) return
        repairJob?.cancel()
        selectedId = workspaceId
        invalidateSuggestion()
    }
    override fun classicLaunchStarted(workspaceId: String) {
        repairJob?.cancel()
        selectWorkspace(workspaceId)
        invalidateSuggestion() // A second launch of the same workspace is a new identity.
        startedGeneration = generation
    }
    override fun classicLaunchCompleted(request: WorkspaceLaunchRequest) {
        if (selectedId != request.workspaceId || startedGeneration != generation || completedGeneration == generation) return
        val ticket = generation
        completedGeneration = ticket
        if (!autoRepairEnabled.value) return
        val ready = capability()
        if (ready != ShizukuRuntimeState.READY) {
            mutableState.value = CarDockRepairState("Repair unavailable", true, ready.userMessage(),
                workspaceId=selectedId, shizukuState=ready)
            return
        }
        mutableState.value = CarDockRepairState("Checking layout\u2026",true,workspaceId=selectedId)
        suggestionJob = scope.launch {
            val report = try {
                withTimeout(deadlineMs) {
                    controlBusy.first { !it }
                    delay(stabilizationMs)
                    if (!autoRepairEnabled.value || capability() != ShizukuRuntimeState.READY) {
                        return@withTimeout ExistingWorkspaceAssessmentReport(WorkspaceAssessmentStatus.UNAVAILABLE)
                    }
                    assess(request)
                }
            } catch (timeout: TimeoutCancellationException) {
                ExistingWorkspaceAssessmentReport(WorkspaceAssessmentStatus.UNAVAILABLE,detail="Layout assessment timed out")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                ExistingWorkspaceAssessmentReport(WorkspaceAssessmentStatus.UNAVAILABLE,detail=error.message ?: "Layout assessment unavailable")
            }
            if (ticket != generation || selectedId != request.workspaceId) return@launch
            mutableState.value = CarDockRepairState(when (report.status) {
                WorkspaceAssessmentStatus.REPAIR_AVAILABLE -> "Repair available"
                WorkspaceAssessmentStatus.UNAVAILABLE -> "Repair unavailable"
                else -> "Repair"
            },true,when (report.status) {
                WorkspaceAssessmentStatus.LAYOUT_CORRECT -> "Layout already correct"
                WorkspaceAssessmentStatus.PARTIAL_OR_UNRESOLVED -> "Layout partially observed or unresolved"
                else -> report.detail
            },assessment=report,workspaceId=selectedId)
            if (autoRepairEnabled.value && capability() == ShizukuRuntimeState.READY &&
                report.status == WorkspaceAssessmentStatus.REPAIR_AVAILABLE && !controlBusy.value) {
                startRepair(request.workspaceId, ticket, report, automatic=true) { automaticRepair(request, report) }
            }
        }
    }
    override fun controlActionStarted() {
        // The read-only engine reserves the same arbiter; do not invalidate its own reservation.
        if (!assessmentOwnsControl && !repairOwnsControl &&
            (automaticAttempt || repairJob?.isActive != true)) invalidateSuggestion()
    }
    internal fun assessmentReservationChanged(owned: Boolean) { assessmentOwnsControl = owned }
    internal fun repairReservationChanged(owned: Boolean) { repairOwnsControl = owned }
    override fun invalidateSuggestion() {
        generation++
        suggestionJob?.cancel()
        suggestionJob = null
        if (automaticAttempt) {
            repairJob?.cancel()
            repairJob = null
            automaticAttempt = false
        }
        if (repairJob?.isActive != true) mutableState.value = selectedId?.let {
            CarDockRepairState("Repair",true,workspaceId=it)
        } ?: CarDockRepairState()
    }
    override fun repair() {
        if (repairJob?.isActive == true) return
        val id = selectedId ?: return
        invalidateSuggestion()
        val ticket = generation
        val ready = capability()
        if (ready != ShizukuRuntimeState.READY) {
            mutableState.value = CarDockRepairState("Repair unavailable", true, ready.userMessage(),
                workspaceId=id, shizukuState=ready)
            onManualResult(mutableState.value)
            return
        }
        startRepair(id, ticket, automatic=false) { manualRepair(id) }
    }
    private fun startRepair(id: String, ticket: Long, assessment: ExistingWorkspaceAssessmentReport? = null,
        automatic: Boolean, operation: suspend () -> CarDockRepairState) {
        automaticAttempt = automatic
        mutableState.value = CarDockRepairState(if (automatic) "Auto repairing\u2026" else "Repairing\u2026",
            false,assessment=assessment,workspaceId=id)
        repairJob = scope.launch(start=CoroutineStart.LAZY) {
            val result = try { operation() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { CarDockRepairState("Repair unavailable",true,error.message ?: "Repair failed") }
            if (ticket == generation && selectedId == id) {
                mutableState.value = result.copy(workspaceId=id,assessment=assessment)
                if (!automatic) onManualResult(mutableState.value)
            }
        }
        repairJob?.start()
    }
    override fun dispose() { invalidateSuggestion(); repairJob?.cancel(); preferenceJob.cancel() }
}
