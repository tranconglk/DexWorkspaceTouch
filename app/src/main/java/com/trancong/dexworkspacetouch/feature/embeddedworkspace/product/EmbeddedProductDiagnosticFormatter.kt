package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import java.time.Instant

object EmbeddedProductDiagnosticFormatter {
    // DTO giới hạn 2 item + 2 outcome, mỗi ID tối đa 256 ký tự: v1 luôn nhỏ hơn giới hạn này.
    const val MAX_OUTPUT_CHARS = 8192
    fun format(snapshot: EmbeddedProductDiagnostics?): String {
        if (snapshot == null) return "Chưa có chẩn đoán Embedded"
        return buildString {
            fun field(key: String, value: Any?) { append('\n').append(key).append('=').append(value ?: "unknown") }
            fun id(key: String, value: EmbeddedDiagnosticId? = null) {
                field(key, value?.value)
                append(";provenance=").append(value?.provenance?.name ?: "unknown")
            }
            append("DWT Embedded diagnostics v1")
            field("snapshot_utc", Instant.ofEpochMilli(snapshot.snapshotEpochMillis).toString())
            id("workspace_id", snapshot.workspaceId)
            field("phase", snapshot.phase.name)
            id("run_identity")
            field("generation", snapshot.generation)
            field("start_operation_id", snapshot.startOperationId)
            field("cleanup_operation_id", snapshot.cleanupOperationId)
            field("invocation", snapshot.invocationCategory.name)
            // Application status chưa lưu các giá trị này; không truy vấn runtime/capability để bù.
            field("readiness", null)
            field("shizuku", null)
            field("result_kind", snapshot.resultKind?.name)
            field("issue_code", snapshot.issueCode)
            id("issue_source_cell_id", snapshot.issueSourceCellId)
            field("blocked_cause", snapshot.blockedCause?.name)
            field("allocation_evidence", snapshot.allocationEvidence.name)
            field("cleanup_evidence", snapshot.cleanupEvidence.name)
            id("vdm_id")
            id("task_id")
            id("remote_resource_id")
            field("items", "${snapshot.items.size};omitted=${snapshot.omittedItemCount}")
            snapshot.items.forEachIndexed { index, item ->
                val prefix = "item[$index]"
                id("$prefix.source_cell_id", item.sourceCellId)
                id("$prefix.owned_session_id", item.ownedSessionId)
                id("$prefix.display_id", item.displayId)
                field("$prefix.order", item.order)
                field("$prefix.phase", item.phase.name)
            }
            field("cleanup_outcomes", "${snapshot.cleanupOutcomes.size};omitted=${snapshot.omittedCleanupCount}")
            snapshot.cleanupOutcomes.forEachIndexed { index, outcome ->
                val prefix = "cleanup[$index]"
                id("$prefix.source_cell_id", outcome.sourceCellId)
                field("$prefix.evidence", outcome.evidence.name)
                field("$prefix.failure_code", outcome.failureCode)
            }
        }
    }
}

// Seam thuộc UI; không đặt writer/callback vào DTO hoặc Application gate.
internal class EmbeddedProductDiagnosticCopyAction(
    private val text: String?,
    private val writeClipboard: (String) -> Unit,
) {
    fun onUserTap() { text?.let(writeClipboard) }
}
