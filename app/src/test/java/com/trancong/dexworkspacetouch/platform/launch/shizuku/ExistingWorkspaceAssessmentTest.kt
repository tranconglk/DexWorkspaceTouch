package com.trancong.dexworkspacetouch.platform.launch.shizuku

import com.trancong.dexworkspacetouch.feature.car.CarWorkflowExecutionArbiter
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.product.*
import com.trancong.dexworkspacetouch.platform.launch.bounds.*
import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.launcher.model.*
import org.junit.Assert.*
import org.junit.Test

class ExistingWorkspaceAssessmentTest {
    @Test fun wrongCorrelatedBoundsSuggestWithoutAnyMutation() {
        val commands = mutableListOf<List<String>>()
        val report = engine(WorkspaceCommandShell { commands += it; ok(dump(wrong)) }).assess(request, snapshot)
        assertEquals(WorkspaceAssessmentStatus.REPAIR_AVAILABLE, report.status)
        assertEquals(AssessmentCellStatus.WRONG_BOUNDS, report.cells.single().status)
        assertEquals(77, report.cells.single().observed!!.id)
        assertEquals(expected, report.cells.single().expected)
        assertEquals(listOf(listOf("dumpsys", "activity", "activities")), commands)
    }
    @Test fun allCorrectWithinTwoPixelsDoNotSuggestOrMutate() {
        val report = engine(WorkspaceCommandShell { assertEquals("dumpsys", it.first()); ok(dump(expected.copy(left=10))) })
            .assess(request, snapshot)
        assertEquals(WorkspaceAssessmentStatus.LAYOUT_CORRECT, report.status)
    }
    @Test fun missingAmbiguousAndUnidentifiedTasksNeverSuggest() {
        for (text in listOf("", dump(wrong)+"\n"+dump(wrong,78),
            dump(wrong).replace("* Hist  #0:","no activity:"))) {
            val report = engine(WorkspaceCommandShell { assertEquals("dumpsys",it.first()); ok(text) }).assess(request,snapshot)
            assertEquals(WorkspaceAssessmentStatus.PARTIAL_OR_UNRESOLVED,report.status)
        }
    }
    @Test fun wrongSafeTaskCanSuggestEvenWhenAnotherTaskIsMissing() {
        val req = request.copy(targets=request.targets+request.targets.single().copy(sourceCellId="missing",order=1,
            identity=AppIdentity("com.example.missing","com.example.missing.Main")))
        val report=engine(WorkspaceCommandShell { ok(dump(wrong)) }).assess(req,snapshot)
        assertEquals(WorkspaceAssessmentStatus.REPAIR_AVAILABLE,report.status)
        assertEquals(listOf(AssessmentCellStatus.WRONG_BOUNDS,AssessmentCellStatus.MISSING),report.cells.map { it.status })
    }
    @Test fun duplicateComponentsAndUnsafeContextAreUnresolved() {
        val duplicate=request.copy(targets=request.targets+request.targets.single().copy(sourceCellId="duplicate",order=1))
        assertEquals(WorkspaceAssessmentStatus.PARTIAL_OR_UNRESOLVED,
            engine(WorkspaceCommandShell { ok(dump(wrong)) }).assess(duplicate,snapshot).status)
        assertEquals(WorkspaceAssessmentStatus.UNAVAILABLE,
            engine(WorkspaceCommandShell { error("No inspection on internal display") }).assess(request,snapshot.copy(displayId=0)).status)
    }
    @Test fun transportUnavailableReleasesAdmission() {
        val gate=EmbeddedProductRunGate()
        val arbiter=CarWorkflowExecutionArbiter()
        assertEquals(WorkspaceAssessmentStatus.UNAVAILABLE,
            engine(WorkspaceCommandShell { throw IllegalStateException("Shizuku unavailable") },gate,arbiter).assess(request,snapshot).status)
        assertFalse(arbiter.isRunning.value)
        assertTrue(gate.canEnterEmbedded())
    }
    @Test fun embeddedCleanupBlockedAndArbiterConflictPreserveAuthority() {
        for(cleanup in listOf(false,true)) {
            val gate=EmbeddedProductRunGate()
            val token=gate.tryAcquireEmbedded("embedded")!!
            if(cleanup) {
                val op=gate.startOperation(token)!!
                gate.markInvoked(op)
                gate.acceptResult(op,ProductExecutionValue.from(
                    com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunResult.CleanupIncomplete(emptyList(),emptyList())))
            }
            val before=gate.status.value
            assertEquals(WorkspaceAssessmentStatus.UNAVAILABLE,
                engine(WorkspaceCommandShell { error("No shell") },gate).assess(request,snapshot).status)
            assertSame(before,gate.status.value)
        }
        val arbiter=CarWorkflowExecutionArbiter()
        assertTrue(arbiter.tryAcquire())
        assertEquals(WorkspaceAssessmentStatus.UNAVAILABLE,
            engine(WorkspaceCommandShell { error("No shell") },arbiter=arbiter).assess(request,snapshot).status)
        assertTrue(arbiter.isRunning.value)
    }
    @Test fun admissionIsHeldAcrossReadAndManualRepairStillWorksAfterAssessment() {
        val gate=EmbeddedProductRunGate()
        val arbiter=CarWorkflowExecutionArbiter()
        val pool=java.util.concurrent.Executors.newSingleThreadExecutor()
        var resized=false
        val commands=mutableListOf<List<String>>()
        try {
            val runner=engine(WorkspaceCommandShell {
                commands+=it
                assertFalse(arbiter.tryAcquire())
                assertFalse(pool.submit<Boolean> { gate.tryDispatchClassic {} }.get())
                assertNull(pool.submit<RunToken?> { gate.tryAcquireEmbedded("other") }.get())
                if(it.first()=="am") { resized=true;ok("") } else ok(dump(if(resized) expected else wrong))
            },gate,arbiter)
            assertEquals(WorkspaceAssessmentStatus.REPAIR_AVAILABLE,runner.assess(request,snapshot).status)
            assertTrue(commands.all { it==listOf("dumpsys","activity","activities") })
            assertTrue(runner.run(request,snapshot).complete)
            assertEquals(1,commands.count { it.take(3)==listOf("am","task","resize") })
            assertFalse(commands.any { it.getOrNull(1)=="start" })
        } finally { pool.shutdownNow() }
    }
    private fun engine(shell:WorkspaceCommandShell,gate:EmbeddedProductRunGate=EmbeddedProductRunGate(),
        arbiter:CarWorkflowExecutionArbiter=CarWorkflowExecutionArbiter())=ExistingWorkspaceRepair(gate,arbiter,shell,pause={},pollAttempts=3)
    private fun ok(text:String)=WorkspaceCommandResult(0,text)
    private fun dump(bounds:PixelBounds,id:Int=77)="Display #204 (activities from top to bottom):\n"+
        "  * Task{abc #$id type=standard U=0 visible=true mode=freeform}\n"+
        "    mBounds=Rect(${bounds.left}, ${bounds.top} - ${bounds.right}, ${bounds.bottom})\n"+
        "      * Hist  #0: ActivityRecord{act$id u0 com.example.calc/com.example.calc.Main t$id}\n"+
        "      Intent { cmp=com.example.calc/com.example.calc.Main }"
    private val request=WorkspaceLaunchRequest("swc","SWC",listOf(AppLaunchTarget("left",
        AppIdentity("com.example.calc","com.example.calc.Main"),NormalizedBounds(0f,0f,0.5f,1f),0)))
    private val expected=PixelBounds(8,33,952,1128)
    private val wrong=PixelBounds(100,100,800,800)
    private val snapshot=DisplayWorkAreaSnapshot(204,DisplayWorkArea(1920,1200,insetTopPx=25,insetBottomPx=64),
        DiagnosticPixelBounds(0,0,1920,1200),DiagnosticPixelBounds(0,0,1920,1200),1f,HostWindowMode.MAXIMIZED)
}
