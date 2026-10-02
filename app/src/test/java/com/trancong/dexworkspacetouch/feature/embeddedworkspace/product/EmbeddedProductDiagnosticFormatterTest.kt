package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppSessionId
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionFailure
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionPhase
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceCleanupOutcome
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceItemReceipt
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunResult
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

class EmbeddedProductDiagnosticFormatterTest {
    private val time = Instant.parse("2026-10-02T03:04:05.123Z")

    @Test fun exactHeader() = assertEquals("DWT Embedded diagnostics v1", text(ProductRunStatus()).lineSequence().first())

    @Test fun idleCanonicalOutputAndStableFieldOrder() = assertEquals(canonical(), text(ProductRunStatus()))

    @Test fun activeCanonicalOutputHasOnlyProvenanceBackedIds() {
        val status = ProductRunStatus(token = RunToken("ws"), generation = 1, startOperationId = 1,
            invocationCategory = ProductInvocationCategory.RESULT_OR_UNCERTAIN, phase = ProductRunPhase.ACTIVE,
            resultKind = ProductResultKind.STARTED, allocationEvidence = AllocationEvidence.POSSIBLE_OR_OWNED,
            cleanupEvidence = CleanupEvidence.UNCERTAIN, items = listOf(item()))
        assertEquals(canonical("ACTIVE", "ws", "1", "1", invocation = "RESULT_OR_UNCERTAIN",
            result = "STARTED", allocation = "POSSIBLE_OR_OWNED", cleanup = "UNCERTAIN", items = itemText()), text(status))
    }

    @Test fun utcDoesNotDependOnLocaleOrTimeZone() {
        val locale = Locale.getDefault()
        val zone = TimeZone.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
            assertEquals(canonical(), text(ProductRunStatus()))
        } finally { Locale.setDefault(locale); TimeZone.setDefault(zone) }
    }

    @Test fun suppliedSnapshotTimeIsTheOnlyTimeUsed() {
        val snapshot = EmbeddedProductDiagnostics.from(ProductRunStatus(), Instant.EPOCH)
        assertEquals(canonical().replace(time.toString(), "1970-01-01T00:00:00Z"), format(snapshot))
    }

    @Test fun identifierWithoutProvenanceRemainsUnknown() {
        val id = EmbeddedDiagnosticId.copied("unowned-id", null)
        assertNull(id.value)
        assertNull(id.provenance)
    }

    @Test fun blankReceiptIdentifiersRemainUnknown() {
        val output = text(ProductRunStatus(items = listOf(item().copy(sourceCellId = "", sessionId = "", displayId = -1))))
        assertTrue(output.contains("item[0].source_cell_id=unknown;provenance=unknown"))
        assertTrue(output.contains("item[0].owned_session_id=unknown;provenance=unknown"))
        assertTrue(output.contains("item[0].display_id=unknown;provenance=unknown"))
    }

    @Test fun noVdmTaskOrRemoteResourceIdIsInferred() {
        val output = text(ProductRunStatus(token = RunToken("workspace-vdm-9"), items = listOf(item())))
        for (name in listOf("vdm_id", "task_id", "remote_resource_id", "run_identity")) {
            assertTrue(output.contains("$name=unknown;provenance=unknown"))
        }
        assertFalse(output.contains("pkg"))
    }

    @Test fun cleanupIncompleteCanonicalBlockedCauseAndEvidence() {
        val f = blocked(incomplete())
        assertEquals(canonical("CLEANUP_BLOCKED", "ws", "1", "1", "2", "RESULT_OR_UNCERTAIN",
            "INCOMPLETE", "RuntimeCleanupIncomplete", "cell", "INCOMPLETE", "POSSIBLE_OR_OWNED", "INCOMPLETE",
            itemText(), cleanupText("INCOMPLETE", "unknown")), text(f.gate.status.value))
    }

    @Test fun recoveryRequiredRemoteDiedUsesCopiedValues() {
        val f = blocked(EmbeddedWorkspaceRunResult.RecoveryRequired("cell", listOf(receipt().copy(phase = EmbeddedSessionPhase.REMOTE_DIED)),
            listOf(EmbeddedWorkspaceCleanupOutcome.RecoveryRequired("cell", EmbeddedSessionFailure("REMOTE_DIED", "hidden")))))
        val output = text(f.gate.status.value)
        assertTrue(output.contains("result_kind=RECOVERY_REQUIRED\nissue_code=RemoteDied"))
        assertTrue(output.contains("blocked_cause=REMOTE_DIED"))
        assertTrue(output.contains("cleanup[0].failure_code=REMOTE_DIED"))
    }

    @Test fun recoveryRequiredWithoutRemoteEvidenceRemainsUncertain() {
        val output = text(ProductRunStatus(phase = ProductRunPhase.CLEANUP_BLOCKED,
            resultKind = ProductResultKind.RECOVERY_REQUIRED, cleanupEvidence = CleanupEvidence.UNCERTAIN))
        assertTrue(output.contains("blocked_cause=RECOVERY_REQUIRED"))
        assertTrue(output.contains("issue_code=unknown"))
    }

    @Test fun cleanupOutcomeUncertainIsRepresented() {
        val output = text(blocked(null).gate.status.value)
        assertTrue(output.contains("issue_code=CleanupOutcomeUncertain"))
        assertTrue(output.contains("blocked_cause=UNCERTAIN"))
        assertTrue(output.contains("cleanup_evidence=UNCERTAIN"))
    }

    @Test fun cleanupTimeoutIsCopiedAsValueEvidence() {
        val f = blocked(incomplete("CLEANUP_TIMEOUT"))
        val snapshot = EmbeddedProductDiagnostics.from(f.gate.status.value, time)
        assertEquals("CLEANUP_TIMEOUT", snapshot.cleanupOutcomes.single().failureCode)
        assertEquals(EmbeddedCleanupBlockedCause.TIMEOUT, snapshot.blockedCause)
        assertTrue(format(snapshot).contains("cleanup[0].failure_code=CLEANUP_TIMEOUT"))
    }

    @Test fun timeoutCodeOnCleanOutcomeDoesNotInventTimeoutCause() {
        val output = text(ProductRunStatus(phase = ProductRunPhase.CLEANUP_BLOCKED,
            cleanupOutcomes = listOf(ProductCleanupStatus("cell", CleanupEvidence.CLEAN_CONFIRMED, "CLEANUP_TIMEOUT"))))
        assertTrue(output.contains("blocked_cause=UNKNOWN"))
    }

    @Test fun startingFormatsSafely() = assertPhase(ProductRunPhase.STARTING)
    @Test fun stoppingFormatsSafely() = assertPhase(ProductRunPhase.STOPPING)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun snapshotFormatsAfterControllerAndExecutionAreDropped() = kotlinx.coroutines.test.runTest {
        var execution: EmbeddedProductExecution? = object : EmbeddedProductExecution {
            override suspend fun start(): EmbeddedWorkspaceRunResult = EmbeddedWorkspaceRunResult.Started(listOf(receipt()))
            override suspend fun close(): EmbeddedWorkspaceRunResult = incomplete("CLEANUP_TIMEOUT")
        }
        val gate = EmbeddedProductRunGate()
        val root = kotlinx.coroutines.Job()
        val scope = kotlinx.coroutines.CoroutineScope(root + kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        var controller: EmbeddedWorkspaceProductController? = EmbeddedWorkspaceProductController("ws", gate,
            EmbeddedCapabilityProbe { EmbeddedCapabilitySnapshot(true, true, true, true) },
            { ProductHostReadiness(true, true) }, execution!!, scope)
        controller!!.start()
        assertEquals(ProductRunPhase.ACTIVE, gate.state.value)
        controller.onHostDisposed().join()
        assertTrue(root.isCompleted)
        controller = null
        execution = null
        assertNull(controller)
        assertNull(execution)
        // Một host mới chỉ cần Application status sau terminal và sau khi bỏ graph route cũ.
        val snapshot = EmbeddedProductDiagnostics.from(gate.status.value, time)
        assertEquals(canonical("CLEANUP_BLOCKED", "ws", "1", "1", "2", "RESULT_OR_UNCERTAIN",
            "INCOMPLETE", "RuntimeCleanupIncomplete", "cell", "TIMEOUT", "POSSIBLE_OR_OWNED", "INCOMPLETE",
            itemText(), cleanupText("INCOMPLETE", "CLEANUP_TIMEOUT")), format(snapshot))
    }

    @Test fun inputCollectionMutationCannotChangeSnapshot() {
        val items = mutableListOf(item())
        val outcomes = mutableListOf(ProductCleanupStatus("cell", CleanupEvidence.INCOMPLETE, "CLEANUP_TIMEOUT"))
        val snapshot = EmbeddedProductDiagnostics.from(ProductRunStatus(items = items, cleanupOutcomes = outcomes), time)
        items.clear(); outcomes.clear()
        assertEquals("session", snapshot.items.single().ownedSessionId.value)
        assertEquals("CLEANUP_TIMEOUT", snapshot.cleanupOutcomes.single().failureCode)
        assertTrue(format(snapshot).contains("items=1;omitted=0"))
    }

    @Test fun consumerCannotMutateSnapshotLists() {
        val snapshot = EmbeddedProductDiagnostics.from(ProductRunStatus(items = listOf(item()),
            cleanupOutcomes = listOf(ProductCleanupStatus("cell", CleanupEvidence.UNCERTAIN, null))), time)
        try { (snapshot.items as MutableList).clear(); fail("immutable items") } catch (_: UnsupportedOperationException) { }
        try { (snapshot.cleanupOutcomes as MutableList).clear(); fail("immutable outcomes") } catch (_: UnsupportedOperationException) { }
    }

    @Test fun entireDtoGraphContainsOnlyFinalValueFields() {
        val snapshot = EmbeddedProductDiagnostics.from(blocked(incomplete("CLEANUP_TIMEOUT")).gate.status.value, time)
        assertValueGraph(snapshot)
        val forbidden = listOf("Throwable", "Binder", "Surface", "Activity", "Context", "Flow", "Job", "Deferred", "Function", "Controller", "Runner", "SessionPort")
        for (field in EmbeddedProductDiagnostics::class.java.declaredFields) {
            assertFalse(forbidden.any { field.type.name.contains(it) })
        }
    }

    @Test fun failureMessageAndStackTraceAreNeverCopied() {
        val output = text(blocked(incomplete("CLEANUP_TIMEOUT", "java.lang.Throwable: secret\n at stackTrace(Binder@123 Surface@456)")).gate.status.value)
        assertFalse(output.contains("Throwable")); assertFalse(output.contains("stackTrace"))
        assertFalse(output.contains("Binder@")); assertFalse(output.contains("Surface@"))
    }

    @Test fun licenseAndSecurityMaterialInUnallowlistedFieldsIsOmitted() {
        val secret = "license activation_token Authorization Bearer private_key public_key Cloudflare admin logcat"
        val output = text(ProductRunStatus(items = listOf(item().copy(packageName = secret, componentName = secret)),
            cleanupOutcomes = listOf(ProductCleanupStatus("cell", CleanupEvidence.INCOMPLETE, secret))))
        assertFalse(output.contains(secret))
        for (word in secret.split(' ')) assertFalse(output.contains(word))
    }

    @Test fun arbitraryUnknownFailureCodeIsNotDumped() {
        val output = text(ProductRunStatus(cleanupOutcomes = listOf(ProductCleanupStatus("cell", CleanupEvidence.UNCERTAIN, "OPAQUE_PRIVATE_PAYLOAD"))))
        assertTrue(output.contains("cleanup[0].failure_code=unknown"))
        assertFalse(output.contains("OPAQUE_PRIVATE_PAYLOAD"))
    }

    @Test fun longIdentifiersAreOmittedRatherThanAmbiguouslyShortened() {
        val snapshot = EmbeddedProductDiagnostics.from(ProductRunStatus(token = RunToken("x".repeat(10000)),
            items = listOf(item().copy(sourceCellId = "a".repeat(10000), sessionId = "b".repeat(10000)))), time)
        assertNull(snapshot.workspaceId.value)
        assertNull(snapshot.items.single().sourceCellId.value)
        assertNull(snapshot.items.single().ownedSessionId.value)
        assertTrue(format(snapshot).contains("workspace_id=unknown;provenance=unknown"))
    }

    @Test fun unsafeIdentifierCannotInjectLinesOrBrokenSurrogates() {
        for (id in listOf("cell\nAuthorization=secret", "cell\u0000", "cell\uD800", "cell;provenance=forged")) {
            assertNull(EmbeddedProductDiagnostics.from(ProductRunStatus(token = RunToken(id)), time).workspaceId.value)
        }
    }

    @Test fun listAndTotalOutputHaveExplicitBounds() {
        val id = "x".repeat(256)
        val snapshot = EmbeddedProductDiagnostics.from(ProductRunStatus(token = RunToken(id),
            items = List(10000) { item().copy(sourceCellId = id, sessionId = id) },
            cleanupOutcomes = List(10000) { ProductCleanupStatus(id, CleanupEvidence.INCOMPLETE, "CLEANUP_TIMEOUT") }), time)
        assertEquals(2, snapshot.items.size); assertEquals(9998, snapshot.omittedItemCount)
        assertEquals(2, snapshot.cleanupOutcomes.size); assertEquals(9998, snapshot.omittedCleanupCount)
        assertTrue(format(snapshot).length <= EmbeddedProductDiagnosticFormatter.MAX_OUTPUT_CHARS)
        assertEquals(8192, EmbeddedProductDiagnosticFormatter.MAX_OUTPUT_CHARS)
    }

    @Test fun explicitCopyWritesExactlyOnceAndNeverMutatesBlockedGate() {
        val f = blocked(incomplete("CLEANUP_TIMEOUT"))
        val before = f.gate.status.value
        val output = text(before)
        val writes = mutableListOf<String>()
        val action = EmbeddedProductDiagnosticCopyAction(output, writes::add)
        assertTrue(writes.isEmpty())
        action.onUserTap()
        assertEquals(listOf(output), writes)
        assertSame(before, f.gate.status.value)
        assertFalse(f.gate.canEnterEmbedded())
        assertFalse(f.gate.tryDispatchClassic { fail("copy must not unlock") })
        assertEquals(setOf(EmbeddedCleanupBlockedAction.BACK, EmbeddedCleanupBlockedAction.VIEW_STATUS), before.cleanupBlockedUi()!!.permittedActions)
    }

    @Test fun noClipboardWriteBeforeTapEvenAfterRepeatedFormatting() {
        var writes = 0
        val snapshot = EmbeddedProductDiagnostics.from(ProductRunStatus(), time)
        EmbeddedProductDiagnosticCopyAction(format(snapshot)) { writes++ }
        repeat(5) { format(snapshot) }
        assertEquals(0, writes)
    }

    @Test fun absentSnapshotHasExplicitMessageAndCannotCopy() {
        assertEquals("Chưa có chẩn đoán Embedded", EmbeddedProductDiagnosticFormatter.format(null))
        var writes = 0
        EmbeddedProductDiagnosticCopyAction(null) { writes++ }.onUserTap()
        assertEquals(0, writes)
    }

    @Test fun repeatedFormattingIsExactlyDeterministic() {
        val snapshot = EmbeddedProductDiagnostics.from(blocked(incomplete("CLEANUP_TIMEOUT")).gate.status.value, time)
        val first = format(snapshot)
        repeat(5) { assertEquals(first, format(snapshot)) }
        assertTrue(first.startsWith("DWT Embedded diagnostics v1\nsnapshot_utc=$time\n"))
    }

    @Test fun laterTransitionCannotMutateAlreadyCopiedSnapshot() {
        val gate = EmbeddedProductRunGate()
        val start = invoke(gate)
        val snapshot = EmbeddedProductDiagnostics.from(gate.status.value, time)
        val original = format(snapshot)
        gate.markStopping(start)
        assertEquals(ProductRunPhase.STARTING, snapshot.phase)
        assertEquals(original, format(snapshot))
        assertTrue(original.contains("phase=STARTING"))
    }

    @Test fun staleOldCallbackCannotAlterSnapshotOrCurrentGate() {
        val f = blocked(incomplete("CLEANUP_TIMEOUT"))
        val snapshot = EmbeddedProductDiagnostics.from(f.gate.status.value, time)
        val before = f.gate.status.value
        val original = format(snapshot)
        assertFalse(f.gate.acceptResult(f.start, ProductExecutionValue.from(EmbeddedWorkspaceRunResult.Started(listOf(receipt())))))
        assertFalse(f.gate.acceptResult(f.cleanup, ProductExecutionValue.from(EmbeddedWorkspaceRunResult.Stopped(listOf(receipt()),
            listOf(EmbeddedWorkspaceCleanupOutcome.Clean("cell"))))))
        assertSame(before, f.gate.status.value)
        assertEquals(original, format(snapshot))
        assertTrue(original.contains("blocked_cause=TIMEOUT"))
    }

    private fun assertPhase(phase: ProductRunPhase) = assertEquals(canonical(phase.name), text(ProductRunStatus(phase = phase)))
    private fun text(status: ProductRunStatus) = format(EmbeddedProductDiagnostics.from(status, time))
    private fun format(snapshot: EmbeddedProductDiagnostics) = EmbeddedProductDiagnosticFormatter.format(snapshot)
    private fun item() = ProductItemStatus("cell", "session", "pkg", "pkg.Main", 0, EmbeddedSessionPhase.ACTIVE, 10)
    private fun receipt() = EmbeddedWorkspaceItemReceipt("cell", EmbeddedAppSessionId("session"), "pkg", "pkg.Main", 0, EmbeddedSessionPhase.ACTIVE, 10)
    private fun incomplete(code: String? = null, message: String = "private detail") = EmbeddedWorkspaceRunResult.CleanupIncomplete(listOf(receipt()),
        listOf(EmbeddedWorkspaceCleanupOutcome.Incomplete("cell", code?.let { EmbeddedSessionFailure(it, message) })))
    private data class Blocked(val gate: EmbeddedProductRunGate, val start: ProductRunOperation, val cleanup: ProductRunOperation)
    private fun blocked(result: EmbeddedWorkspaceRunResult?): Blocked {
        val gate = EmbeddedProductRunGate()
        val start = invoke(gate)
        val cleanup = gate.markStopping(start)!!
        assertTrue(gate.acceptResult(cleanup, ProductExecutionValue.from(result)))
        return Blocked(gate, start, cleanup)
    }
    private fun invoke(gate: EmbeddedProductRunGate) = gate.startOperation(gate.tryAcquireEmbedded("ws")!!)!!.also { assertTrue(gate.markInvoked(it)) }

    private fun canonical(phase: String = "IDLE", workspace: String = "unknown", generation: String = "0", start: String = "unknown",
        cleanupOp: String = "unknown", invocation: String = "NOT_INVOKED", result: String = "unknown", issue: String = "unknown",
        cell: String = "unknown", cause: String = "unknown", allocation: String = "NONE_CONFIRMED", cleanup: String = "NOT_NEEDED",
        items: String = "items=0;omitted=0", outcomes: String = "cleanup_outcomes=0;omitted=0") = """
        DWT Embedded diagnostics v1
        snapshot_utc=$time
        workspace_id=$workspace;provenance=${if (workspace == "unknown") "unknown" else "PRODUCT_RUN_WORKSPACE"}
        phase=$phase
        run_identity=unknown;provenance=unknown
        generation=$generation
        start_operation_id=$start
        cleanup_operation_id=$cleanupOp
        invocation=$invocation
        readiness=unknown
        shizuku=unknown
        result_kind=$result
        issue_code=$issue
        issue_source_cell_id=$cell;provenance=${if (cell == "unknown") "unknown" else "PRODUCT_ISSUE"}
        blocked_cause=$cause
        allocation_evidence=$allocation
        cleanup_evidence=$cleanup
        vdm_id=unknown;provenance=unknown
        task_id=unknown;provenance=unknown
        remote_resource_id=unknown;provenance=unknown
        $items
        $outcomes
    """.lines().joinToString("\n") { it.trimStart() }.trim()

    private fun itemText() = """
        items=1;omitted=0
        item[0].source_cell_id=cell;provenance=OWNED_ITEM_RESULT
        item[0].owned_session_id=session;provenance=OWNED_ITEM_RESULT
        item[0].display_id=10;provenance=OWNED_ITEM_RESULT
        item[0].order=0
        item[0].phase=ACTIVE
    """.trimIndent()
    private fun cleanupText(evidence: String, failure: String) = """
        cleanup_outcomes=1;omitted=0
        cleanup[0].source_cell_id=cell;provenance=CLEANUP_RESULT
        cleanup[0].evidence=$evidence
        cleanup[0].failure_code=$failure
    """.trimIndent()
}
