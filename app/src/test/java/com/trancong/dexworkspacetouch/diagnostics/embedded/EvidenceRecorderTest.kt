package com.trancong.dexworkspacetouch.diagnostics.embedded

import org.junit.Assert.*
import org.junit.Test

class EvidenceRecorderTest {
    @Test fun unknownRemoteFailureCodeCannotSmuggleSecretIntoEvidence() {
        val recorder = EvidenceRecorder("app", 1, 2, "epoch")
        recorder.emit("ipc.raw_reply", fields = mapOf("failure_code" to "license_secret_012"))
        val record = recorder.drain().single()
        assertFalse(record.toJson().contains("license_secret_012"))
        assertTrue(record.omittedFields > 0)
    }
    @Test fun recordsImmutableStageSidOperationAndProcessProvenance() {
        val recorder = EvidenceRecorder("remote", 23, 2000, "remote-epoch")
        val fields = mutableMapOf("stage" to "input_stability", "ipc_operation" to "41")
        recorder.emit("start.begin", "012-new-sid", "cell-a", "graph-a", fields)
        fields["stage"] = "wrong"
        val records = recorder.drain()
        assertEquals("Missing stage/correlation evidence", 1, records.size)
        val record = records.single()
        assertEquals("input_stability", record.fields["stage"])
        assertEquals("41", record.fields["ipc_operation"])
        assertEquals("012-new-sid", record.sid)
        assertEquals(23, record.pid); assertEquals(2000, record.uid)
        assertEquals("remote-epoch", record.processEpoch)
        assertTrue(record.utcMillis > 0); assertTrue(record.monotonicNanos > 0)
        assertEquals(1L, record.sequence)
    }

    @Test fun queueOverflowHasSequenceGapAndDropIndicator() {
        val recorder = EvidenceRecorder("app", 1, 2, "epoch", EvidenceLimits(queueRecords = 1))
        recorder.emit("start.begin"); recorder.emit("start.failure")
        assertEquals(1, recorder.drain().size)
        recorder.emit("runner.result")
        val last = recorder.drain().single()
        assertEquals(3L, last.sequence); assertEquals(1L, last.droppedBefore)
    }

    @Test fun secretsAndArbitraryOutputsAreOmittedAndOversizeFieldsAreExplicit() {
        val recorder = EvidenceRecorder("app", 1, 2, "epoch", EvidenceLimits(recordBytes = 1024))
        recorder.emit("ipc.failure", fields = mapOf("message" to "license token secret unrelated window dump",
            "license_token" to "secret", "stage" to "x".repeat(300), "exception_type" to "java.lang.IllegalStateException"))
        val records = recorder.drain()
        assertEquals(1, records.size)
        val record = records.single()
        assertFalse(record.toJson().contains("secret")); assertFalse(record.toJson().contains("window"))
        assertTrue(record.omittedFields > 0); assertTrue(record.truncatedFields > 0)
        assertTrue(record.redactedFields > 0)
        assertTrue(record.toJson().toByteArray().size <= 1024)
    }
}
