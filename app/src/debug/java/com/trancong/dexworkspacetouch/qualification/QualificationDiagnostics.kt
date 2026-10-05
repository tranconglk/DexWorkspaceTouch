package com.trancong.dexworkspacetouch.qualification

import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import com.trancong.dexworkspacetouch.BuildConfig
import com.trancong.dexworkspacetouch.feature.embeddedapp.*
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.product.*
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Read-only qualification observer. Stores values, never host/Surface/session handles. */
object QualificationDiagnostics {
    const val TAG = "DWT-010-DIAG"
    const val HISTORICAL_SID = "0bf0ecec-9e2c-4196-8edb-a076b688edb1"
    private var gate: EmbeddedProductRunGate? = null
    private var runId: String? = null
    private var hostIdentity: Int? = null
    private var hostId: Long? = null
    private val owners = mutableMapOf<String, Map<String, Any?>>()
    private val inputSessions = mutableMapOf<String, String>()
    private val rows = mutableListOf<String>()
    private val uiValues = mutableMapOf<String, JSONObject>()
    private var sequence = 0L
    private var evidenceCategory = "C_NEW_INDEPENDENT_DIAGNOSTIC"
    val overlayAvailable get() = runCatching {
        Class.forName("com.trancong.dexworkspacetouch.feature.embeddedapp.remote.RemoteSessionRuntime")
            .getDeclaredField("qualificationSessionId")
    }.isSuccess

    @Synchronized fun attach(id: String, identity: Int, host: Long, productGate: EmbeddedProductRunGate, category: String = "C_NEW_INDEPENDENT_DIAGNOSTIC") {
        require(BuildConfig.DEBUG && BuildConfig.APPLICATION_ID == VDM010_HARNESS_PACKAGE)
        require(id.isNotBlank())
        require(runId == null || runId == id)
        evidenceCategory = category
        if (hostIdentity != identity) uiValues.clear()
        runId = id; hostIdentity = identity; hostId = host; gate = productGate
    }
    private fun context(): Map<String, Any?> {
        val s = gate?.status?.value
        return mapOf("independent_run_id" to runId, "app_pid" to if (gate != null) Process.myPid() else null,
            "host_identity" to hostIdentity, "host_id" to hostId,
            "product_token_identity" to s?.token?.let(System::identityHashCode), "generation" to s?.generation,
            "start_operation_id" to s?.startOperationId, "cleanup_operation_id" to s?.cleanupOperationId,
            "gate_phase" to s?.phase?.name)
    }
    @Synchronized private fun emit(stage: String, sid: String?, fields: Map<String, Any?> = emptyMap()) {
        runCatching {
            if (!BuildConfig.DEBUG || BuildConfig.APPLICATION_ID != VDM010_HARNESS_PACKAGE) return
            val values = context() + owners[sid].orEmpty() + fields + mapOf(
                "category" to evidenceCategory, "stage" to stage, "session_id" to sid,
                "observer_pid" to Process.myPid(), "timestamp_ms" to System.currentTimeMillis(),
                "elapsed_nanos" to SystemClock.elapsedRealtimeNanos(), "observer_host_identity" to hostIdentity)
            val json = JSONObject().apply { values.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) } }.toString()
            if (gate != null) rows += json
            val record = ++sequence
            val chunks = json.chunked(1800)
            chunks.forEachIndexed { i, part -> Log.i(TAG, "record=$record part=${i + 1}/${chunks.size} $part") }
        }
    }
    @Synchronized fun register(cell: String, sid: String, surface: EmbeddedWorkspaceExecutionSurface) = runCatching {
        require(sid != HISTORICAL_SID)
        owners[sid] = context().filterKeys { it != "gate_phase" && it != "cleanup_operation_id" } + mapOf("source_cell" to cell)
        emit("session_owned", sid, mapOf("execution_surface_identity" to System.identityHashCode(surface),
            "execution_surface_valid" to surface.isValid))
    }
    @Synchronized fun registerInput(sid: String, input: String) { inputSessions[input] = sid }
    fun surface(stage: String, sid: String, surface: Surface?) =
        emit(stage, sid, mapOf("surface_identity" to surface?.let(System::identityHashCode),
            "surface_valid" to surface?.let { runCatching { it.isValid }.getOrNull() }))
    fun launchSurface(input: String, surface: Surface?) = runCatching {
        val sid = synchronized(this) { inputSessions[input] }
        emit("remote_launch_guard", sid, mapOf("input_name" to input,
            "surface_identity" to surface?.let(System::identityHashCode), "surface_valid" to surface?.isValid))
    }
    fun snapshot(cell: String, sid: String, snapshot: EmbeddedSessionSnapshot) =
        emit("runner_snapshot", sid, mapOf("source_cell" to cell, "session_phase" to snapshot.phase.name,
            "display_id" to snapshot.displayId, "failure_code" to snapshot.failure?.code,
            "failure_message" to snapshot.failure?.message))
    fun completion(sid: String, success: Boolean, message: String?, operation: Long) =
        emit("raw_start_completion_before_fence", sid, mapOf("success" to success, "ipc_operation" to operation,
            "failure_code" to if (success) null else "START_FAILED", "failure_message" to message))
    fun remoteFailure(sid: String, stage: String, error: Throwable, surface: Surface) = runCatching {
        surface("remote_failure_surface", sid, surface)
        emit("remote_start_failure", sid, mapOf("failure_stage" to stage, "failure_code" to "START_FAILED",
            "failure_message" to "${error.javaClass.name}: ${error.message}",
            "stack_trace" to error.stackTraceToString()))
    }
    fun inputSample(sid: String, display: Int, section: String, previous: String, equal: Int, deadline: Long) =
        emit("input_stability_sample", sid, mapOf("display_id" to display, "section_length" to section.length,
            "has_finger" to section.contains(" finger "), "equals_previous" to (section == previous),
            "equal_before" to equal, "remaining_ms" to (deadline - SystemClock.uptimeMillis()),
            "section_sha256" to MessageDigest.getInstance("SHA-256").digest(section.toByteArray())
                .joinToString("") { "%02x".format(it) },
            "section" to section.take(32000), "section_truncated" to (section.length > 32000)))
    private fun receipt(r: EmbeddedWorkspaceItemReceipt) = JSONObject()
        .put("source_cell", r.sourceCellId).put("session_id", r.sessionId.value)
        .put("package_name", r.packageName).put("component_name", r.componentName).put("order", r.order)
        .put("phase", r.phase.name).put("display_id", r.displayId)
    private fun outcome(o: EmbeddedWorkspaceCleanupOutcome): JSONObject {
        val failure = when (o) {
            is EmbeddedWorkspaceCleanupOutcome.Clean -> null
            is EmbeddedWorkspaceCleanupOutcome.Incomplete -> o.failure
            is EmbeddedWorkspaceCleanupOutcome.RecoveryRequired -> o.failure
        }
        return JSONObject().put("source_cell", o.sourceCellId).put("kind", o.javaClass.simpleName)
            .put("failure_code", failure?.code ?: JSONObject.NULL)
            .put("failure_message", failure?.message ?: JSONObject.NULL)
    }
    fun result(result: EmbeddedWorkspaceRunResult) = runCatching {
        val receipts = when (result) {
            is EmbeddedWorkspaceRunResult.Started -> result.receipts
            is EmbeddedWorkspaceRunResult.StartFailed -> result.receipts
            is EmbeddedWorkspaceRunResult.Stopped -> result.receipts
            is EmbeddedWorkspaceRunResult.CleanupIncomplete -> result.receipts
            is EmbeddedWorkspaceRunResult.RecoveryRequired -> result.receipts
            else -> emptyList()
        }
        val partial = (result as? EmbeddedWorkspaceRunResult.StartFailed)?.partialReceipt
        val outcomes = when (result) {
            is EmbeddedWorkspaceRunResult.StartFailed -> result.rollbackOutcomes
            is EmbeddedWorkspaceRunResult.Stopped -> result.cleanupOutcomes
            is EmbeddedWorkspaceRunResult.CleanupIncomplete -> result.cleanupOutcomes
            is EmbeddedWorkspaceRunResult.RecoveryRequired -> result.cleanupOutcomes
            else -> emptyList()
        }
        val failure = (result as? EmbeddedWorkspaceRunResult.StartFailed)?.failure
        val sid = partial?.sessionId?.value ?: receipts.firstOrNull()?.sessionId?.value
        emit("runner_result_before_value_mapping", sid, mapOf("result_kind" to result.javaClass.simpleName,
            "failure_code" to failure?.code, "failure_message" to failure?.message,
            "receipts" to JSONArray(receipts.map(::receipt)), "partial_receipt" to partial?.let(::receipt),
            "rollback_or_cleanup_outcomes" to JSONArray(outcomes.map(::outcome)),
            "all_owned_sessions_clean" to (result as? EmbeddedWorkspaceRunResult.StartFailed)?.allOwnedSessionsClean))
    }
    @Synchronized fun eligibility(value: EmbeddedEligibilityResult?) = runCatching {
        val fields = JSONObject().put("eligibility", value?.javaClass?.simpleName ?: "LOADING")
            .put("rejection_reason", (value as? EmbeddedEligibilityResult.Rejected)?.reason?.toString() ?: JSONObject.NULL)
        uiValue("product_root", fields)
    }
    @Synchronized fun readiness(route: String, value: EmbeddedReadinessSnapshot?) = runCatching {
        val capability = value?.capability
        uiValue(route, JSONObject().put("readiness", value?.readiness?.javaClass?.simpleName ?: JSONObject.NULL)
            .put("platform_supported", capability?.platformSupported ?: JSONObject.NULL)
            .put("binder_available", capability?.shizukuBinderAvailable ?: JSONObject.NULL)
            .put("permission_granted", capability?.shizukuPermissionGranted ?: JSONObject.NULL)
            .put("shizuku_launch_available", capability?.shizukuLaunchAvailable ?: JSONObject.NULL)
            .put("permission_denied", value?.permissionDenied ?: JSONObject.NULL))
    }
    private fun uiValue(route: String, fields: JSONObject) {
        if (uiValues[route]?.toString() == fields.toString()) return
        uiValues[route] = fields
        emit("product_ui_value", null, mapOf("route_state" to route, "value" to fields))
    }
    @Synchronized fun snapshotUiValues(): JSONObject = JSONObject(uiValues.mapValues { JSONObject(it.value.toString()) })
    @Synchronized fun snapshotRows(): List<String> = rows.toList()
}
