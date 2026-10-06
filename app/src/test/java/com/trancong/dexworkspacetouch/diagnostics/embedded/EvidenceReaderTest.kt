package com.trancong.dexworkspacetouch.diagnostics.embedded

import com.trancong.dexworkspacetouch.diagnostics.embedded.offline.EvidenceReader
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppSessionId
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionPhase
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.*

class EvidenceReaderTest {
    @Test fun contiguousPrefixWithoutResultTailCannotVerifyCoverage() {
        val rows = completeTimeline()
        val appPrefix = rows.filter { it.getString("domain") == "app" }.takeWhile { it.getString("event") != "runner.receipt" }
        val remote = rows.filter { it.getString("domain") == "remote" }
        assertEquals("NOT_VERIFIED", EvidenceReader.inspect((appPrefix + remote).map { it.toString() }).verification)
    }
    @Test fun missingProductProjectionAfterResultEndCannotVerifyCoverage() {
        val rows = completeTimeline()
        val truncated = rows.filter { it.getString("event") !in setOf("product.projection", "product.acceptance") }
        assertEquals("NOT_VERIFIED", EvidenceReader.inspect(truncated.map { it.toString() }).verification)
    }
    @Test fun unknownTopLevelDataAndUnsafeIdentifiersCannotBeExported() {
        for (mutation in listOf<(JSONObject) -> Unit>(
            { it.put("license_token", "secret") }, { it.put("process_epoch", "unsafe secret") },
            { it.put("sid", "unsafe secret") }, { it.put("cell", "unsafe secret") }, { it.put("graph", "unsafe secret") },
        )) {
            val rows = completeTimeline()
            mutation(rows.single { it.getString("event") == "start.failure" })
            val report = EvidenceReader.inspect(rows.map { it.toString() })
            assertTrue(report.reasons.contains("UNSAFE_SCHEMA"))
            assertEquals("NOT_VERIFIED", report.verification)
            assertFalse(report.observedFailures.any { "secret" in it })
        }
    }
    @Test fun twoCellTimelineUsesGraphResultAndCellSpecificOutcomeSid() {
        val report = EvidenceReader.inspect(completeTimeline(twoCells = true).map { it.toString() })
        assertEquals("OBSERVED", report.verification)
        assertEquals(setOf("012-a", "012-b"), report.linkedSids.keys)
        val recorder = EvidenceRecorder("app", 1, 2, "app")
        EmbeddedEvidence.installApp(recorder)
        try {
            val receipts = listOf("a", "b").mapIndexed { index, cell ->
                EmbeddedWorkspaceItemReceipt(cell, EmbeddedAppSessionId("012-$cell"), "unused", "unused", index, EmbeddedSessionPhase.STOPPED)
            }
            EmbeddedResultEvidence.record(EmbeddedWorkspaceRunResult.Stopped(receipts,
                listOf(EmbeddedWorkspaceCleanupOutcome.Clean("a"), EmbeddedWorkspaceCleanupOutcome.Clean("b"))), "graph")
            assertEquals("012-b", recorder.drain().single { it.event == "runner.outcome" && it.cell == "b" }.sid)
        } finally { EmbeddedEvidence.installApp(null) }
    }
    @Test fun outcomeSidMustFollowItsCellInActualResultCapture() {
        val recorder = EvidenceRecorder("app", 1, 2, "app")
        EmbeddedEvidence.installApp(recorder)
        try {
            val receipts = listOf("a", "b").mapIndexed { index, cell ->
                EmbeddedWorkspaceItemReceipt(cell, EmbeddedAppSessionId("012-$cell"), "unused", "unused", index, EmbeddedSessionPhase.STOPPED)
            }
            EmbeddedResultEvidence.record(EmbeddedWorkspaceRunResult.Stopped(receipts,
                listOf(EmbeddedWorkspaceCleanupOutcome.Clean("a"), EmbeddedWorkspaceCleanupOutcome.Clean("b"))), "graph")
            assertEquals("012-b", recorder.drain().single { it.event == "runner.outcome" && it.cell == "b" }.sid)
        } finally { EmbeddedEvidence.installApp(null) }
    }
    private fun completeTimeline(twoCells: Boolean = false): List<JSONObject> {
        val app = EvidenceRecorder("app", 1, 2, "app")
        val remote = EvidenceRecorder("remote", 3, 2000, "remote")
        val cells = if (twoCells) listOf("a", "b") else listOf("a")
        app.emit("product.operation", graph = "graph", fields = mapOf("product_generation" to "7", "start_operation" to "9"))
        cells.forEach { cell ->
            app.emit("session.link", "012-$cell", cell, "graph")
            remote.emit("start.entry", "012-$cell")
            remote.emit(if (cell == "a") "start.failure" else "start.success", "012-$cell")
        }
        app.emit("runner.result", "012-a", graph = "graph", fields = mapOf("result_kind" to "StartFailed"))
        cells.forEach { cell ->
            app.emit("runner.receipt", "012-$cell", cell, "graph", mapOf("role" to "active_receipt"))
            app.emit("runner.outcome", "012-$cell", cell, "graph", mapOf("kind" to "Incomplete"))
        }
        app.emit("runner.result.end", "012-a", graph = "graph", fields = mapOf("count" to (cells.size * 2).toString()))
        app.emit("product.projection", graph = "graph", fields = mapOf("product_generation" to "7", "start_operation" to "9"))
        app.emit("product.acceptance", graph = "graph", fields = mapOf("accepted" to "true"))
        return (app.drain() + remote.drain()).map { JSONObject(it.toJson()) }
    }
    @Test fun unknownFieldCannotBecomeAnExportedRawCause() {
        val recorder = EvidenceRecorder("remote", 1, 2000, "epoch")
        recorder.emit("start.failure", "012-sid")
        val row = JSONObject(recorder.drain().single().toJson())
        row.getJSONObject("fields").put("license_token", "secret")
        val report = EvidenceReader.inspect(listOf(row.toString()))
        assertTrue(report.reasons.contains("UNSAFE_SCHEMA"))
        assertTrue(report.observedFailures.isEmpty())
    }
    @Test fun missingSequenceCannotVerifyCauseOrCleanup() {
        val recorder = EvidenceRecorder("app", 1, 2, "epoch")
        recorder.emit("session.link", "012-sid", "a", "graph")
        recorder.emit("session.failure", "012-sid", fields = mapOf("message" to "Received Surface became invalid"))
        recorder.emit("runner.result", "012-sid")
        val rows = recorder.drain().map { it.toJson() }
        val report = EvidenceReader.inspect(listOf(rows.first(), rows.last()))
        assertEquals("NOT_VERIFIED", report.verification)
        assertTrue(report.reasons.contains("SEQUENCE_GAP"))
        assertTrue(report.observedFailures.isEmpty())
    }
    @Test fun incompleteTailAndEvictedSegmentsStayNotVerified() {
        val report = EvidenceReader.inspect(listOf("{\"record_type\":\"retention\",\"evicted_segments\":1}", "{partial"))
        assertEquals("NOT_VERIFIED", report.verification)
        assertTrue(report.reasons.contains("EVICTED")); assertTrue(report.reasons.contains("MALFORMED_OR_PARTIAL"))
    }
    @Test fun redactedOrTruncatedCauseIsNotInvented() {
        val recorder = EvidenceRecorder("remote", 1, 2000, "remote")
        recorder.emit("start.failure", "012-sid", fields = mapOf("message" to "unsafe secret"))
        val report = EvidenceReader.inspect(recorder.drain().map { it.toJson() })
        assertEquals("NOT_VERIFIED", report.verification)
        assertTrue(report.reasons.contains("OMITTED_OR_TRUNCATED"))
    }
    @Test fun linksSidThroughGraphWithoutTreatingTokenAsAuthority() {
        val app = EvidenceRecorder("app", 1, 2, "app")
        val remote = EvidenceRecorder("remote", 3, 2000, "remote")
        app.emit("product.operation", graph = "graph", fields = mapOf("product_generation" to "7", "start_operation" to "9"))
        app.emit("session.link", "012-sid", "cell", "graph")
        remote.emit("start.entry", "012-sid")
        val report = EvidenceReader.inspect((app.drain() + remote.drain()).map { it.toJson() })
        assertEquals("app/graph/7/9/cell", report.linkedSids["012-sid"])
        assertEquals("NOT_VERIFIED", report.verification) // Không có terminal timeline.
    }
}
