package com.trancong.dexworkspacetouch.qualification

import android.os.Process
import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.test.platform.app.InstrumentationRegistry
import com.trancong.dexworkspacetouch.feature.embeddedapp.*
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.*
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.product.*
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** B capture plumbing only: no VDM, Binder Start, real host recreation or cleanup claim. */
class EmbeddedDiagnosticCaptureDeviceTest {
    @Test fun preservesRawFailureAndExactOwnedValuesBeforeProductProjection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(VDM010_HARNESS_PACKAGE, context.packageName)
        assertTrue(QualificationDiagnostics.overlayAvailable)
        val gate = EmbeddedProductRunGate()
        val token = gate.tryAcquireEmbedded("capture-selftest")!!
        val op = gate.startOperation(token)!!
        gate.markInvoked(op)
        val sid = "010-controlled-capture"
        val message = "CONTROLLED raw failure; retained before ProductExecutionValue"
        QualificationDiagnostics.attach("vdm010-controlled-capture", 117, 1, gate,
            "B_DIAGNOSTIC_CAPTURE_SELFTEST")
        QualificationDiagnostics.register("calc", sid, object : EmbeddedWorkspaceExecutionSurface {
            override val isValid = true
        })
        val texture = SurfaceTexture(false)
        val surface = Surface(texture)
        try { QualificationDiagnostics.surface("controlled_surface", sid, surface) } finally { surface.release(); texture.release() }
        QualificationDiagnostics.completion(sid, false, message, 1L)
        QualificationDiagnostics.snapshot("calc", sid, EmbeddedSessionSnapshot(EmbeddedSessionPhase.FAILED,
            failure = EmbeddedSessionFailure("START_FAILED", message)))
        val result = EmbeddedWorkspaceRunResult.StartFailed("calc", EmbeddedSessionFailure("START_FAILED", message),
            emptyList(), EmbeddedWorkspaceItemReceipt("calc", EmbeddedAppSessionId(sid), "calc", "calc.Main",
                0, EmbeddedSessionPhase.FAILED, -1),
            listOf(EmbeddedWorkspaceCleanupOutcome.Incomplete("calc", EmbeddedSessionFailure("START_FAILED", message))), false)
        QualificationDiagnostics.result(result)
        val rows = QualificationDiagnostics.snapshotRows().map(::JSONObject)
        assertTrue(rows.all { it.getString("category") == "B_DIAGNOSTIC_CAPTURE_SELFTEST" })
        val raw = rows.single { it.getString("stage") == "raw_start_completion_before_fence" }
        assertEquals(message, raw.getString("failure_message"))
        assertEquals("START_FAILED", raw.getString("failure_code"))
        assertEquals(Process.myPid(), raw.getInt("app_pid"))
        assertEquals(117, raw.getInt("host_identity"))
        assertEquals(System.identityHashCode(token), raw.getInt("product_token_identity"))
        assertEquals(op.generation, raw.getLong("generation"))
        assertEquals(op.operationId, raw.getLong("start_operation_id"))
        assertEquals("calc", raw.getString("source_cell"))
        assertEquals(sid, raw.getString("session_id"))
        assertTrue(raw.getLong("timestamp_ms") > 0)
        val terminal = rows.single { it.getString("stage") == "runner_result_before_value_mapping" }
        assertEquals(sid, terminal.getJSONObject("partial_receipt").getString("session_id"))
        assertEquals("FAILED", terminal.getJSONObject("partial_receipt").getString("phase"))
        assertEquals("Incomplete", terminal.getJSONArray("rollback_or_cleanup_outcomes").getJSONObject(0).getString("kind"))
        assertEquals(message, terminal.getString("failure_message"))
        assertTrue(gate.acceptResult(op, ProductExecutionValue.from(result)))
        assertEquals(ProductRunPhase.CLEANUP_BLOCKED, gate.status.value.phase)
        QualificationDiagnostics.readiness("controlled-fixture", EmbeddedReadinessSnapshot(
            EmbeddedCapabilitySnapshot(true, true, false), EmbeddedReadinessResult.ShizukuPermissionMissing))
        val readiness = QualificationDiagnostics.snapshotUiValues().getJSONObject("controlled-fixture")
        assertEquals("ShizukuPermissionMissing", readiness.getString("readiness"))
        assertFalse(readiness.getBoolean("permission_granted"))
        val node = android.view.accessibility.AccessibilityNodeInfo.obtain().apply {
            text = "Ki\u1ec3m tra l\u1ea1i"; contentDescription = "controlled capture"; isEnabled = false
            isClickable = true; isVisibleToUser = true; className = "android.widget.Button"
            setBoundsInScreen(android.graphics.Rect(1, 2, 101, 52))
        }
        val described = QualificationPreStartCapture.describeNode(node)
        assertEquals("Ki\u1ec3m tra l\u1ea1i", described.getString("text"))
        assertEquals("controlled capture", described.getString("content_description"))
        assertFalse(described.getBoolean("enabled")); assertTrue(described.getBoolean("clickable"))
        assertTrue(described.getBoolean("visible_to_user"))
        assertEquals("[1,2,101,52]", described.getJSONArray("bounds").toString())
        assertEquals("android.widget.Button", described.getString("class"))
        // Synthetic node has no sealed window/connection. Preserve that limitation explicitly.
        assertTrue(described.getJSONArray("parent_query_errors").getString(0).contains("not sealed"))
        context.getExternalFilesDir("dwt-vdm-010-capture")!!.resolve("controlled-prestart-values.json")
            .writeText(JSONObject().put("category", "B_CONTROLLED_NODE_AND_VALUE_CAPTURE")
                .put("node", described).put("readiness", readiness).toString(2))
        context.getExternalFilesDir("dwt-vdm-010-capture")!!.resolve("controlled-capture.jsonl")
            .writeText(QualificationDiagnostics.snapshotRows().joinToString("\n"))
    }
}
