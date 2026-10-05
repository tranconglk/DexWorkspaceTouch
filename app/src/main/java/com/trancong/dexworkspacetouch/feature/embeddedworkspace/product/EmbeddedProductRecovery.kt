package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionPhase
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceCleanupOutcome
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspacePreflightRejection
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunResult

enum class AllocationEvidence { NONE_CONFIRMED, POSSIBLE_OR_OWNED, UNKNOWN }
enum class CleanupEvidence { NOT_NEEDED, CLEAN_CONFIRMED, INCOMPLETE, UNCERTAIN }
enum class EmbeddedRecoveryAction { BACK, REQUEST_EXIT, OPEN_CLASSIC, START_EMBEDDED, REFRESH_READINESS, REQUEST_SHIZUKU_PERMISSION }

data class EmbeddedProductRecovery(
    val phase: ProductRunPhase,
    val issue: EmbeddedProductIssue?,
    val allocationEvidence: AllocationEvidence,
    val cleanupEvidence: CleanupEvidence,
    val permittedActions: Set<EmbeddedRecoveryAction>,
)

object EmbeddedProductRecoveryMapper {
    fun snapshot(
        phase: ProductRunPhase,
        gateAvailable: Boolean,
        issue: EmbeddedProductIssue?,
        allocationEvidence: AllocationEvidence,
        cleanupEvidence: CleanupEvidence,
    ): EmbeddedProductRecovery {
        // Gate/operation có thẩm quyền hơn snapshot allocation; không suy IDLE là clean.
        val resolved = phase == ProductRunPhase.IDLE && gateAvailable &&
            (cleanupEvidence == CleanupEvidence.CLEAN_CONFIRMED ||
                (allocationEvidence == AllocationEvidence.NONE_CONFIRMED && cleanupEvidence == CleanupEvidence.NOT_NEEDED))
        val actions = buildSet {
            if (resolved) {
                add(EmbeddedRecoveryAction.BACK)
                add(EmbeddedRecoveryAction.OPEN_CLASSIC)
                when (issue) {
                    is EmbeddedProductIssue.MissingWorkspace, is EmbeddedProductIssue.LaunchNotReady,
                    EmbeddedProductIssue.UnsupportedLayout, is EmbeddedProductIssue.UnsupportedEmbeddedItemCount,
                    is EmbeddedProductIssue.DuplicateTarget, EmbeddedProductIssue.UnsupportedPlatform -> Unit
                    else -> add(EmbeddedRecoveryAction.REFRESH_READINESS)
                }
                // SurfaceLost cũ không chặn run mới sau clean; UI vẫn phải xác nhận readiness host mới.
                if (issue == null || (issue is EmbeddedProductIssue.SurfaceLost &&
                        cleanupEvidence == CleanupEvidence.CLEAN_CONFIRMED)) {
                    add(EmbeddedRecoveryAction.START_EMBEDDED)
                }
                if (issue == EmbeddedProductIssue.ShizukuPermissionMissing) add(EmbeddedRecoveryAction.REQUEST_SHIZUKU_PERMISSION)
            }
            if (phase in setOf(ProductRunPhase.STARTING, ProductRunPhase.ACTIVE, ProductRunPhase.STOPPING)) {
                add(EmbeddedRecoveryAction.REQUEST_EXIT)
            }
        }
        return EmbeddedProductRecovery(phase, issue, allocationEvidence, cleanupEvidence, actions)
    }

    fun eligibility(reason: EmbeddedEligibilityFailure, phase: ProductRunPhase, gateAvailable: Boolean): EmbeddedProductRecovery =
        beforeStart(when (reason) {
            is EmbeddedEligibilityFailure.MissingWorkspace -> EmbeddedProductIssue.MissingWorkspace(reason.workspaceId)
            is EmbeddedEligibilityFailure.LaunchNotReady -> EmbeddedProductIssue.LaunchNotReady(reason.reason)
            EmbeddedEligibilityFailure.UnsupportedLayout -> EmbeddedProductIssue.UnsupportedLayout
            is EmbeddedEligibilityFailure.UnsupportedEmbeddedItemCount -> EmbeddedProductIssue.UnsupportedEmbeddedItemCount(reason.count)
            is EmbeddedEligibilityFailure.DuplicateTarget -> EmbeddedProductIssue.DuplicateTarget()
            is EmbeddedEligibilityFailure.GeometryUnavailable -> EmbeddedProductIssue.GeometryUnavailable(reason.sourceCellId)
        }, phase, gateAvailable)

    fun readiness(reason: EmbeddedReadinessResult, phase: ProductRunPhase, gateAvailable: Boolean): EmbeddedProductRecovery =
        beforeStart(when (reason) {
            EmbeddedReadinessResult.Ready -> null
            EmbeddedReadinessResult.UnsupportedPlatform -> EmbeddedProductIssue.UnsupportedPlatform
            EmbeddedReadinessResult.ShizukuUnavailable -> EmbeddedProductIssue.ShizukuUnavailable
            EmbeddedReadinessResult.ShizukuPermissionMissing -> EmbeddedProductIssue.ShizukuPermissionMissing
            EmbeddedReadinessResult.GeometryUnavailable -> EmbeddedProductIssue.GeometryUnavailable()
            EmbeddedReadinessResult.RendererNotReady -> EmbeddedProductIssue.RendererNotReady()
        }, phase, gateAvailable)

    private fun beforeStart(issue: EmbeddedProductIssue?, phase: ProductRunPhase, gateAvailable: Boolean): EmbeddedProductRecovery {
        val absent = phase == ProductRunPhase.IDLE && gateAvailable
        return snapshot(phase, gateAvailable, issue,
            if (absent) AllocationEvidence.NONE_CONFIRMED else AllocationEvidence.UNKNOWN,
            if (absent) CleanupEvidence.NOT_NEEDED else CleanupEvidence.UNCERTAIN)
    }

    // Caller chỉ cung cấp result của đúng token/run đang được theo dõi; không tái dựng run sau restart.
    fun result(result: EmbeddedWorkspaceRunResult?, phase: ProductRunPhase, gateAvailable: Boolean): EmbeddedProductRecovery {
        var issue: EmbeddedProductIssue? = null
        var allocation = AllocationEvidence.UNKNOWN
        var cleanup = CleanupEvidence.UNCERTAIN
        when (result) {
            is EmbeddedWorkspaceRunResult.PreflightRejected -> {
                issue = preflightIssue(result.rejection)
                allocation = AllocationEvidence.NONE_CONFIRMED
                cleanup = CleanupEvidence.NOT_NEEDED
            }
            is EmbeddedWorkspaceRunResult.Started -> {
                if (result.receipts.isNotEmpty()) allocation = AllocationEvidence.POSSIBLE_OR_OWNED
            }
            is EmbeddedWorkspaceRunResult.StartFailed -> {
                issue = when (result.failure.code) {
                    "SURFACE_LOST", "SURFACE_INVALID" -> EmbeddedProductIssue.SurfaceLost(result.sourceCellId)
                    "REMOTE_DIED" -> EmbeddedProductIssue.RemoteDied(result.sourceCellId)
                    else -> EmbeddedProductIssue.RuntimeStartFailed(result.sourceCellId)
                }
                val ownedIds = (result.receipts.map { it.sourceCellId } + listOfNotNull(result.partialReceipt?.sourceCellId)).toSet()
                if (ownedIds.isNotEmpty() || result.rollbackOutcomes.isNotEmpty()) allocation = AllocationEvidence.POSSIBLE_OR_OWNED
                val cleanIds = result.rollbackOutcomes.map { it.sourceCellId }
                cleanup = when {
                    result.allOwnedSessionsClean && result.rollbackOutcomes.all { it is EmbeddedWorkspaceCleanupOutcome.Clean } &&
                        cleanIds.size == cleanIds.toSet().size && cleanIds.containsAll(ownedIds) -> CleanupEvidence.CLEAN_CONFIRMED
                    result.rollbackOutcomes.any { it is EmbeddedWorkspaceCleanupOutcome.Incomplete } -> CleanupEvidence.INCOMPLETE
                    else -> CleanupEvidence.UNCERTAIN
                }
            }
            is EmbeddedWorkspaceRunResult.Stopped -> {
                if (result.receipts.isNotEmpty()) allocation = AllocationEvidence.POSSIBLE_OR_OWNED
                val ownedIds = result.receipts.map { it.sourceCellId }
                val cleanIds = result.cleanupOutcomes.map { it.sourceCellId }
                cleanup = if (ownedIds.isNotEmpty() && ownedIds.size == ownedIds.toSet().size &&
                    cleanIds.size == ownedIds.size && cleanIds.toSet() == ownedIds.toSet() &&
                    result.cleanupOutcomes.all { it is EmbeddedWorkspaceCleanupOutcome.Clean }) CleanupEvidence.CLEAN_CONFIRMED
                else CleanupEvidence.UNCERTAIN
                if (cleanup != CleanupEvidence.CLEAN_CONFIRMED) issue = EmbeddedProductIssue.CleanupOutcomeUncertain()
            }
            is EmbeddedWorkspaceRunResult.CleanupIncomplete -> {
                issue = EmbeddedProductIssue.RuntimeCleanupIncomplete(result.cleanupOutcomes.firstOrNull {
                    it is EmbeddedWorkspaceCleanupOutcome.Incomplete
                }?.sourceCellId)
                if (result.receipts.isNotEmpty() || result.cleanupOutcomes.isNotEmpty()) allocation = AllocationEvidence.POSSIBLE_OR_OWNED
                if (result.cleanupOutcomes.any { it is EmbeddedWorkspaceCleanupOutcome.Incomplete }) cleanup = CleanupEvidence.INCOMPLETE
            }
            is EmbeddedWorkspaceRunResult.RecoveryRequired -> {
                val remoteOutcome = result.cleanupOutcomes.firstOrNull { it is EmbeddedWorkspaceCleanupOutcome.RecoveryRequired }
                val remoteReceipt = result.receipts.firstOrNull { it.phase == EmbeddedSessionPhase.REMOTE_DIED }
                issue = if (remoteOutcome != null || remoteReceipt != null) EmbeddedProductIssue.RemoteDied(
                    result.sourceCellId ?: remoteOutcome?.sourceCellId ?: remoteReceipt?.sourceCellId)
                else EmbeddedProductIssue.CleanupOutcomeUncertain(result.sourceCellId)
                if (result.receipts.isNotEmpty() || result.cleanupOutcomes.isNotEmpty()) allocation = AllocationEvidence.POSSIBLE_OR_OWNED
            }
            EmbeddedWorkspaceRunResult.DuplicateCall, null -> issue = EmbeddedProductIssue.CleanupOutcomeUncertain()
        }
        return snapshot(phase, gateAvailable, issue, allocation, cleanup)
    }

    private fun preflightIssue(reason: EmbeddedWorkspacePreflightRejection): EmbeddedProductIssue = when (reason) {
        is EmbeddedWorkspacePreflightRejection.MissingSlot -> EmbeddedProductIssue.RendererNotReady(reason.sourceCellId)
        is EmbeddedWorkspacePreflightRejection.UnknownSlot -> EmbeddedProductIssue.RendererNotReady(reason.sourceCellId)
        is EmbeddedWorkspacePreflightRejection.DuplicateSlot -> EmbeddedProductIssue.RendererNotReady(reason.sourceCellId)
        is EmbeddedWorkspacePreflightRejection.InvalidSurface -> EmbeddedProductIssue.RendererNotReady(reason.sourceCellId)
        is EmbeddedWorkspacePreflightRejection.DuplicateTargetIdentity -> EmbeddedProductIssue.DuplicateTarget(reason.sourceCellIds.firstOrNull())
        is EmbeddedWorkspacePreflightRejection.GeometryRejected -> EmbeddedProductIssue.GeometryUnavailable(reason.sourceCellId)
        is EmbeddedWorkspacePreflightRejection.InvalidPlan -> EmbeddedProductIssue.UnsupportedLayout
    }
}

fun EmbeddedProductRecovery.message(): String {
    val cause = issue?.message() ?: when (phase) {
        ProductRunPhase.IDLE -> if (cleanupEvidence == CleanupEvidence.CLEAN_CONFIRMED) "Đã dừng Embedded."
            else "Thiết bị đáp ứng kiểm tra khả năng; Embedded vẫn đang Thử nghiệm trên thiết bị này."
        ProductRunPhase.STARTING -> "Đang khởi động Embedded."
        ProductRunPhase.ACTIVE -> "Embedded đang chạy (Thử nghiệm)."
        ProductRunPhase.STOPPING -> "Đang dọn Embedded."
        ProductRunPhase.CLEANUP_BLOCKED -> "Chưa xác nhận được kết quả dọn Embedded."
    }
    return when (cleanupEvidence) {
        CleanupEvidence.CLEAN_CONFIRMED -> "$cause Cleanup của run đã được xác nhận."
        CleanupEvidence.INCOMPLETE, CleanupEvidence.UNCERTAIN -> if (phase == ProductRunPhase.CLEANUP_BLOCKED || issue != null) {
            "$cause Chưa xác nhận cleanup sạch; tạm khóa mở Workspace khác."
        } else cause
        CleanupEvidence.NOT_NEEDED -> cause
    }
}
