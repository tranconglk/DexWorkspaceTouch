package com.trancong.dexworkspacetouch.diagnostics.embedded

import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.*

/** Capture ngay bản sao scalar; sink không giữ result/receipt/runtime. */
object EmbeddedResultEvidence {
    fun record(result: EmbeddedWorkspaceRunResult, graph: String?, prefix: String = "runner") = EmbeddedEvidence.observe {
        val failed = result as? EmbeddedWorkspaceRunResult.StartFailed
        val receipts = when (result) {
            is EmbeddedWorkspaceRunResult.Started -> result.receipts
            is EmbeddedWorkspaceRunResult.StartFailed -> result.receipts
            is EmbeddedWorkspaceRunResult.Stopped -> result.receipts
            is EmbeddedWorkspaceRunResult.CleanupIncomplete -> result.receipts
            is EmbeddedWorkspaceRunResult.RecoveryRequired -> result.receipts
            else -> emptyList()
        }
        val outcomes = when (result) {
            is EmbeddedWorkspaceRunResult.StartFailed -> result.rollbackOutcomes
            is EmbeddedWorkspaceRunResult.Stopped -> result.cleanupOutcomes
            is EmbeddedWorkspaceRunResult.CleanupIncomplete -> result.cleanupOutcomes
            is EmbeddedWorkspaceRunResult.RecoveryRequired -> result.cleanupOutcomes
            else -> emptyList()
        }
        val sid = failed?.partialReceipt?.sessionId?.value ?: receipts.firstOrNull()?.sessionId?.value
        EmbeddedEvidence.app("$prefix.result", sid, failed?.sourceCellId, graph, buildMap {
            put("result_kind", result.javaClass.simpleName)
            failed?.let {
                put("failure_code", it.failure.code); it.failure.message?.let { message -> put("message", message) }
                put("all_owned_sessions_clean", it.allOwnedSessionsClean.toString())
            }
        })
        fun receipt(item: EmbeddedWorkspaceItemReceipt, role: String) {
            EmbeddedEvidence.app("$prefix.receipt", item.sessionId.value, item.sourceCellId, graph,
                mapOf("role" to role, "phase" to item.phase.name, "display_id" to item.displayId.toString(), "order" to item.order.toString()))
        }
        receipts.take(2).forEach { receipt(it, "active_receipt") }
        failed?.partialReceipt?.let { receipt(it, "partial_receipt") }
        val receiptByCell = (receipts + listOfNotNull(failed?.partialReceipt)).groupBy { it.sourceCellId }
        outcomes.take(2).forEach { outcome ->
            val failure = when (outcome) {
                is EmbeddedWorkspaceCleanupOutcome.Clean -> null
                is EmbeddedWorkspaceCleanupOutcome.Incomplete -> outcome.failure
                is EmbeddedWorkspaceCleanupOutcome.RecoveryRequired -> outcome.failure
            }
            val outcomeSid = receiptByCell[outcome.sourceCellId]?.map { it.sessionId.value }?.distinct()?.singleOrNull()
            EmbeddedEvidence.app("$prefix.outcome", outcomeSid, outcome.sourceCellId, graph, buildMap {
                put("kind", outcome.javaClass.simpleName)
                failure?.let { put("failure_code", it.code); it.message?.let { message -> put("message", message) } }
            })
        }
        val omitted = (receipts.size - 2).coerceAtLeast(0) + (outcomes.size - 2).coerceAtLeast(0)
        if (omitted > 0) EmbeddedEvidence.app("evidence.truncated", graph = graph, fields = mapOf("count" to omitted.toString()))
        // Marker chỉ kết thúc bản sao diagnostics; không phải acknowledgement execution.
        EmbeddedEvidence.app("$prefix.result.end", sid, failed?.sourceCellId, graph, fields = mapOf(
            "count" to (receipts.take(2).size + (if (failed?.partialReceipt != null) 1 else 0) + outcomes.take(2).size).toString()))
    }
}
