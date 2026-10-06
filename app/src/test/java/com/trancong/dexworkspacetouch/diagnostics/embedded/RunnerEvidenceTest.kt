package com.trancong.dexworkspacetouch.diagnostics.embedded

import com.trancong.dexworkspacetouch.feature.embeddedapp.*
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.product.*
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.execution.embedded.*
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RunnerEvidenceTest {
    @Test fun rollbackExceptionsAndPartialReceiptSurviveBeforeProjection() = runTest {
        val recorder = EvidenceRecorder("app", 12, 10001, "app")
        val run = replay(recorder, "Received Surface became invalid")
        assertEquals(1, run.stops); assertEquals(1, run.closes)
        val failures = recorder.drain()
        assertEquals("Both ignored rollback exceptions must be observed", 2,
            failures.count { it.event == "runner.rollback.failure" })
        assertTrue(failures.any { it.event == "runner.receipt" && it.fields["role"] == "partial_receipt" && it.sid == "012-runner" })
        assertTrue(failures.any { it.event == "runner.outcome" && it.fields["kind"] == "Incomplete" })
    }
    @Test fun distinctCausesKeepEqualProductEvidenceAndDistinctDiagnosticEvidence() = runTest {
        val first = EvidenceRecorder("app", 12, 10001, "app")
        val a = replay(first, "Received Surface became invalid")
        val second = EvidenceRecorder("app", 12, 10001, "app")
        val b = replay(second, "VDM input configuration did not settle to touchscreen=finger")
        assertEquals(a.status, b.status)
        val eventsA = first.drain().filter { it.event == "runner.result" }
        val eventsB = second.drain().filter { it.event == "runner.result" }
        assertEquals("Runner evidence absent", 1, eventsA.size); assertEquals(1, eventsB.size)
        assertNotEquals(eventsA.single().fields["message"], eventsB.single().fields["message"])
    }
    @Test fun disabledFullAndThrowingSinkLeaveEveryResultValueGateAndCleanupCountUnchanged() = runTest {
        val enabled = EvidenceRecorder("app", 12, 10001, "app")
        val baseline = replay(null, "Received Surface became invalid")
        val full = EvidenceRecorder("app", 12, 10001, "app", EvidenceLimits(queueRecords = 1))
        full.emit("occupied")
        val throwing = object : EvidenceRecorder("app", 12, 10001, "app") {
            override fun emit(event: String, sid: String?, cell: String?, graph: String?, fields: Map<String, String>) {
                throw IllegalStateException("controlled writer error")
            }
        }
        for (sink in listOf(enabled, full, throwing)) {
            val actual = replay(sink, "Received Surface became invalid")
            assertEquals(baseline.result, actual.result)
            assertEquals(baseline.value, actual.value)
            assertEquals(baseline.status, actual.status)
            assertEquals(baseline.stops, actual.stops); assertEquals(baseline.closes, actual.closes)
        }
        assertTrue("Enabled sink must actually receive evidence", enabled.drain().any { it.event == "runner.result" })
    }

    private suspend fun TestScope.replay(sink: EvidenceRecorder?, cause: String): Run {
        EmbeddedEvidence.installApp(sink)
        var stops = 0; var closes = 0
        val factory = object : EmbeddedWorkspaceSessionFactory {
            override fun create(target: EmbeddedAppTarget, observer: (EmbeddedSessionSnapshot) -> Unit): EmbeddedWorkspaceSessionHandle {
                val lifecycle = EmbeddedAppLifecycleCoordinator()
                return object : EmbeddedWorkspaceSessionHandle {
                    override val sessionId = EmbeddedAppSessionId("012-runner")
                    override fun connect() { lifecycle.beginConnect(); lifecycle.serviceReady(); observer(lifecycle.snapshot) }
                    override fun start(surface: EmbeddedWorkspaceExecutionSurface) {
                        lifecycle.beginStart(); lifecycle.startFailed(EmbeddedSessionFailure("START_FAILED", cause)); observer(lifecycle.snapshot)
                    }
                    override fun sendTouch(event: EmbeddedTouchEvent) = false
                    override fun stop() { stops++; assertFalse(lifecycle.requestStop()); throw IllegalArgumentException("VDM cleanup failed") }
                    override fun close() { closes++; assertFalse(lifecycle.requestClose()); observer(lifecycle.snapshot); throw IllegalStateException("Remote cleanup failed") }
                }
            }
        }
        val plan = EmbeddedWorkspacePlan("012", "012", listOf(EmbeddedWorkspacePlanItem("cell", "calc", "calc.Main",
            NormalizedBounds(0f, 0f, 1f, 1f), 0)))
        val runner = EmbeddedWorkspaceRunner(EmbeddedWorkspacePreflight { EmbeddedAppGeometry(900, 675, 320) },
            factory, PROOF_EMBEDDED_WORKSPACE_TIMEOUTS, this, StandardTestDispatcher(testScheduler))
        val gate = EmbeddedProductRunGate()
        val op = gate.startOperation(gate.tryAcquireEmbedded("012")!!)!!
        gate.markInvoked(op)
        try {
            val result = runner.start(EmbeddedWorkspaceExecutionRequest(plan, listOf(EmbeddedWorkspaceHostSlot("cell",
                object : EmbeddedWorkspaceExecutionSurface { override val isValid = true }))))
            val value = ProductExecutionValue.from(result)
            assertTrue(gate.acceptResult(op, value))
            assertEquals(ProductRunPhase.CLEANUP_BLOCKED, gate.status.value.phase)
            assertNull(gate.tryAcquireEmbedded("012"))
            return Run(result, listOf(value.kind, value.issue, value.allocationEvidence, value.cleanupEvidence,
                value.items, value.cleanupOutcomes, value.authoritativeClean), gate.status.value.copy(token = null), stops, closes)
        } finally { runner.stop(); EmbeddedEvidence.installApp(null) }
    }
    private data class Run(val result: EmbeddedWorkspaceRunResult, val value: List<Any?>,
        val status: ProductRunStatus, val stops: Int, val closes: Int)
}
