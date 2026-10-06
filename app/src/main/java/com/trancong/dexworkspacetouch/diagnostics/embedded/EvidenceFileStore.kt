package com.trancong.dexworkspacetouch.diagnostics.embedded

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID
import org.json.JSONObject

/** Chỉ metadata file phục vụ quota; không đọc evidence để quyết định execution. */
class EvidenceFileStore(private val directory: File, private val limits: EvidenceLimits = EvidenceLimits()) {
    private var active: File? = null
    @Synchronized fun append(record: EmbeddedEvidenceEvent): Boolean = runCatching {
        require(directory.isDirectory || directory.mkdirs())
        val bytes = (record.toJson() + "\n").toByteArray(Charsets.UTF_8)
        require(bytes.size <= limits.recordBytes + 1)
        RandomAccessFile(File(directory, ".writer.lock"), "rw").channel.use { channel ->
            val lock = channel.tryLock() ?: return false
            lock.use {
                var file = active?.takeIf { it.isFile && it.length() + bytes.size <= limits.segmentBytes }
                if (file == null) {
                    val old = directory.listFiles().orEmpty().filter { it.name.startsWith("segment-") && it.extension == "jsonl" }
                        .sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.name })
                    var evicted = 0
                    val retained = old.toMutableList()
                    while (retained.any { it.length() > limits.segmentBytes } || retained.size >= limits.segments ||
                        retained.sumOf { it.length() } > (limits.segments - 1L) * limits.segmentBytes) {
                        val candidate = retained.firstOrNull { it.length() > limits.segmentBytes } ?: retained.first()
                        check(candidate.delete()); retained.remove(candidate); evicted++
                    }
                    file = File(directory, "segment-${UUID.randomUUID()}.jsonl")
                    val header = JSONObject().put("record_type", "retention").put("schema", 1)
                        .put("evicted_segments", evicted).put("process_epoch", record.processEpoch)
                        .put("first_sequence", record.sequence).toString() + "\n"
                    check(header.toByteArray(Charsets.UTF_8).size + bytes.size <= limits.segmentBytes)
                    FileOutputStream(file).use { it.write(header.toByteArray(Charsets.UTF_8)) }
                    active = file
                }
                FileOutputStream(file, true).use { it.write(bytes) }
            }
        }
        true
    }.getOrDefault(false)
}

class EvidenceWriter(private val recorder: EvidenceRecorder, private val store: EvidenceFileStore) {
    fun start() {
        Thread({
            while (true) {
                try {
                    val records = recorder.drain()
                    val lost = records.count { !store.append(it) }
                    if (lost > 0) recorder.recordWriteLoss(lost)
                    recorder.flushLossIndicator()
                    Thread.sleep(250)
                } catch (_: Throwable) { /* Writer lỗi không đi vào execution. */ }
            }
        }, "embedded-evidence-${recorder.domain}").apply { isDaemon = true }.start()
    }
}
