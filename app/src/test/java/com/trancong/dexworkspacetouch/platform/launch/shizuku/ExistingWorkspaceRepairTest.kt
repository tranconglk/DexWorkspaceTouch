package com.trancong.dexworkspacetouch.platform.launch.shizuku

import com.trancong.dexworkspacetouch.feature.car.CarWorkflowExecutionArbiter
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.product.EmbeddedProductRunGate
import com.trancong.dexworkspacetouch.platform.launch.bounds.*
import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.launcher.model.*
import org.junit.Assert.*
import org.junit.Test

class ExistingWorkspaceRepairTest {
    @Test fun incorrectExistingTaskIsRepairedWithoutLaunching() {
        var resized = false
        val commands = mutableListOf<List<String>>()
        val report = runner(WorkspaceCommandShell { command ->
            commands += command
            if (command.first() == "am") { resized = true; ok("") }
            else ok(dump(task(if (resized) expected else wrong)))
        }).run(request(), snapshot)
        assertTrue(report.admitted)
        assertEquals(RepairCellStatus.REPAIRED, report.cells.single().status)
        assertEquals(77, report.cells.single().after!!.id)
        assertEquals(expected, report.cells.single().after!!.bounds)
        assertEquals(listOf("am","task","resize","77","8","33","952","1128"),
            commands.single { it.first() == "am" })
        assertFalse(commands.any { it.getOrNull(1) == "start" })
    }
    @Test fun exactComponentDisplayUserVisibleFreeformAndActivityIdentityRequired() {
        for (candidate in listOf(task().copy(displayId=0),task().copy(userId=10),task().copy(visible=false),
            task().copy(freeform=false),task().copy(component="com.example.calc/com.example.calc.Other"))) {
            val commands = mutableListOf<List<String>>()
            val report = runner(WorkspaceCommandShell { commands += it; ok(dump(candidate)) }).run(request(),snapshot)
            assertFalse(report.complete)
            assertFalse(commands.any { it.first() == "am" })
        }
        val report = runner(WorkspaceCommandShell { ok(dump(task()).replace("* Hist  #0:","no activity:")) }).run(request(),snapshot)
        assertEquals(RepairCellStatus.UNRESOLVED,report.cells.single().status)
    }
    @Test fun ambiguousAndDuplicateTaskIdsFailClosed() {
        for (tasks in listOf(listOf(task(),task().copy(id=78)),listOf(task(),task()))) {
            val commands = mutableListOf<List<String>>()
            val report=runner(WorkspaceCommandShell { commands += it; ok(dump(*tasks.toTypedArray())) }).run(request(),snapshot)
            assertEquals(RepairCellStatus.UNRESOLVED,report.cells.single().status)
            assertFalse(commands.any { it.first()=="am" })
        }
    }
    @Test fun packageOnlyTargetAndDuplicateWorkspaceComponentFailClosed() {
        val target=request().targets.single()
        for (req in listOf(request().copy(targets=listOf(target.copy(identity=AppIdentity("com.example.calc",null)))),
            request().copy(targets=listOf(target,target.copy(sourceCellId="second",order=1))))) {
            val commands=mutableListOf<List<String>>()
            val report=runner(WorkspaceCommandShell { commands+=it; ok(dump(task())) }).run(req,snapshot)
            assertTrue(report.cells.all { it.status==RepairCellStatus.UNRESOLVED })
            assertFalse(commands.any { it.first()=="am" })
        }
    }
    @Test fun missingAppIsReportedAndNeverLaunched() {
        val commands=mutableListOf<List<String>>()
        val report=runner(WorkspaceCommandShell { commands+=it; ok("") }).run(request(),snapshot)
        assertEquals(RepairCellStatus.MISSING,report.cells.single().status)
        assertEquals(listOf(WorkspaceTaskCorrelation.DUMP_COMMAND),commands)
    }
    @Test fun alreadyCorrectWithinToleranceHasNoResize() {
        var calls=0
        val report=runner(WorkspaceCommandShell { assertEquals("dumpsys",it.first()); calls++; ok(dump(task(expected.copy(left=10)))) }).run(request(),snapshot)
        assertEquals(RepairCellStatus.CORRECT,report.cells.single().status)
        assertTrue(report.complete)
        assertEquals(2,calls)
    }
    @Test fun changedActivityIdentityBeforeResizeFailsClosed() {
        var reads=0
        val commands=mutableListOf<List<String>>()
        val report=runner(WorkspaceCommandShell { commands+=it; reads++; ok(dump(task()).let { text ->
            if(reads==1) text else text.replace("ActivityRecord{act77","ActivityRecord{new77") }) }).run(request(),snapshot)
        assertEquals(RepairCellStatus.UNRESOLVED,report.cells.single().status)
        assertFalse(commands.any { it.first()=="am" })
    }
    @Test fun newlyAmbiguousTaskBeforeResizeFailsClosed() {
        var reads=0
        val commands=mutableListOf<List<String>>()
        val report=runner(WorkspaceCommandShell { commands+=it; reads++; ok(if(reads==1) dump(task()) else dump(task(),task().copy(id=78))) }).run(request(),snapshot)
        assertEquals(RepairCellStatus.UNRESOLVED,report.cells.single().status)
        assertFalse(commands.any { it.first()=="am" })
    }
    @Test fun resizeErrorAndReadbackMismatchAreNotSuccess() {
        for (resizeError in listOf(true,false)) {
            val report=runner(WorkspaceCommandShell { if(it.first()=="am" && resizeError) WorkspaceCommandResult(1,"Error: refused")
                else ok(if(it.first()=="am") "" else dump(task())) }).run(request(),snapshot)
            assertEquals(if(resizeError) RepairCellStatus.RESIZE_FAILED else RepairCellStatus.READBACK_FAILED,report.cells.single().status)
            assertFalse(report.complete)
        }
    }
    @Test fun oneCellFailureAllowsOnlySafeNextCellResize() {
        val target=request().targets.single()
        val req=request().copy(targets=listOf(target.copy(identity=AppIdentity("com.example.missing","com.example.missing.Main")),
            target.copy(sourceCellId="right",order=1)))
        var resized=false
        val commands=mutableListOf<List<String>>()
        val report=runner(WorkspaceCommandShell { commands+=it; if(it.first()=="am") {resized=true;ok("")}
            else ok(dump(task(if(resized) expected else wrong),task().copy(id=99,component="com.unrelated.app/com.unrelated.app.Main"))) }).run(req,snapshot)
        assertEquals(listOf(RepairCellStatus.MISSING,RepairCellStatus.REPAIRED),report.cells.map { it.status })
        assertFalse(report.complete)
        assertEquals("77",commands.single { it.first()=="am" }[3])
    }
    @Test fun activeEmbeddedCleanupBlockedAndConflictingDispatchRejectWithoutShell() {
        for (cleanup in listOf(false,true)) {
            val gate=EmbeddedProductRunGate()
            val token=gate.tryAcquireEmbedded("embedded")!!
            if(cleanup) {
                val op=gate.startOperation(token)!!
                gate.markInvoked(op)
                gate.acceptResult(op,com.trancong.dexworkspacetouch.feature.embeddedworkspace.product.ProductExecutionValue.from(
                    com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunResult.CleanupIncomplete(emptyList(),emptyList())))
            }
            val before=gate.status.value
            val arbiter=CarWorkflowExecutionArbiter()
            assertFalse(runner(WorkspaceCommandShell { error("no shell") },gate,arbiter).run(request(),snapshot).admitted)
            assertSame(before,gate.status.value)
            assertFalse(arbiter.isRunning.value)
        }
        val arbiter=CarWorkflowExecutionArbiter()
        assertTrue(arbiter.tryAcquire())
        assertFalse(runner(WorkspaceCommandShell { error("no shell") },arbiter=arbiter).run(request(),snapshot).admitted)
        assertTrue(arbiter.isRunning.value)
        arbiter.release()
    }
    @Test fun holdsExistingGateAndArbiterAcrossInspectionAndReleasesAfterFailure() {
        val gate=EmbeddedProductRunGate()
        val arbiter=CarWorkflowExecutionArbiter()
        val pool=java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val report=runner(WorkspaceCommandShell {
                assertFalse(arbiter.tryAcquire())
                assertFalse(pool.submit<Boolean> { gate.tryDispatchClassic {} }.get())
                assertNull(pool.submit<com.trancong.dexworkspacetouch.feature.embeddedworkspace.product.RunToken?> { gate.tryAcquireEmbedded("other") }.get())
                throw IllegalStateException("transport failed")
            },gate,arbiter).run(request(),snapshot)
            assertTrue(report.admitted)
            assertEquals(RepairCellStatus.UNRESOLVED,report.cells.single().status)
            assertFalse(arbiter.isRunning.value)
            assertTrue(gate.canEnterEmbedded())
        } finally {pool.shutdownNow()}
    }
    @Test fun finalWorkspaceReadbackRejectsLaterDrift() {
        var reads=0
        val report=runner(WorkspaceCommandShell { if(it.first()=="am") ok("") else {reads++;ok(dump(task(if(reads in 3..5) expected else wrong)))} }).run(request(),snapshot)
        assertEquals(RepairCellStatus.READBACK_FAILED,report.cells.single().status)
    }
    @Test fun noEmbeddedRuntimeOrLaunchCapabilityInRepairClass() {
        val source=java.io.File("src/main/java/com/trancong/dexworkspacetouch/platform/launch/shizuku/ExistingWorkspaceRepair.kt").readText()
        assertFalse(source.contains("workspace.execution.embedded"))
        assertFalse(source.contains("launchCommand("))
        assertFalse(source.contains("UserService"))
    }
    private fun runner(shell: WorkspaceCommandShell, gate: EmbeddedProductRunGate = EmbeddedProductRunGate(),
        arbiter: CarWorkflowExecutionArbiter = CarWorkflowExecutionArbiter()) =
        ExistingWorkspaceRepair(gate, arbiter, shell, pause = {}, pollAttempts = 3)
    private fun request() = WorkspaceLaunchRequest("swc", "SWC", listOf(
        AppLaunchTarget("left", AppIdentity("com.example.calc", "com.example.calc.Main"),
            NormalizedBounds(0f,0f,0.5f,1f), 0)))
    private fun task(bounds: PixelBounds = wrong) = WorkspaceTask(77,204,0,COMPONENT,bounds,true,true)
    private fun dump(vararg tasks: WorkspaceTask) = tasks.joinToString("\n") {
        "Display #${it.displayId} (activities from top to bottom):\n" +
        "  * Task{abc #${it.id} type=standard U=${it.userId} visible=${it.visible} mode=${if(it.freeform) "freeform" else "fullscreen"}}\n" +
        "    mBounds=Rect(${it.bounds!!.left}, ${it.bounds.top} - ${it.bounds.right}, ${it.bounds.bottom})\n" +
        "      * Hist  #0: ActivityRecord{act${it.id} u${it.userId} ${it.component ?: COMPONENT} t${it.id}}\n" +
        "      Intent { ${it.launchIdentifier?.let { id -> "id=$id " } ?: ""}${it.component?.let { c -> "cmp=$c" } ?: "act=android.intent.action.MAIN"} }"
    }
    private fun ok(text: String) = WorkspaceCommandResult(0,text)
    private companion object {
        const val COMPONENT = "com.example.calc/com.example.calc.Main"
        val expected = PixelBounds(8,33,952,1128)
        val wrong = PixelBounds(100,100,800,800)
        val snapshot = DisplayWorkAreaSnapshot(204,DisplayWorkArea(1920,1200,insetTopPx=25,insetBottomPx=64),
            DiagnosticPixelBounds(0,0,1920,1200),DiagnosticPixelBounds(0,0,1920,1200),1f,HostWindowMode.MAXIMIZED)
    }
}
