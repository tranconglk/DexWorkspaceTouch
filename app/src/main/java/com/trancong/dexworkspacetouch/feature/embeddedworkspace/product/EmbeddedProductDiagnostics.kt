package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionPhase
import java.time.Instant
import java.util.Collections

enum class EmbeddedDiagnosticProvenance { PRODUCT_RUN_WORKSPACE, PRODUCT_ISSUE, OWNED_ITEM_RESULT, CLEANUP_RESULT }

class EmbeddedDiagnosticId private constructor(val value: String?, val provenance: EmbeddedDiagnosticProvenance?) {
    companion object {
        // Bỏ ID không hợp lệ/quá dài, không cắt rồi trình bày như một ID đầy đủ.
        internal fun copied(value: String?, provenance: EmbeddedDiagnosticProvenance?): EmbeddedDiagnosticId {
            val valid = provenance != null && !value.isNullOrBlank() && value.length <= MAX_ID_CHARS &&
                value.codePoints().allMatch { code ->
                    !Character.isISOControl(code) && !Character.isWhitespace(code) && !Character.isSpaceChar(code) &&
                        code !in 0xD800..0xDFFF && code != '='.code && code != ';'.code
                }
            return if (valid) EmbeddedDiagnosticId(value, provenance) else EmbeddedDiagnosticId(null, null)
        }
        private const val MAX_ID_CHARS = 256
    }
}

class EmbeddedDiagnosticItem internal constructor(
    val sourceCellId: EmbeddedDiagnosticId,
    val ownedSessionId: EmbeddedDiagnosticId,
    val displayId: EmbeddedDiagnosticId,
    val order: Int,
    val phase: EmbeddedSessionPhase,
)

class EmbeddedDiagnosticCleanup internal constructor(
    val sourceCellId: EmbeddedDiagnosticId,
    val evidence: CleanupEvidence,
    val failureCode: String?,
)

class EmbeddedProductDiagnostics private constructor(
    val snapshotEpochMillis: Long,
    val workspaceId: EmbeddedDiagnosticId,
    val phase: ProductRunPhase,
    val generation: Long,
    val startOperationId: Long?,
    val cleanupOperationId: Long?,
    val invocationCategory: ProductInvocationCategory,
    val resultKind: ProductResultKind?,
    val issueCode: String?,
    val issueSourceCellId: EmbeddedDiagnosticId,
    val blockedCause: EmbeddedCleanupBlockedCause?,
    val allocationEvidence: AllocationEvidence,
    val cleanupEvidence: CleanupEvidence,
    val items: List<EmbeddedDiagnosticItem>,
    val omittedItemCount: Int,
    val cleanupOutcomes: List<EmbeddedDiagnosticCleanup>,
    val omittedCleanupCount: Int,
) {
    companion object {
        private const val MAX_ENTRIES = 2
        // Chỉ mã lỗi cleanup có nghĩa đã biết; không xuất payload/mã lạ do remote trả về.
        private val cleanupFailureCodes = setOf("CLEANUP_TIMEOUT", "CLEANUP_INCOMPLETE", "CLEANUP_FAILED",
            "REMOTE_DIED", "SURFACE_LOST", "SURFACE_INVALID", "UNKNOWN_SESSION", "RECOVERY_REQUIRED", "START_FAILED")

        // Đọc đúng một status giá trị. Không giữ status, result gốc hoặc supplier để đọc lại sau này.
        fun from(status: ProductRunStatus, snapshotAtUtc: Instant): EmbeddedProductDiagnostics = EmbeddedProductDiagnostics(
            snapshotAtUtc.toEpochMilli(),
            EmbeddedDiagnosticId.copied(status.token?.workspaceId, EmbeddedDiagnosticProvenance.PRODUCT_RUN_WORKSPACE),
            status.phase, status.generation, status.startOperationId, status.cleanupOperationId,
            status.invocationCategory, status.resultKind, status.issue?.code,
            EmbeddedDiagnosticId.copied(status.issue?.sourceCellId, EmbeddedDiagnosticProvenance.PRODUCT_ISSUE),
            status.cleanupBlockedCause(), status.allocationEvidence, status.cleanupEvidence,
            Collections.unmodifiableList(status.items.take(MAX_ENTRIES).map { item -> EmbeddedDiagnosticItem(
                EmbeddedDiagnosticId.copied(item.sourceCellId, EmbeddedDiagnosticProvenance.OWNED_ITEM_RESULT),
                EmbeddedDiagnosticId.copied(item.sessionId, EmbeddedDiagnosticProvenance.OWNED_ITEM_RESULT),
                EmbeddedDiagnosticId.copied(item.displayId.takeIf { it >= 0 }?.toString(), EmbeddedDiagnosticProvenance.OWNED_ITEM_RESULT),
                item.order, item.phase,
            ) }), (status.items.size - MAX_ENTRIES).coerceAtLeast(0),
            Collections.unmodifiableList(status.cleanupOutcomes.take(MAX_ENTRIES).map { outcome -> EmbeddedDiagnosticCleanup(
                EmbeddedDiagnosticId.copied(outcome.sourceCellId, EmbeddedDiagnosticProvenance.CLEANUP_RESULT),
                outcome.evidence, outcome.failureCode?.takeIf { it in cleanupFailureCodes },
            ) }), (status.cleanupOutcomes.size - MAX_ENTRIES).coerceAtLeast(0),
        )
    }
}
