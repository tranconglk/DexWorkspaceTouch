package com.trancong.dexworkspacetouch.diagnostics.embedded

import com.trancong.dexworkspacetouch.diagnostics.embedded.offline.EvidenceReader
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
class ProductCorrelationEvidenceTest {
    @Test fun realProductControllerAndRunnerLinkOperationCellAndInjectedRemoteTimeline() = runTest {
        val app = EvidenceRecorder("app", 11, 10001, "app-epoch")
        val remote = EvidenceRecorder("remote", 22, 2000, "remote-epoch")
        EmbeddedEvidence.installApp(app); EmbeddedEvidence.installRemote(remote)
        val sid = "012-correlated"
        val factory = object : EmbeddedWorkspaceSessionFactory {
            override fun create(target: EmbeddedAppTarget, observer: (EmbeddedSessionSnapshot) -> Unit): EmbeddedWorkspaceSessionHandle {
                val lifecycle = EmbeddedAppLifecycleCoordinator()
                return object : EmbeddedWorkspaceSessionHandle {
                    override val sessionId = EmbeddedAppSessionId(sid)
                    override fun connect() { lifecycle.beginConnect(); lifecycle.serviceReady(); observer(lifecycle.snapshot) }
                    override fun start(surface: EmbeddedWorkspaceExecutionSurface) {
                        lifecycle.beginStart()
                        EmbeddedEvidence.remote("start.entry", sid)
                        val error = runCatching { EmbeddedEvidence.remoteStep(sid, "input_stability") {
                            throw IllegalStateException("VDM input configuration did not settle to touchscreen=finger")
                        } }.exceptionOrNull()!!
                        EmbeddedEvidence.remote("start.failure", sid, EmbeddedEvidence.errorFields(error) + ("stage" to "input_stability"))
                        lifecycle.startFailed(EmbeddedSessionFailure("START_FAILED", error.message)); observer(lifecycle.snapshot)
                    }
                    override fun sendTouch(event: EmbeddedTouchEvent) = false
                    override fun stop() { lifecycle.requestStop() }
                    override fun close() { lifecycle.requestClose(); observer(lifecycle.snapshot) }
                }
            }
        }
        val plan = EmbeddedWorkspacePlan("012", "012", listOf(EmbeddedWorkspacePlanItem("cell-a", "calc", "calc.Main",
            NormalizedBounds(0f, 0f, 1f, 1f), 0)))
        val graph = "012-graph"
        val runner = EmbeddedWorkspaceRunner(EmbeddedWorkspacePreflight { EmbeddedAppGeometry(100, 100, 160) }, factory,
            PROOF_EMBEDDED_WORKSPACE_TIMEOUTS, this, StandardTestDispatcher(testScheduler), graph)
        val gate = EmbeddedProductRunGate()
        val controller = EmbeddedWorkspaceProductController("012", gate,
            EmbeddedCapabilityProbe { EmbeddedCapabilitySnapshot(true, true, true) }, { ProductHostReadiness(true, true) },
            object : EmbeddedProductExecution {
                override suspend fun start() = runner.start(EmbeddedWorkspaceExecutionRequest(plan, listOf(EmbeddedWorkspaceHostSlot("cell-a",
                    object : EmbeddedWorkspaceExecutionSurface { override val isValid = true }))))
                override suspend fun close() = runner.stop()
            }, backgroundScope, graph)
        try {
            assertTrue(controller.start() is ProductStartOutcome.RunResult)
            val operation = controller.startOperation!!
            val appEvents = app.drain()
            val opEvent = appEvents.single { it.event == "product.operation" }
            assertEquals(System.identityHashCode(operation.token).toString(), opEvent.fields["token_identity"])
            assertEquals(operation.generation.toString(), opEvent.fields["product_generation"])
            assertEquals(operation.operationId.toString(), opEvent.fields["start_operation"])
            assertTrue(appEvents.any { it.event == "product.acceptance" && it.fields["accepted"] == "true" })
            val remoteEvents = remote.drain()
            val report = EvidenceReader.inspect((appEvents + remoteEvents).map { it.toJson() })
            assertEquals("app-epoch/012-graph/1/1/cell-a", report.linkedSids[sid])
            assertEquals("OBSERVED", report.verification)
            assertTrue(report.observedFailures.any { it.contains("input_stability") })
            assertEquals(ProductRunPhase.CLEANUP_BLOCKED, gate.status.value.phase)
        } finally { runner.stop(); EmbeddedEvidence.installApp(null); EmbeddedEvidence.installRemote(null) }
    }
}
