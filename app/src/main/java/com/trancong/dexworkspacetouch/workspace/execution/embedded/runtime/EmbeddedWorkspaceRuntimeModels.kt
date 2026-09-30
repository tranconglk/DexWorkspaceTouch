package com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime

import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppGeometry
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppSessionId
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppTarget
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionFailure
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionPhase
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlan
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlanItem
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface EmbeddedWorkspaceExecutionSurface {
    val isValid: Boolean
}

data class EmbeddedWorkspaceHostSlot(
    val sourceCellId: String,
    val executionSurface: EmbeddedWorkspaceExecutionSurface,
)

data class EmbeddedWorkspaceExecutionRequest(
    val plan: EmbeddedWorkspacePlan,
    val hostSlots: List<EmbeddedWorkspaceHostSlot>,
)

fun interface EmbeddedGeometryPolicy {
    fun geometryFor(item: EmbeddedWorkspacePlanItem): EmbeddedAppGeometry
}

data class EmbeddedWorkspaceRunnerTimeoutPolicy(
    val readyTimeout: Duration,
    val activeTimeout: Duration,
    val cleanupTimeout: Duration,
)

val PROOF_EMBEDDED_WORKSPACE_TIMEOUTS = EmbeddedWorkspaceRunnerTimeoutPolicy(
    readyTimeout = 10.seconds,
    activeTimeout = 30.seconds,
    cleanupTimeout = 20.seconds,
)

data class EmbeddedWorkspacePreparedItem(
    val planItem: EmbeddedWorkspacePlanItem,
    val target: EmbeddedAppTarget,
    val executionSurface: EmbeddedWorkspaceExecutionSurface,
)

sealed interface EmbeddedWorkspacePreflightResult {
    data class Prepared(val items: List<EmbeddedWorkspacePreparedItem>) : EmbeddedWorkspacePreflightResult
    data class Rejected(val rejection: EmbeddedWorkspacePreflightRejection) : EmbeddedWorkspacePreflightResult
}

sealed interface EmbeddedWorkspacePreflightRejection {
    data class MissingSlot(val sourceCellId: String) : EmbeddedWorkspacePreflightRejection
    data class UnknownSlot(val sourceCellId: String) : EmbeddedWorkspacePreflightRejection
    data class DuplicateSlot(val sourceCellId: String) : EmbeddedWorkspacePreflightRejection
    data class InvalidSurface(val sourceCellId: String) : EmbeddedWorkspacePreflightRejection
    data class DuplicateTargetIdentity(
        val packageName: String,
        val componentName: String,
        val sourceCellIds: List<String>,
    ) : EmbeddedWorkspacePreflightRejection
    data class GeometryRejected(val sourceCellId: String, val message: String?) : EmbeddedWorkspacePreflightRejection
    data class InvalidPlan(val message: String) : EmbeddedWorkspacePreflightRejection
}

enum class EmbeddedWorkspaceRunnerPhase {
    IDLE, PREFLIGHT, STARTING, ACTIVE, ROLLING_BACK, FAILED_CLEAN, STOPPING, STOPPED, RECOVERY_REQUIRED,
}

data class EmbeddedWorkspaceItemReceipt(
    val sourceCellId: String,
    val sessionId: EmbeddedAppSessionId,
    val packageName: String,
    val componentName: String,
    val order: Int,
    val phase: EmbeddedSessionPhase,
    val displayId: Int = -1,
)

sealed interface EmbeddedWorkspaceCleanupOutcome {
    val sourceCellId: String

    data class Clean(override val sourceCellId: String) : EmbeddedWorkspaceCleanupOutcome
    data class Incomplete(override val sourceCellId: String, val failure: EmbeddedSessionFailure?) : EmbeddedWorkspaceCleanupOutcome
    data class RecoveryRequired(override val sourceCellId: String, val failure: EmbeddedSessionFailure?) : EmbeddedWorkspaceCleanupOutcome
}

sealed interface EmbeddedWorkspaceRunResult {
    data class Started(val receipts: List<EmbeddedWorkspaceItemReceipt>) : EmbeddedWorkspaceRunResult
    data class PreflightRejected(val rejection: EmbeddedWorkspacePreflightRejection) : EmbeddedWorkspaceRunResult
    data class StartFailed(
        val sourceCellId: String,
        val failure: EmbeddedSessionFailure,
        val receipts: List<EmbeddedWorkspaceItemReceipt>,
        val partialReceipt: EmbeddedWorkspaceItemReceipt?,
        val rollbackOutcomes: List<EmbeddedWorkspaceCleanupOutcome>,
        val allOwnedSessionsClean: Boolean,
    ) : EmbeddedWorkspaceRunResult
    data class Stopped(
        val receipts: List<EmbeddedWorkspaceItemReceipt>,
        val cleanupOutcomes: List<EmbeddedWorkspaceCleanupOutcome>,
    ) : EmbeddedWorkspaceRunResult
    data class CleanupIncomplete(
        val receipts: List<EmbeddedWorkspaceItemReceipt>,
        val cleanupOutcomes: List<EmbeddedWorkspaceCleanupOutcome>,
    ) : EmbeddedWorkspaceRunResult
    data class RecoveryRequired(
        val sourceCellId: String?,
        val receipts: List<EmbeddedWorkspaceItemReceipt>,
        val cleanupOutcomes: List<EmbeddedWorkspaceCleanupOutcome>,
    ) : EmbeddedWorkspaceRunResult
    data object DuplicateCall : EmbeddedWorkspaceRunResult
}
