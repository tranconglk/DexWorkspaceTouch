package com.trancong.dexworkspacetouch.diagnostics.embedded.offline

import org.json.JSONObject
import java.io.File
import com.trancong.dexworkspacetouch.diagnostics.embedded.EvidencePrivacy

/** Chỉ công cụ offline gọi reader; runtime/gate không phụ thuộc reader. */
data class EvidenceReadReport(val verification: String, val reasons: List<String>,
    val linkedSids: Map<String, String>, val observedFailures: List<String>)

object EvidenceReader {
    private val recordKeys = setOf("schema", "provenance", "domain", "process_epoch", "pid", "uid", "sequence",
        "utc_ms", "monotonic_ns", "event", "sid", "cell", "graph", "fields", "omitted_fields",
        "truncated_fields", "redacted_fields", "dropped_before")
    private fun safeRecord(row: JSONObject): Boolean {
        if (row.keys().asSequence().toSet() != recordKeys) return false
        fun integer(key: String) = row.get(key) is Int || row.get(key) is Long
        if (listOf("schema", "pid", "uid", "sequence", "utc_ms", "monotonic_ns", "omitted_fields",
                "truncated_fields", "redacted_fields", "dropped_before").any { !integer(it) }) return false
        if (row.getInt("schema") != 1 || row.getString("provenance") != "LOCAL_OBSERVATION" ||
            row.getString("domain") !in setOf("app", "remote") || row.getLong("sequence") <= 0 ||
            row.getInt("pid") <= 0 || row.getInt("uid") < 0 || row.getLong("utc_ms") <= 0) return false
        if (listOf("omitted_fields", "truncated_fields", "redacted_fields", "dropped_before").any { row.getLong(it) < 0 }) return false
        for (key in listOf("process_epoch", "event", "sid", "cell", "graph")) {
            if (key in setOf("sid", "cell", "graph") && row.isNull(key)) continue
            val value = row.get(key)
            if (value !is String || EvidencePrivacy.identifier(value) != value) return false
        }
        val fields = row.getJSONObject("fields")
        return fields.length() <= 24 && fields.keys().asSequence().all { key ->
            fields.get(key) is String && EvidencePrivacy.field(key, fields.getString(key)) == fields.getString(key)
        }
    }
    fun inspect(lines: List<String>): EvidenceReadReport {
        val reasons = linkedSetOf<String>()
        val rows = mutableListOf<JSONObject>()
        if (lines.size > 4096) reasons += "READER_LIMIT"
        for (line in lines.take(4096)) {
            try {
                if (line.toByteArray(Charsets.UTF_8).size > 4096) { reasons += "READER_LIMIT"; continue }
                val row = JSONObject(line)
                if (row.optString("record_type") == "retention") {
                    if (row.optInt("evicted_segments") > 0) reasons += "EVICTED"
                    if (row.keys().asSequence().toSet() != setOf("record_type", "schema", "evicted_segments", "process_epoch", "first_sequence") ||
                        row.optInt("schema") != 1 || EvidencePrivacy.identifier(row.optString("process_epoch")) == null ||
                        row.optLong("first_sequence") <= 0 || row.optInt("evicted_segments") < 0) reasons += "UNSAFE_SCHEMA"
                    continue
                }
                if (!safeRecord(row)) {
                    reasons += "UNSAFE_SCHEMA"
                    continue
                }
                if (row.getInt("omitted_fields") > 0 || row.getInt("truncated_fields") > 0 ||
                    row.getInt("redacted_fields") > 0) reasons += "OMITTED_OR_TRUNCATED"
                if (row.getLong("dropped_before") > 0) reasons += "DROPPED"
                if (row.optString("event") == "evidence.truncated") reasons += "OMITTED_OR_TRUNCATED"
                // Chỉ copy schema đã kiểm tra; không xuất JSON đầu vào chưa sanitize.
                rows += JSONObject().also { safe -> recordKeys.forEach { safe.put(it, row.get(it)) } }
            } catch (_: Exception) { reasons += "MALFORMED_OR_PARTIAL" }
        }
        if (rows.isEmpty()) reasons += "MISSING_EVIDENCE"
        for (stream in rows.groupBy { it.getString("domain") + "/" + it.getString("process_epoch") }.values) {
            val sequences = stream.map { it.getLong("sequence") }.sorted()
            if (sequences.first() != 1L || sequences.zipWithNext().any { it.second != it.first + 1 }) reasons += "SEQUENCE_GAP"
        }
        fun app(row: JSONObject) = row.getString("domain") == "app"
        fun sameGraph(a: JSONObject, b: JSONObject) = app(a) && app(b) && !a.isNull("graph") &&
            a.optString("graph") == b.optString("graph") && a.getString("process_epoch") == b.getString("process_epoch")
        val products = rows.filter { app(it) && it.optString("event") == "product.operation" }
        val links = linkedMapOf<String, String>()
        val sessionLinks = rows.filter { app(it) && it.optString("event") == "session.link" }
        for (row in sessionLinks) {
            val product = products.singleOrNull { sameGraph(it, row) && it.getLong("sequence") < row.getLong("sequence") }
            if (product == null) { reasons += "MISSING_OR_AMBIGUOUS_LINKAGE"; continue }
            val fields = product.getJSONObject("fields")
            val sid = row.optString("sid")
            if (row.isNull("cell") || fields.optString("product_generation").toLongOrNull() == null ||
                fields.optString("start_operation").toLongOrNull() == null) {
                reasons += "MISSING_OR_AMBIGUOUS_LINKAGE"; continue
            }
            val identity = "${row.getString("process_epoch")}/${row.optString("graph")}/" +
                "${fields.optString("product_generation")}/${fields.optString("start_operation")}/${row.optString("cell")}"
            if (sid.isBlank() || sid == "null" || links.put(sid, identity) != null) reasons += "MISSING_OR_AMBIGUOUS_LINKAGE"
        }
        val sids = rows.mapNotNull { it.optString("sid").takeIf { id -> id.isNotBlank() && id != "null" } }.toSet()
        for (sid in sids) {
            val remote = rows.filter { it.getString("domain") == "remote" && it.optString("sid") == sid }
            val entry = remote.singleOrNull { it.optString("event") == "start.entry" }
            val terminal = remote.singleOrNull { it.optString("event") in setOf("start.failure", "start.success") }
            val link = sessionLinks.singleOrNull { it.optString("sid") == sid }
            if (sid !in links || entry == null || terminal == null ||
                entry.getString("process_epoch") != terminal.getString("process_epoch") ||
                entry.getLong("sequence") >= terminal.getLong("sequence") || link == null ||
                rows.none { sameGraph(it, link) && it.optString("event") == "runner.result" }) {
                reasons += "MISSING_TIMELINE"
            }
        }
        val results = rows.filter { app(it) && it.optString("event") == "runner.result" }
        for (result in results) {
            val timeline = rows.filter { sameGraph(it, result) && it.getLong("sequence") > result.getLong("sequence") }
                .sortedBy { it.getLong("sequence") }.takeWhile { it.optString("event") != "runner.result" }
            val end = timeline.firstOrNull { it.optString("event") == "runner.result.end" }
            if (end == null) { reasons += "MISSING_RESULT_TAIL"; continue }
            val children = timeline.takeWhile { it !== end }.filter { it.optString("event") in setOf("runner.receipt", "runner.outcome") }
            if (end.getJSONObject("fields").optString("count").toIntOrNull() != children.size) reasons += "MISSING_RESULT_TAIL"
            for (child in children) {
                val link = sessionLinks.singleOrNull { sameGraph(it, child) && it.optString("cell") == child.optString("cell") }
                if (link == null || link.isNull("sid") || child.optString("sid") != link.optString("sid")) reasons += "MISSING_OR_AMBIGUOUS_LINKAGE"
            }
            val product = products.singleOrNull { sameGraph(it, result) }
            val projection = timeline.firstOrNull { it.getLong("sequence") > end.getLong("sequence") && it.optString("event") == "product.projection" }
            val acceptance = projection?.let { projected -> timeline.firstOrNull {
                it.getLong("sequence") > projected.getLong("sequence") && it.optString("event") == "product.acceptance"
            } }
            if (product == null || projection == null || acceptance == null ||
                projection.getJSONObject("fields").optString("product_generation") != product.getJSONObject("fields").optString("product_generation") ||
                (projection.getJSONObject("fields").optString("start_operation").isBlank() && projection.getJSONObject("fields").optString("cleanup_operation").isBlank()) ||
                acceptance.getJSONObject("fields").optString("accepted") !in setOf("true", "false")) reasons += "MISSING_PRODUCT_TAIL"
        }
        if (results.isEmpty()) reasons += "MISSING_RESULT_TAIL"
        if (sids.isEmpty()) reasons += "MISSING_TIMELINE"
        val failures = if (reasons.isEmpty()) rows.filter { it.optString("event").endsWith("failure") }.map { it.toString() } else emptyList()
        return EvidenceReadReport(if (reasons.isEmpty()) "OBSERVED" else "NOT_VERIFIED", reasons.toList(), links, failures)
    }

    /** Entrypoint CLI offline; runtime/gate không gọi. */
    @JvmStatic fun main(args: Array<String>) {
        require(args.isNotEmpty() && args.size <= 8)
        val files = args.map(::File)
        require(files.sumOf { it.length() } <= 1024 * 1024)
        val report = inspect(files.flatMap { it.readLines(Charsets.UTF_8) })
        println(JSONObject().put("verification", report.verification).put("authority", "NOT_VERIFIED")
            .put("reasons", report.reasons).put("linked_sids", report.linkedSids)
            .put("observed_failures", report.observedFailures).toString(2))
    }
}
