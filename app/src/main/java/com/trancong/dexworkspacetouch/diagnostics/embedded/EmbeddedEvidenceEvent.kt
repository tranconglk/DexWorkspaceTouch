package com.trancong.dexworkspacetouch.diagnostics.embedded

import org.json.JSONObject
import java.util.Collections

/** Bản sao giá trị chẩn đoán; không chứng minh ownership hoặc cleanup. */
class EmbeddedEvidenceEvent internal constructor(
    val domain: String, val processEpoch: String, val pid: Int, val uid: Int,
    val sequence: Long, val utcMillis: Long, val monotonicNanos: Long,
    val event: String, val sid: String?, val cell: String?, val graph: String?,
    fields: Map<String, String>, val omittedFields: Int, val truncatedFields: Int,
    val redactedFields: Int, val droppedBefore: Long,
) {
    val fields: Map<String, String> = Collections.unmodifiableMap(LinkedHashMap(fields))
    fun toJson(): String = JSONObject().put("schema", 1).put("provenance", "LOCAL_OBSERVATION")
        .put("domain", domain).put("process_epoch", processEpoch).put("pid", pid).put("uid", uid)
        .put("sequence", sequence).put("utc_ms", utcMillis).put("monotonic_ns", monotonicNanos)
        .put("event", event).put("sid", sid ?: JSONObject.NULL).put("cell", cell ?: JSONObject.NULL)
        .put("graph", graph ?: JSONObject.NULL).put("fields", JSONObject(fields))
        .put("omitted_fields", omittedFields).put("truncated_fields", truncatedFields)
        .put("redacted_fields", redactedFields).put("dropped_before", droppedBefore).toString()
}

data class EvidenceLimits(val queueRecords: Int = 64, val recordBytes: Int = 4096,
    val segmentBytes: Int = 128 * 1024, val segments: Int = 4) {
    init { require(queueRecords in 1..64 && recordBytes in 1024..4096 &&
        segmentBytes in recordBytes..128 * 1024 && segments in 1..4) }
}

internal object EvidencePrivacy {
    private val id = Regex("[a-zA-Z0-9_.:-]{1,96}")
    private val types = Regex("(?:java|javax|kotlin|android|com\\.trancong\\.dexworkspacetouch)\\.[a-zA-Z0-9_.$]{1,180}")
    private val keys = setOf("stage", "step", "state", "phase", "success", "admitted", "disposition",
        "ipc_operation", "service_generation", "service_operation", "product_generation", "start_operation",
        "cleanup_operation", "token_identity", "host_identity", "surface_identity", "surface_valid",
        "surface_read_error", "exception_type", "exception_code", "failure_code", "message", "message_reason", "raw_message_chars", "remote_uid",
        "association_id", "device_id", "display_id", "input_device_id", "task_id", "removed_task_id",
        "attempted", "returned", "id_observed", "device_returned", "input_returned", "association_returned",
        "input_phase", "live_resources_flag", "launch_result", "exit_code", "has_section", "has_finger",
        "equals_previous", "equal_before", "remaining_ms", "sample_index", "section_length", "order",
        "role", "kind", "all_owned_sessions_clean", "authoritative_clean", "allocation_evidence",
        "cleanup_evidence", "issue_code", "accepted", "count", "result_kind", "observed_before_reset")
    private val messages = setOf("Received Surface became invalid", "Received Surface is invalid",
        "Surface is not valid", "Execution surface is no longer valid",
        "VDM input configuration did not settle to touchscreen=finger", "Display creation failed",
        "Touchscreen creation failed", "Target launch failed", "VDM cleanup failed", "Session start failed",
        "START_IPC_FAILED", "Start failed", "Remote cleanup failed", "Cleanup failed",
        "Timed out waiting for READY", "Timed out waiting for ACTIVE", "Execution surface was lost",
        "createVirtualTouchscreen returned null IVirtualInputDevice", "Requires shell UID 2000",
        "Gate A display is not active", "Gate A VirtualDevice is not active")
    private val failureCodes = setOf("START_FAILED", "START_IPC_FAILED", "CREATE_FAILED", "CONNECT_FAILED",
        "START_COMMAND_FAILED", "SESSION_FAILED", "SESSION_STOPPED", "READY_TIMEOUT", "ACTIVE_TIMEOUT",
        "CLEANUP_TIMEOUT", "CLEANUP_FAILED", "CLEANUP_INCOMPLETE", "REMOTE_DIED", "REMOTE_UNAVAILABLE",
        "SURFACE_LOST", "SURFACE_INVALID", "UNKNOWN_SESSION", "STOP_FAILED", "RECOVERY_REQUIRED",
        "START_REJECTED", "START_CAPACITY_EXHAUSTED", "DUPLICATE_OR_INVALID_SESSION")
    fun identifier(value: String?): String? = value?.takeIf { id.matches(it) }
    fun field(key: String, value: String): String? = when {
        key !in keys -> null
        key == "message" -> value.takeIf { it in messages || Regex("Invalid (?:displayId|InputDevice ID)=-?\\d+").matches(it) }
        key == "exception_type" -> value.takeIf { types.matches(it) }
        key == "failure_code" -> value.takeIf { it in failureCodes }
        else -> value.takeIf { it.length <= 128 && Regex("[a-zA-Z0-9_.:+/=-]{1,128}").matches(it) }
    }
}
