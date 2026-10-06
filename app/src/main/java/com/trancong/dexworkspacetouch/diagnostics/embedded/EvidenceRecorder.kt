package com.trancong.dexworkspacetouch.diagnostics.embedded

import java.util.ArrayDeque

/** Queue giới hạn; không I/O hoặc callback trên caller. */
open class EvidenceRecorder @JvmOverloads constructor(
    val domain: String, val pid: Int, val uid: Int, val epoch: String, val limits: EvidenceLimits = EvidenceLimits(),
) {
    init { require(domain == "app" || domain == "remote"); require(EvidencePrivacy.identifier(epoch) != null) }
    private val queue = ArrayDeque<EmbeddedEvidenceEvent>()
    private var sequence = 0L
    private var dropped = 0L
    @Synchronized open fun emit(event: String, sid: String? = null, cell: String? = null, graph: String? = null,
        fields: Map<String, String> = emptyMap()) {
        if (sid == "db621ed2-c769-408e-b901-7679ee03bd4f") return
        val seq = ++sequence
        if (queue.size == limits.queueRecords || EvidencePrivacy.identifier(event) == null) { dropped++; return }
        var omitted = 0; var truncated = 0; var redacted = 0
        val copied = linkedMapOf<String, String>()
        for ((key, raw) in fields) {
            if (copied.size == 24) { omitted++; continue }
            if (raw.length > 128) truncated++
            val value = EvidencePrivacy.field(key, raw)
            if (value == null) {
                omitted++
                if (key == "message") { redacted++; copied["message_reason"] = "OMITTED_UNSAFE_OR_UNRECOGNIZED" }
            } else copied[key] = value
        }
        val safeSid = EvidencePrivacy.identifier(sid); val safeCell = EvidencePrivacy.identifier(cell)
        val safeGraph = EvidencePrivacy.identifier(graph)
        omitted += listOf(sid to safeSid, cell to safeCell, graph to safeGraph).count { it.first != null && it.second == null }
        val utc = System.currentTimeMillis(); val mono = System.nanoTime()
        fun record() = EmbeddedEvidenceEvent(domain, epoch, pid, uid, seq, utc, mono, event,
            safeSid, safeCell, safeGraph, copied, omitted, truncated, redacted, dropped)
        var value = record()
        while (value.toJson().toByteArray(Charsets.UTF_8).size > limits.recordBytes && copied.isNotEmpty()) {
            copied.remove(copied.keys.last()); omitted++; truncated++; value = record()
        }
        if (value.toJson().toByteArray(Charsets.UTF_8).size > limits.recordBytes) { dropped++; return }
        queue.addLast(value); dropped = 0
    }
    @Synchronized fun drain(): List<EmbeddedEvidenceEvent> = queue.toList().also { queue.clear() }
    @Synchronized fun recordWriteLoss(count: Int) { dropped += count }
    @Synchronized fun flushLossIndicator() {
        if (dropped > 0 && queue.size < limits.queueRecords) emit("evidence.loss", fields = mapOf("count" to dropped.toString()))
    }
}
