package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunResult
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceCleanupOutcome
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceItemReceipt
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspacePreflightRejection
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppSessionId
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionPhase
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

class EmbeddedProductRunGateTest {
    @Test fun lateStartedAfterStopIntentCannotBecomeActive() {
        val gate = EmbeddedProductRunGate()
        val token = acquire(gate, "ws")
        gate.markStopping(token)
        accept(gate, token, EmbeddedWorkspaceRunResult.Started(emptyList()))
        assertEquals(ProductRunPhase.STOPPING, gate.state.value)
    }

    @Test fun lateStartedAfterBlockedTerminalCannotChangeEvidenceOrPhase() {
        val gate = EmbeddedProductRunGate()
        val token = acquire(gate, "ws")
        accept(gate, token, EmbeddedWorkspaceRunResult.CleanupIncomplete(emptyList(), emptyList()))
        val blocked = gate.status.value
        accept(gate, token, EmbeddedWorkspaceRunResult.Started(emptyList()))
        assertEquals(blocked, gate.status.value)
        assertEquals(ProductRunPhase.CLEANUP_BLOCKED, gate.state.value)
        assertFalse(gate.tryDispatchClassic { error("must not dispatch") })
    }

    @Test fun startedAndCleanStopFollowProductPhases() {
        val gate = EmbeddedProductRunGate()
        val token = acquire(gate, "ws")
        assertEquals(ProductRunPhase.STARTING, gate.state.value)
        accept(gate, token, EmbeddedWorkspaceRunResult.Started(emptyList()))
        assertEquals(ProductRunPhase.ACTIVE, gate.state.value)
        assertFalse(gate.tryDispatchClassic { error("must not dispatch") })
        val cleanup = gate.markStopping(token)!!
        assertEquals(ProductRunPhase.STOPPING, gate.state.value)
        accept(gate, cleanup, EmbeddedWorkspaceRunResult.Stopped(emptyList(), emptyList()))
        assertEquals(ProductRunPhase.IDLE, gate.state.value)
        assertNull(gate.status.value.issue)
        var dispatched = false
        assertTrue(gate.tryDispatchClassic { dispatched = true })
        assertTrue(dispatched)
    }

    @Test fun incompleteCleanupBlocksBothModesAndStaleCallback() {
        val gate = EmbeddedProductRunGate()
        val old = acquire(gate, "old")
        accept(gate, old, EmbeddedWorkspaceRunResult.PreflightRejected(
            com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspacePreflightRejection.InvalidPlan("x")))
        val current = acquire(gate, "current")
        accept(gate, old, EmbeddedWorkspaceRunResult.Started(emptyList()))
        assertEquals(ProductRunPhase.STARTING, gate.state.value)
        accept(gate, current, EmbeddedWorkspaceRunResult.CleanupIncomplete(emptyList(), emptyList()))
        assertEquals(ProductRunPhase.CLEANUP_BLOCKED, gate.state.value)
        assertNull(gate.tryAcquireEmbedded("next"))
        assertFalse(gate.tryDispatchClassic { error("must not dispatch") })
    }

    @Test fun classicDispatchAndEmbeddedAcquireCannotOverlap() {
        val gate = EmbeddedProductRunGate()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val worker = thread {
            assertTrue(gate.tryDispatchClassic {
                entered.countDown()
                release.await(5, TimeUnit.SECONDS)
            })
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        assertNull(gate.tryAcquireEmbedded("ws"))
        release.countDown()
        worker.join(5000)
        assertFalse(worker.isAlive)
        assertNotNull(gate.tryAcquireEmbedded("ws"))
    }

    @Test fun staleTokenGenerationAndStartOperationAreRejectedWithoutChangingStatus() {
        val gate = EmbeddedProductRunGate()
        val start = acquire(gate, "ws")
        val before = gate.status.value
        val started = ProductExecutionValue.from(EmbeddedWorkspaceRunResult.Started(listOf(receipt())))
        for (stale in listOf(start.copy(token = RunToken("ws")),
            start.copy(generation = start.generation - 1), start.copy(operationId = start.operationId + 1),
            start.copy(kind = ProductOperationKind.CLEANUP))) {
            assertFalse(gate.acceptResult(stale, started))
            assertEquals(before, gate.status.value)
        }
        assertTrue(gate.acceptResult(start, started))
        assertEquals(ProductRunPhase.ACTIVE, gate.state.value)
    }

    @Test fun staleCleanupCannotReleaseNewerRunEvenWithSameWorkspaceId() {
        val gate = EmbeddedProductRunGate()
        val old = acquire(gate, "ws")
        val oldCleanup = gate.markStopping(old)!!
        assertTrue(accept(gate, oldCleanup, EmbeddedWorkspaceRunResult.Stopped(emptyList(), emptyList())))
        val newer = acquire(gate, "ws")
        assertTrue(newer.generation > old.generation)
        assertTrue(newer.operationId > oldCleanup.operationId)
        val cleanup = gate.markStopping(newer)!!
        val before = gate.status.value
        val clean = ProductExecutionValue.from(EmbeddedWorkspaceRunResult.Stopped(emptyList(), emptyList()))
        for (stale in listOf(oldCleanup, cleanup.copy(generation = old.generation),
            cleanup.copy(operationId = oldCleanup.operationId), cleanup.copy(token = old.token))) {
            assertFalse(gate.acceptResult(stale, clean))
            assertEquals(before, gate.status.value)
        }
        assertTrue(gate.acceptResult(cleanup, clean))
        assertEquals(ProductRunPhase.IDLE, gate.state.value)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun statusCollectorStartingNewRunCannotBeOverwrittenByPreviousPhasePublication() = runTest {
        val gate = EmbeddedProductRunGate()
        val old = acquire(gate, "old")
        var newer: RunToken? = null
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            gate.status.collect { status ->
                if (status.phase == ProductRunPhase.IDLE && status.generation == old.generation) {
                    newer = gate.tryAcquireEmbedded("newer")
                }
            }
        }
        val cleanup = gate.markStopping(old)!!
        accept(gate, cleanup, EmbeddedWorkspaceRunResult.Stopped(emptyList(), emptyList()))
        assertNotNull(newer)
        assertEquals(old.generation + 1, gate.status.value.generation)
        assertEquals(ProductRunPhase.STARTING, gate.state.value)
        assertEquals(ProductRunPhase.STARTING, gate.status.value.phase)
        assertFalse(gate.tryDispatchClassic { error("new run owns gate") })
    }

    @Test fun stopIntentHasOneOperationAndRejectsBothStartedAndOldStartTerminal() {
        val gate = EmbeddedProductRunGate()
        val start = acquire(gate, "ws")
        val cleanup = gate.markStopping(start)!!
        assertEquals(cleanup, gate.markStopping(start))
        assertTrue(cleanup.operationId > start.operationId)
        assertTrue(gate.status.value.stopRequested)
        val before = gate.status.value
        assertFalse(accept(gate, start, EmbeddedWorkspaceRunResult.Started(emptyList())))
        assertFalse(accept(gate, start, EmbeddedWorkspaceRunResult.Stopped(emptyList(), emptyList())))
        assertFalse(accept(gate, cleanup, EmbeddedWorkspaceRunResult.Started(emptyList())))
        assertEquals(before, gate.status.value)
    }

    @Test fun everyNonIdlePhaseBlocksClassicAndAnotherEmbeddedRun() {
        for (phase in listOf(ProductRunPhase.STARTING, ProductRunPhase.ACTIVE,
            ProductRunPhase.STOPPING, ProductRunPhase.CLEANUP_BLOCKED)) {
            val gate = EmbeddedProductRunGate()
            val start = acquire(gate, "ws")
            when (phase) {
                ProductRunPhase.ACTIVE -> accept(gate, start, EmbeddedWorkspaceRunResult.Started(listOf(receipt())))
                ProductRunPhase.STOPPING -> gate.markStopping(start)
                ProductRunPhase.CLEANUP_BLOCKED -> gate.markUncertain(start)
                else -> Unit
            }
            assertEquals(phase, gate.state.value)
            assertFalse(gate.canEnterEmbedded())
            assertNull(gate.tryAcquireEmbedded("conflict"))
            assertFalse(gate.tryDispatchClassic { error("must not dispatch") })
        }
    }

    @Test fun preRunnerRejectionReleasesOnlyWithExactAuthoritativeNoInvocationProof() {
        val gate = EmbeddedProductRunGate()
        val token = gate.tryAcquireEmbedded("ws")!!
        val start = gate.startOperation(token)!!
        assertEquals(ProductInvocationCategory.NOT_INVOKED, gate.status.value.invocationCategory)
        assertFalse(gate.releaseWithoutAllocation(ProductNoAllocationProof(start)))
        val proof = gate.preRunnerRejection(start)!!
        assertEquals(ProductInvocationCategory.PRE_RUNNER_REJECTED, gate.status.value.invocationCategory)
        assertFalse(gate.markInvoked(start))
        assertTrue(gate.releaseWithoutAllocation(proof))
        val next = acquire(gate, "next")
        assertFalse(gate.releaseWithoutAllocation(proof))
        assertEquals(ProductRunPhase.STARTING, gate.state.value)
        assertNull(gate.preRunnerRejection(next))
        assertFalse(gate.releaseWithoutAllocation(ProductNoAllocationProof(next)))
    }

    @Test fun exactPreflightRejectionUsesResultHandlingAfterInvocation() {
        val gate = EmbeddedProductRunGate()
        val start = acquire(gate, "ws")
        assertTrue(accept(gate, start, EmbeddedWorkspaceRunResult.PreflightRejected(
            EmbeddedWorkspacePreflightRejection.InvalidPlan("invalid"))))
        assertEquals(ProductRunPhase.IDLE, gate.state.value)
        assertEquals(ProductInvocationCategory.RESULT_OR_UNCERTAIN, gate.status.value.invocationCategory)
        assertEquals(AllocationEvidence.NONE_CONFIRMED, gate.status.value.allocationEvidence)
        assertEquals(CleanupEvidence.NOT_NEEDED, gate.status.value.cleanupEvidence)
    }

    @Test fun invokedMissingResultNeverAuthorizesReleaseOrLaterCleanCallback() {
        val gate = EmbeddedProductRunGate()
        val start = acquire(gate, "ws")
        assertTrue(gate.markUncertain(start))
        val blocked = gate.status.value
        assertEquals(ProductInvocationCategory.RESULT_OR_UNCERTAIN, blocked.invocationCategory)
        assertEquals(CleanupEvidence.UNCERTAIN, blocked.cleanupEvidence)
        assertFalse(gate.releaseWithoutAllocation(ProductNoAllocationProof(start)))
        assertFalse(accept(gate, start, EmbeddedWorkspaceRunResult.PreflightRejected(
            EmbeddedWorkspacePreflightRejection.InvalidPlan("late"))))
        assertFalse(accept(gate, start, EmbeddedWorkspaceRunResult.Stopped(emptyList(), emptyList())))
        assertEquals(blocked, gate.status.value)
    }

    @Test fun missingStartResultKeepsAdmissionClosedUntilExactInitialCleanup() {
        val gate = EmbeddedProductRunGate()
        val start = acquire(gate, "ws")
        assertTrue(gate.recordMissingStartResult(start))
        assertEquals(ProductRunPhase.STARTING, gate.state.value)
        assertEquals(ProductInvocationCategory.RESULT_OR_UNCERTAIN, gate.status.value.invocationCategory)
        assertEquals(AllocationEvidence.UNKNOWN, gate.status.value.allocationEvidence)
        assertEquals(CleanupEvidence.UNCERTAIN, gate.status.value.cleanupEvidence)
        assertFalse(gate.releaseWithoutAllocation(ProductNoAllocationProof(start)))
        assertFalse(gate.tryDispatchClassic { error("must not dispatch") })
        assertNull(gate.tryAcquireEmbedded("next"))
        val cleanup = gate.markStopping(start)!!
        assertEquals(cleanup, gate.markStopping(start))
        accept(gate, cleanup, EmbeddedWorkspaceRunResult.CleanupIncomplete(emptyList(), emptyList()))
        assertEquals(ProductRunPhase.CLEANUP_BLOCKED, gate.state.value)
    }

    @Test fun copiedStatusCannotBeMutatedThroughRuntimeListsAndHasOnlyImmutableValues() {
        val gate = EmbeddedProductRunGate()
        val start = acquire(gate, "ws")
        val receipts = mutableListOf(receipt())
        accept(gate, start, EmbeddedWorkspaceRunResult.Started(receipts))
        val active = gate.status.value
        receipts.clear()
        assertEquals(1, active.items.size)
        val cleanup = gate.markStopping(start)!!
        val outcomes = mutableListOf<EmbeddedWorkspaceCleanupOutcome>(EmbeddedWorkspaceCleanupOutcome.Incomplete(
            "cell", EmbeddedSessionFailure("CLEANUP_TIMEOUT", "transport detail")))
        accept(gate, cleanup, EmbeddedWorkspaceRunResult.CleanupIncomplete(listOf(receipt()), outcomes))
        val blocked = gate.status.value
        outcomes.clear()
        assertEquals("CLEANUP_TIMEOUT", blocked.cleanupOutcomes.single().failureCode)
        assertEquals(CleanupEvidence.INCOMPLETE, blocked.cleanupEvidence)
        try { (blocked.items as MutableList).clear(); error("status list must be immutable") }
        catch (_: UnsupportedOperationException) { }
        assertValueGraph(active)
        assertValueGraph(blocked)
    }

    @Test fun missingDuplicateOrWrongOwnedCleanupEvidenceCannotReleaseGate() {
        val malformed = listOf(emptyList(),
            listOf(EmbeddedWorkspaceCleanupOutcome.Clean("other")),
            listOf(EmbeddedWorkspaceCleanupOutcome.Clean("cell"), EmbeddedWorkspaceCleanupOutcome.Clean("cell")))
        for (outcomes in malformed) {
            val gate = EmbeddedProductRunGate()
            val start = acquire(gate, "ws")
            accept(gate, start, EmbeddedWorkspaceRunResult.Started(listOf(receipt())))
            val cleanup = gate.markStopping(start)!!
            accept(gate, cleanup, EmbeddedWorkspaceRunResult.Stopped(listOf(receipt()), outcomes))
            assertEquals(ProductRunPhase.CLEANUP_BLOCKED, gate.state.value)
        }
    }

    private fun acquire(gate: EmbeddedProductRunGate, workspaceId: String): ProductRunOperation =
        gate.startOperation(gate.tryAcquireEmbedded(workspaceId)!!)!!.also { assertTrue(gate.markInvoked(it)) }

    private fun accept(gate: EmbeddedProductRunGate, operation: ProductRunOperation,
        result: EmbeddedWorkspaceRunResult): Boolean = gate.acceptResult(operation, ProductExecutionValue.from(result))

    private fun receipt() = EmbeddedWorkspaceItemReceipt("cell", EmbeddedAppSessionId("session"),
        "pkg", "pkg.Main", 0, EmbeddedSessionPhase.ACTIVE, 10)
}

internal fun assertValueGraph(root: Any) {
    val seen = java.util.IdentityHashMap<Any, Boolean>()
    fun visit(value: Any?) {
        if (value == null || value is String || value is Number || value is Boolean || value is Enum<*>) return
        if (seen.put(value, true) != null) return
        if (value is Collection<*>) { value.forEach { visit(it) }; return }
        val type = value.javaClass
        assertTrue("Non-value object in Application status: ${type.name}",
            type.name.startsWith("com.trancong.dexworkspacetouch.feature.embeddedworkspace.product."))
        for (field in type.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }) {
            assertTrue("Mutable field: ${type.name}.${field.name}", java.lang.reflect.Modifier.isFinal(field.modifiers))
            field.isAccessible = true
            visit(field.get(value))
        }
    }
    visit(root)
}

// Fixture compatibility cho các test policy/routing cũ: mô phỏng invocation trước result.
// Chỉ nằm ở test source; production không có đường nhận callback chỉ bằng token.
internal fun EmbeddedProductRunGate.acceptResult(token: RunToken, result: EmbeddedWorkspaceRunResult): Boolean {
    val operation = startOperation(token) ?: return false
    markInvoked(operation)
    return acceptResult(operation, ProductExecutionValue.from(result))
}

internal fun EmbeddedProductRunGate.markStopping(token: RunToken): ProductRunOperation? =
    startOperation(token)?.let { markStopping(it) }

internal fun EmbeddedProductRunGate.markUncertain(token: RunToken): Boolean =
    startOperation(token)?.let { markInvoked(it); markUncertain(it) } ?: false
