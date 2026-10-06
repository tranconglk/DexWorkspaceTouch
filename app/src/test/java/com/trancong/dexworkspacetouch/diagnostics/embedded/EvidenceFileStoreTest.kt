package com.trancong.dexworkspacetouch.diagnostics.embedded

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EvidenceFileStoreTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun oversizedExistingDiagnosticSegmentCannotExceedStorageQuota() {
        val dir = folder.newFolder()
        java.io.File(dir, "segment-old.jsonl").writeText("x".repeat(9000))
        val limits = EvidenceLimits(recordBytes = 1024, segmentBytes = 2048, segments = 2)
        val store = EvidenceFileStore(dir, limits)
        val recorder = EvidenceRecorder("remote", 1, 2000, "epoch", limits)
        recorder.emit("start.entry")
        assertTrue(store.append(recorder.drain().single()))
        assertTrue(dir.listFiles()!!.sumOf { it.length() } <= 4096)
    }
    @Test fun storageIsBoundedAcrossNewWritersAndReportsEviction() {
        val dir = folder.newFolder()
        val limits = EvidenceLimits(recordBytes = 1024, segmentBytes = 2048, segments = 2)
        repeat(3) { process ->
            val recorder = EvidenceRecorder("remote", process, 2000, "epoch-$process", limits)
            val store = EvidenceFileStore(dir, limits)
            repeat(30) {
                recorder.emit("start.begin", "sid-$process", fields = mapOf("stage" to "association"))
                assertTrue("Durable append missing", store.append(recorder.drain().single()))
            }
        }
        val segments = dir.listFiles()!!.filter { it.extension == "jsonl" }
        assertTrue(segments.isNotEmpty()); assertTrue(segments.size <= 2)
        assertTrue(segments.sumOf { it.length() } <= 4096)
        assertTrue(segments.all { it.length() <= 2048 })
        assertTrue(segments.any { it.readText().contains("\"evicted_segments\":1") })
    }
    @Test fun appendNeverRewritesEarlierRecords() {
        val dir = folder.newFolder(); val store = EvidenceFileStore(dir)
        val recorder = EvidenceRecorder("app", 1, 2, "epoch")
        recorder.emit("start.begin"); assertTrue(store.append(recorder.drain().single()))
        val first = dir.listFiles()!!.single { it.extension == "jsonl" }.readText()
        recorder.emit("start.failure"); assertTrue(store.append(recorder.drain().single()))
        assertTrue(dir.listFiles()!!.single { it.extension == "jsonl" }.readText().startsWith(first))
    }
}
