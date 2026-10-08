package com.trancong.dexworkspacetouch.feature.car.overlay

import com.trancong.dexworkspacetouch.feature.car.*
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.product.*
import com.trancong.dexworkspacetouch.platform.launch.bounds.*
import com.trancong.dexworkspacetouch.platform.launch.shizuku.*
import com.trancong.dexworkspacetouch.workspace.launcher.model.*
import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.designer.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceRepairPolicyTest {
    @Test fun changingAutoPreferenceDuringManualRepairPreservesManualResult() = runTest {
        for (initial in listOf(false,true)) {
            val enabled=MutableStateFlow(initial)
            var mutations=0
            val session=WorkspaceRepairSession(this,MutableStateFlow(false),{ error("No auto launch") },
                { delay(1000); mutations++; CarDockRepairState("Manual completed",true) },autoRepairEnabled=enabled)
            session.selectWorkspace(request.workspaceId)
            session.repair(); runCurrent()
            enabled.value=!initial; runCurrent(); advanceUntilIdle()
            assertEquals(1,mutations)
            assertEquals("Manual completed",session.state.value.label)
            assertTrue(session.state.value.enabled)
            session.dispose()
        }
    }

    @Test fun preferenceAtAcceptedSuccessControlsAutoEvenWhenChangedDuringClassicLaunch() = runTest {
        val enabled=MutableStateFlow(false)
        var assessments=0; var repairs=0
        val session=WorkspaceRepairSession(this,MutableStateFlow(false),
            { assessments++; ExistingWorkspaceAssessmentReport(WorkspaceAssessmentStatus.REPAIR_AVAILABLE) },
            { error("No click") },autoRepairEnabled=enabled,
            automaticRepair={ _, _ -> repairs++; CarDockRepairState("Repaired",true) })
        session.classicLaunchStarted(request.workspaceId)
        enabled.value=true; runCurrent()
        session.classicLaunchCompleted(request); advanceUntilIdle()
        assertEquals(1,assessments); assertEquals(1,repairs)
        session.dispose()
    }
    @Test fun unavailableAtTriggerSkipsTransportAndLaterReadyDoesNotRepair() = runTest {
        for (unavailable in listOf(ShizukuRuntimeState.PERMISSION_MISSING, ShizukuRuntimeState.NOT_RUNNING,
            ShizukuRuntimeState.UNAVAILABLE)) {
            val enabled = MutableStateFlow(true)
            var capability = unavailable
            var assessments = 0
            var repairs = 0
            val session = WorkspaceRepairSession(this, MutableStateFlow(false),
                { assessments++; error("Unavailable transport must not be touched") },
                { repairs++; error("Unavailable manual transport must not be touched") },
                autoRepairEnabled=enabled, capability={ capability },
                automaticRepair={ _, _ -> repairs++; CarDockRepairState() })
            launch(session); advanceUntilIdle()
            assertTrue(enabled.value)
            assertEquals(unavailable, session.state.value.shizukuState)
            session.repair(); advanceUntilIdle()
            assertEquals(unavailable, session.state.value.shizukuState)
            capability = ShizukuRuntimeState.READY
            advanceTimeBy(30000); runCurrent()
            // Re-delivered Success, UI refresh/resume and readiness changes cannot resurrect this launch.
            session.classicLaunchCompleted(request); advanceUntilIdle()
            assertEquals(0, assessments); assertEquals(0, repairs)
            session.dispose()
        }
    }

    @Test fun permissionLostDuringStabilizationSkipsAssessmentWithoutRetry() = runTest {
        var ready = ShizukuRuntimeState.READY
        var assessments = 0
        val session = WorkspaceRepairSession(this, MutableStateFlow(false), { assessments++; error("No read") },
            { error("No manual click") }, autoRepairEnabled=MutableStateFlow(true), capability={ ready })
        launch(session); runCurrent()
        ready = ShizukuRuntimeState.PERMISSION_MISSING
        advanceUntilIdle()
        ready = ShizukuRuntimeState.READY
        advanceTimeBy(30000); runCurrent()
        assertEquals(0, assessments)
        session.dispose()
    }
    @Test fun offDoesNotAssessAndManualRepairStillUsesExistingEngine() = runTest {
        val f = Fixture(WorkspaceRepairMode.OFF)
        val session = f.session(this)
        launch(session); advanceUntilIdle()
        assertEquals("Repair", session.state.value.label)
        assertEquals(0, f.commands.size)
        session.repair(); advanceUntilIdle()
        assertTrue(session.state.value.report!!.complete)
        assertEquals(1, f.resizes)
        session.dispose()
    }

    @Test fun legacySuggestDoesNotAssessOrMutateAndManualStillWorks() = runTest {
        val f = Fixture()
        val session = f.session(this)
        launch(session); advanceUntilIdle()
        assertEquals("Repair", session.state.value.label)
        assertTrue(f.commands.isEmpty())
        session.repair(); advanceUntilIdle()
        assertEquals(1, f.resizes)
        session.dispose()
    }

    @Test fun automaticWrongLayoutInvokesSameEngineOnceAndRetainsTaskIdentity() = runTest {
        val f = Fixture(WorkspaceRepairMode.AUTOMATIC)
        val session = f.session(this)
        launch(session); advanceUntilIdle()
        val report = session.state.value.report!!
        assertTrue(report.complete)
        assertEquals(77, report.cells.single().before!!.id)
        assertEquals(77, report.cells.single().after!!.id)
        assertEquals(expected, report.cells.single().after!!.bounds)
        assertEquals(1, f.autoAttempts)
        assertEquals(1, f.resizes)
        assertTrue(f.commands.all { it == WorkspaceTaskCorrelation.DUMP_COMMAND || it.take(3) == listOf("am", "task", "resize") })
        val commandCount = f.commands.size
        session.classicLaunchCompleted(request)
        advanceTimeBy(30000); runCurrent()
        assertEquals(commandCount, f.commands.size)
        assertEquals(1, f.autoAttempts)
        session.dispose()
    }

    @Test fun automaticCorrectLayoutOnlyReadsOnceAndDoesNotInvokeRepair() = runTest {
        val f = Fixture(WorkspaceRepairMode.AUTOMATIC).apply { bounds = expected }
        val session = f.session(this)
        launch(session); advanceUntilIdle()
        assertEquals(WorkspaceAssessmentStatus.LAYOUT_CORRECT, session.state.value.assessment!!.status)
        assertEquals(listOf(WorkspaceTaskCorrelation.DUMP_COMMAND), f.commands)
        assertEquals(0, f.autoAttempts)
        assertNull(session.state.value.report)
        session.dispose()
    }

    @Test fun freshCorrectBoundsBetweenAssessmentAndRunNeverResizeStaleSnapshot() = runTest {
        val f = Fixture(WorkspaceRepairMode.AUTOMATIC).apply { beforeAuto = { bounds = expected } }
        val session = f.session(this)
        launch(session); advanceUntilIdle()
        assertEquals(1, f.autoAttempts)
        assertEquals(0, f.resizes)
        assertEquals(RepairCellStatus.CORRECT, session.state.value.report!!.cells.single().status)
        session.dispose()
    }

    @Test fun taskOrActivityIdentityChangedBetweenAssessmentAndRunFailsClosed() = runTest {
        for (changeTask in listOf(false, true)) {
            val f = Fixture(WorkspaceRepairMode.AUTOMATIC).apply { beforeAuto = { if (changeTask) taskId = 78 else activity = "replacement" } }
            val session = f.session(this)
            launch(session); advanceUntilIdle()
            assertEquals(1, f.autoAttempts)
            assertEquals(0, f.resizes)
            assertFalse(session.state.value.report!!.complete)
            assertEquals(RepairCellStatus.UNRESOLVED, session.state.value.report!!.cells.single().status)
            session.dispose()
        }
    }

    @Test fun partialRepairIsNeverReportedAsFullSuccess() = runTest {
        val f = Fixture(WorkspaceRepairMode.AUTOMATIC).apply { resizeFails = true }
        val session = f.session(this)
        launch(session); advanceUntilIdle()
        assertFalse(session.state.value.report!!.complete)
        assertEquals("Repair: 0/1", session.state.value.label)
        assertEquals(RepairCellStatus.RESIZE_FAILED, session.state.value.report!!.cells.single().status)
        session.dispose()
    }

    @Test fun unresolvedOrUnavailableNeverInvokeAutomaticRepair() = runTest {
        for (status in listOf(WorkspaceAssessmentStatus.PARTIAL_OR_UNRESOLVED, WorkspaceAssessmentStatus.UNAVAILABLE)) {
            var attempts = 0
            val session = WorkspaceRepairSession(this, MutableStateFlow(false), { ExistingWorkspaceAssessmentReport(status) },
                { error("Manual not requested") }, autoRepairEnabled = MutableStateFlow(true),
                automaticRepair = { _, _ -> attempts++; error("Unsafe trigger") })
            launch(session); advanceUntilIdle()
            assertEquals(status, session.state.value.assessment!!.status)
            assertEquals(0, attempts)
            session.dispose()
        }
    }

    @Test fun shizukuUnavailableDoesNotPropagateFailureFromClassicCompletion() = runTest {
        val session = WorkspaceRepairSession(this, MutableStateFlow(false), { error("Shizuku unavailable") },
            { error("Must not repair") }, autoRepairEnabled = MutableStateFlow(true))
        launch(session); advanceUntilIdle()
        assertEquals(WorkspaceAssessmentStatus.UNAVAILABLE, session.state.value.assessment!!.status)
        session.dispose()
    }

    @Test fun lateAssessmentCannotRepairAfterWorkspaceOrGenerationOrDockChanges() = runTest {
        for (change in listOf<(WorkspaceRepairSession) -> Unit>({ it.selectWorkspace("other") },
            { it.classicLaunchStarted(request.workspaceId) }, { it.invalidateSuggestion() }, { it.dispose() })) {
            val late = CompletableDeferred<ExistingWorkspaceAssessmentReport>()
            var attempts = 0
            val session = WorkspaceRepairSession(this, MutableStateFlow(false), { withContext(NonCancellable) { late.await() } },
                { error("No click") }, autoRepairEnabled = MutableStateFlow(true),
                automaticRepair = { _, _ -> attempts++; CarDockRepairState() })
            launch(session); advanceTimeBy(1000); runCurrent()
            change(session)
            late.complete(ExistingWorkspaceAssessmentReport(WorkspaceAssessmentStatus.REPAIR_AVAILABLE))
            advanceUntilIdle()
            assertEquals(0, attempts)
            assertNull(session.state.value.report)
            session.dispose()
        }
    }

    @Test fun anotherWorkflowReservationAfterAssessmentDiscardsAutomaticAction() = runTest {
        val f = Fixture(WorkspaceRepairMode.AUTOMATIC)
        lateinit var session: WorkspaceRepairSession
        session = f.session(this, assess = {
            val result = f.engine.assess(it, snapshot)
            f.busy.value = true
            session.controlActionStarted()
            result
        })
        launch(session); advanceUntilIdle()
        assertEquals(0, f.autoAttempts)
        assertEquals(0, f.resizes)
        session.dispose()
    }

    @Test fun secondClassicLaunchCancelsPendingAutomaticRepairEvenForSameWorkspace() = runTest {
        val entered = CompletableDeferred<Unit>()
        var mutations = 0
        val session = WorkspaceRepairSession(this, MutableStateFlow(false),
            { ExistingWorkspaceAssessmentReport(WorkspaceAssessmentStatus.REPAIR_AVAILABLE) }, { error("No click") },
            autoRepairEnabled = MutableStateFlow(true), automaticRepair = { _, _ ->
                entered.complete(Unit); delay(1000); mutations++; CarDockRepairState()
            })
        launch(session); advanceTimeBy(1000); runCurrent()
        assertTrue(entered.isCompleted)
        session.classicLaunchStarted(request.workspaceId)
        advanceUntilIdle()
        assertEquals(0, mutations)
        assertNull(session.state.value.report)
        session.dispose()
    }

    @Test fun modeChangedToOffCancelsPendingAssessmentAndAutoRepair() = runTest {
        for (duringRepair in listOf(false, true)) {
            val mode = MutableStateFlow(true)
            var mutations = 0
            val session = WorkspaceRepairSession(this, MutableStateFlow(false),
                { ExistingWorkspaceAssessmentReport(WorkspaceAssessmentStatus.REPAIR_AVAILABLE) }, { error("No click") },
                autoRepairEnabled = mode, automaticRepair = { _, _ -> delay(1000); mutations++; CarDockRepairState() })
            launch(session)
            if (duringRepair) { advanceTimeBy(1000); runCurrent() } else runCurrent()
            mode.value = false
            runCurrent(); advanceUntilIdle()
            assertEquals(0, mutations)
            assertEquals("Repair", session.state.value.label)
            session.dispose()
        }
    }

    @Test fun embeddedAndCleanupBlockedStillPreventAutomaticMutation() = runTest {
        for (cleanup in listOf(false, true)) {
            val f = Fixture(WorkspaceRepairMode.AUTOMATIC)
            f.beforeAuto = {
                val token = f.gate.tryAcquireEmbedded("embedded")!!
                if (cleanup) {
                    val operation = f.gate.startOperation(token)!!
                    f.gate.markInvoked(operation)
                    f.gate.acceptResult(operation, ProductExecutionValue.from(
                        com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunResult.CleanupIncomplete(emptyList(), emptyList())))
                }
            }
            val session = f.session(this)
            launch(session); advanceUntilIdle()
            assertFalse(session.state.value.report!!.admitted)
            assertEquals(0, f.resizes)
            assertEquals(if (cleanup) ProductRunPhase.CLEANUP_BLOCKED else ProductRunPhase.STARTING, f.gate.status.value.phase)
            session.dispose()
        }
    }

    private fun launch(session: WorkspaceRepairSession) {
        session.classicLaunchStarted(request.workspaceId)
        session.classicLaunchCompleted(request)
    }

    private class Fixture(initialMode: WorkspaceRepairMode = WorkspaceRepairMode.SUGGEST) {
        val mode = MutableStateFlow(initialMode == WorkspaceRepairMode.AUTOMATIC)
        val busy = MutableStateFlow(false)
        val gate = EmbeddedProductRunGate()
        val commands = mutableListOf<List<String>>()
        var bounds = wrong
        var taskId = 77
        var activity = "original"
        var autoAttempts = 0
        var resizeFails = false
        var beforeAuto: () -> Unit = {}
        val resizes get() = commands.count { it.take(3) == listOf("am", "task", "resize") }
        val engine = ExistingWorkspaceRepair(gate, CarWorkflowExecutionArbiter(), WorkspaceCommandShell { command ->
            commands += command
            if (command == WorkspaceTaskCorrelation.DUMP_COMMAND) WorkspaceCommandResult(0,
                "Display #204 (activities from top to bottom):\n" +
                "  * Task{abc #$taskId type=standard U=0 visible=true mode=freeform}\n" +
                "    mBounds=Rect(${bounds.left}, ${bounds.top} - ${bounds.right}, ${bounds.bottom})\n" +
                "      * Hist  #0: ActivityRecord{$activity u0 com.example.calc/com.example.calc.Main t$taskId}\n" +
                "      Intent { cmp=com.example.calc/com.example.calc.Main }")
            else {
                check(command.take(3) == listOf("am", "task", "resize")) { "Unexpected mutation or launch: $command" }
                if (resizeFails) WorkspaceCommandResult(1, "Error: rejected")
                else { bounds = PixelBounds(command[4].toInt(), command[5].toInt(), command[6].toInt(), command[7].toInt()); WorkspaceCommandResult(0, "") }
            }
        }, pause = {}, pollAttempts = 3)

        fun session(scope: CoroutineScope, assess: suspend (WorkspaceLaunchRequest) -> ExistingWorkspaceAssessmentReport = { engine.assess(it, snapshot) }) =
            WorkspaceRepairSession(scope, busy, assess, { engine.run(request, snapshot).toDockState() }, autoRepairEnabled = mode,
                automaticRepair = { current, assessment ->
                    autoAttempts++; beforeAuto(); engine.run(current, snapshot, assessment).toDockState()
                })
    }

    private companion object {
        val request = WorkspaceLaunchRequest("swc", "SWC", listOf(AppLaunchTarget("left",
            AppIdentity("com.example.calc", "com.example.calc.Main"), NormalizedBounds(0f, 0f, 0.5f, 1f), 0)))
        val wrong = PixelBounds(100, 100, 800, 800)
        val expected = PixelBounds(8, 33, 958, 1128)
        val snapshot = DisplayWorkAreaSnapshot(204, DisplayWorkArea(1920, 1200, insetTopPx = 25, insetBottomPx = 64),
            DiagnosticPixelBounds(0, 0, 1920, 1200), DiagnosticPixelBounds(0, 0, 1920, 1200), 1f, HostWindowMode.MAXIMIZED)
    }
}
