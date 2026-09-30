package com.trancong.dexworkspacetouch.feature.embeddedworkspace

import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionPhase
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedTouchEvent
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlan
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceExecutionRequest
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceExecutionSurface
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceHostSlot
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceItemReceipt
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunResult
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunner
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunnerPhase
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceTouchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EmbeddedWorkspaceRunnerItemState(
    val sourceCellId: String,
    val order: Int,
    val phase: EmbeddedSessionPhase?,
    val displayId: Int,
)

data class EmbeddedWorkspaceRunnerControllerState(
    val phase: EmbeddedWorkspaceRunnerPhase,
    val canStart: Boolean,
    val canStop: Boolean,
    val slotValidity: Map<String, Boolean>,
    val items: List<EmbeddedWorkspaceRunnerItemState>,
    val latestResult: EmbeddedWorkspaceRunResult?,
)

class EmbeddedWorkspaceRunnerController(
    private val plan: EmbeddedWorkspacePlan,
    private val runner: EmbeddedWorkspaceRunner,
) {
    private val orderedPlanItems = plan.items.sortedBy { it.order }
    private val expectedSourceIds = orderedPlanItems.map { it.sourceCellId }
    private val expectedSourceIdSet = expectedSourceIds.toSet()
    private val hostSlots = linkedMapOf<String, EmbeddedWorkspaceExecutionSurface>()
    private var startConsumed = false
    private var cleanupConverged = false
    private var closed = false
    private var phase = EmbeddedWorkspaceRunnerPhase.IDLE
    private var latestResult: EmbeddedWorkspaceRunResult? = null
    private var receipts: List<EmbeddedWorkspaceItemReceipt> = emptyList()

    private val mutableState = MutableStateFlow(snapshot())
    val state: StateFlow<EmbeddedWorkspaceRunnerControllerState> = mutableState.asStateFlow()

    fun updateHostSlot(sourceCellId: String, surface: EmbeddedWorkspaceExecutionSurface) {
        if (closed || sourceCellId !in expectedSourceIdSet) return
        hostSlots[sourceCellId] = surface
        publish()
    }

    suspend fun start(): EmbeddedWorkspaceRunResult? {
        if (closed || startConsumed || !allExpectedSlotsValid()) {
            publish()
            return null
        }

        startConsumed = true
        phase = EmbeddedWorkspaceRunnerPhase.PREFLIGHT
        publish()

        val request = EmbeddedWorkspaceExecutionRequest(
            plan = plan,
            hostSlots = expectedSourceIds.map { sourceCellId ->
                EmbeddedWorkspaceHostSlot(
                    sourceCellId = sourceCellId,
                    executionSurface = checkNotNull(hostSlots[sourceCellId]),
                )
            },
        )
        val result = runner.start(request)
        applyResult(result)
        return result
    }

    suspend fun stop(): EmbeddedWorkspaceRunResult? {
        if (closed || cleanupConverged) return latestResult

        phase = EmbeddedWorkspaceRunnerPhase.STOPPING
        publish()
        val result = runner.stop()
        applyResult(result)
        return result
    }

    suspend fun sendTouch(
        sourceCellId: String,
        event: EmbeddedTouchEvent,
    ): EmbeddedWorkspaceTouchResult = runner.sendTouch(sourceCellId, event)

    suspend fun surfaceLost(sourceCellId: String): EmbeddedWorkspaceRunResult? {
        if (sourceCellId !in expectedSourceIdSet) return null

        hostSlots.remove(sourceCellId)
        publish()
        if (!startConsumed || cleanupConverged || closed) return latestResult

        phase = EmbeddedWorkspaceRunnerPhase.ROLLING_BACK
        publish()
        val result = runner.surfaceLost(sourceCellId)
        applyResult(result)
        return result
    }

    suspend fun close(): EmbeddedWorkspaceRunResult? {
        if (closed) return latestResult
        closed = true
        publish()
        if (cleanupConverged) return latestResult

        phase = EmbeddedWorkspaceRunnerPhase.STOPPING
        publish()
        val result = runner.stop()
        applyResult(result)
        return result
    }

    private fun applyResult(result: EmbeddedWorkspaceRunResult) {
        latestResult = result
        receipts = result.receiptsOrCurrent()
        phase = when (result) {
            is EmbeddedWorkspaceRunResult.Started -> EmbeddedWorkspaceRunnerPhase.ACTIVE
            is EmbeddedWorkspaceRunResult.Stopped -> EmbeddedWorkspaceRunnerPhase.STOPPED
            is EmbeddedWorkspaceRunResult.RecoveryRequired -> EmbeddedWorkspaceRunnerPhase.RECOVERY_REQUIRED
            is EmbeddedWorkspaceRunResult.PreflightRejected,
            is EmbeddedWorkspaceRunResult.StartFailed,
            is EmbeddedWorkspaceRunResult.CleanupIncomplete -> EmbeddedWorkspaceRunnerPhase.FAILED_CLEAN
            EmbeddedWorkspaceRunResult.DuplicateCall -> phase
        }
        cleanupConverged = result !is EmbeddedWorkspaceRunResult.Started &&
            result !is EmbeddedWorkspaceRunResult.DuplicateCall
        publish()
    }

    private fun EmbeddedWorkspaceRunResult.receiptsOrCurrent(): List<EmbeddedWorkspaceItemReceipt> = when (this) {
        is EmbeddedWorkspaceRunResult.Started -> receipts
        is EmbeddedWorkspaceRunResult.StartFailed -> buildList {
            addAll(receipts)
            partialReceipt?.let { partial ->
                if (none { it.sourceCellId == partial.sourceCellId }) add(partial)
            }
        }.sortedBy { it.order }
        is EmbeddedWorkspaceRunResult.Stopped -> receipts
        is EmbeddedWorkspaceRunResult.CleanupIncomplete -> receipts
        is EmbeddedWorkspaceRunResult.RecoveryRequired -> receipts
        is EmbeddedWorkspaceRunResult.PreflightRejected,
        EmbeddedWorkspaceRunResult.DuplicateCall -> this@EmbeddedWorkspaceRunnerController.receipts
    }

    private fun allExpectedSlotsValid(): Boolean =
        expectedSourceIds.isNotEmpty() && expectedSourceIds.all { hostSlots[it]?.isValid == true }

    private fun publish() {
        mutableState.value = snapshot()
    }

    private fun snapshot(): EmbeddedWorkspaceRunnerControllerState {
        val receiptsBySource = receipts.associateBy { it.sourceCellId }
        return EmbeddedWorkspaceRunnerControllerState(
            phase = phase,
            canStart = !closed && !startConsumed && allExpectedSlotsValid(),
            canStop = !closed && startConsumed && !cleanupConverged,
            slotValidity = expectedSourceIds.associateWith { hostSlots[it]?.isValid == true },
            items = orderedPlanItems.map { item ->
                val receipt = receiptsBySource[item.sourceCellId]
                EmbeddedWorkspaceRunnerItemState(
                    sourceCellId = item.sourceCellId,
                    order = item.order,
                    phase = receipt?.phase,
                    displayId = receipt?.displayId ?: -1,
                )
            },
            latestResult = latestResult,
        )
    }
}
