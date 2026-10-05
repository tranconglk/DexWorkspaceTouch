package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import com.trancong.dexworkspacetouch.feature.embeddedapp.*
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.*
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.execution.embedded.*
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Category A diagnostic replay only. Injected causes do not identify the historical
 * Android exception, prove remote cleanup, or authorize another device Start.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EmbeddedStartFailureEvidenceQualificationTest {
    @Test fun differentStartCausesRemainDistinctInRunnerButHaveIdenticalProductEvidence() = runTest {
        val inputWait = replay("VDM input configuration did not settle to touchscreen=finger")
        val invalidSurface = replay("Received Surface became invalid")

        assertNotEquals(inputWait.rawFailure, invalidSurface.rawFailure)
        assertEquals("The archived product projection cannot distinguish these causes",
            inputWait.productStatus, invalidSurface.productStatus)
    }

    private suspend fun TestScope.replay(cause: String): Replay {
        val createdPackages = mutableListOf<String>()
        var stops = 0
        var closes = 0
        val factory = object : EmbeddedWorkspaceSessionFactory {
            override fun create(target: EmbeddedAppTarget, observer: (EmbeddedSessionSnapshot) -> Unit):
                EmbeddedWorkspaceSessionHandle {
                createdPackages += target.packageName
                val lifecycle = EmbeddedAppLifecycleCoordinator()
                return object : EmbeddedWorkspaceSessionHandle {
                    override val sessionId = EmbeddedAppSessionId("010-local-start-failure")
                    override fun connect() {
                        assertTrue(lifecycle.beginConnect())
                        lifecycle.serviceReady()
                        observer(lifecycle.snapshot)
                    }
                    override fun start(surface: EmbeddedWorkspaceExecutionSurface) {
                        assertTrue(lifecycle.beginStart())
                        assertTrue(lifecycle.startFailed(EmbeddedSessionFailure("START_FAILED", cause)))
                        observer(lifecycle.snapshot)
                    }
                    override fun sendTouch(event: EmbeddedTouchEvent) = false
                    override fun stop() {
                        stops++
                        assertFalse("FAILED remains terminal", lifecycle.requestStop())
                    }
                    override fun close() {
                        closes++
                        assertFalse(lifecycle.requestClose())
                        // Production close republishes the terminal lifecycle snapshot.
                        observer(lifecycle.snapshot)
                    }
                }
            }
        }
        val plan = EmbeddedWorkspacePlan("010-local", "010-local", listOf(
            EmbeddedWorkspacePlanItem("calc", "qualification.calc", "qualification.calc.Main",
                NormalizedBounds(0f, 0f, 0.5f, 1f), 0),
            EmbeddedWorkspacePlanItem("waze", "qualification.waze", "qualification.waze.Main",
                NormalizedBounds(0.5f, 0f, 1f, 1f), 1),
        ))
        val runner = EmbeddedWorkspaceRunner(
            EmbeddedWorkspacePreflight { EmbeddedAppGeometry(900, 675, 320) },
            factory, PROOF_EMBEDDED_WORKSPACE_TIMEOUTS, this, StandardTestDispatcher(testScheduler),
        )
        val surface = object : EmbeddedWorkspaceExecutionSurface { override val isValid = true }
        val gate = EmbeddedProductRunGate()
        val operation = gate.startOperation(gate.tryAcquireEmbedded("010-local")!!)!!
        assertTrue(gate.markInvoked(operation))
        try {
            val result = runner.start(EmbeddedWorkspaceExecutionRequest(plan,
                listOf("calc", "waze").map { EmbeddedWorkspaceHostSlot(it, surface) }))
                as EmbeddedWorkspaceRunResult.StartFailed

            assertEquals(EmbeddedSessionFailure("START_FAILED", cause), result.failure)
            assertEquals("calc", result.sourceCellId)
            assertTrue(result.receipts.isEmpty())
            assertEquals("010-local-start-failure", result.partialReceipt?.sessionId?.value)
            assertEquals(EmbeddedSessionPhase.FAILED, result.partialReceipt?.phase)
            assertEquals(-1, result.partialReceipt?.displayId)
            assertFalse(result.allOwnedSessionsClean)
            val incomplete = result.rollbackOutcomes.single() as EmbeddedWorkspaceCleanupOutcome.Incomplete
            assertEquals("calc", incomplete.sourceCellId)
            assertEquals(result.failure, incomplete.failure)
            assertEquals(listOf("qualification.calc"), createdPackages)
            assertEquals(1, stops)
            assertEquals(1, closes)

            val value = ProductExecutionValue.from(result)
            assertFalse(value.authoritativeClean)
            assertTrue(gate.acceptResult(operation, value))
            val status = gate.status.value
            assertEquals(ProductRunPhase.CLEANUP_BLOCKED, status.phase)
            assertEquals(ProductResultKind.START_FAILED, status.resultKind)
            assertEquals(CleanupEvidence.INCOMPLETE, status.cleanupEvidence)
            assertTrue(status.issue is EmbeddedProductIssue.RuntimeStartFailed)
            assertEquals(listOf(ProductCleanupStatus("calc", CleanupEvidence.INCOMPLETE, "START_FAILED")),
                status.cleanupOutcomes)
            assertNull(gate.tryAcquireEmbedded("010-local"))
            // Token identity is intentionally unique per replay; all retained values must match.
            return Replay(result.failure, status.copy(token = null))
        } finally {
            runner.stop()
        }
    }

    private data class Replay(val rawFailure: EmbeddedSessionFailure, val productStatus: ProductRunStatus)
}
