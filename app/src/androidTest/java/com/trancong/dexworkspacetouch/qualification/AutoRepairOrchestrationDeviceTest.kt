package com.trancong.dexworkspacetouch.qualification

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.trancong.dexworkspacetouch.DexWorkspaceTouchApplication
import com.trancong.dexworkspacetouch.feature.car.overlay.*
import com.trancong.dexworkspacetouch.platform.launch.android.DisplayTargetWorkspaceLaunchRuntime
import com.trancong.dexworkspacetouch.platform.launch.bounds.PixelBounds
import com.trancong.dexworkspacetouch.platform.launch.shizuku.*
import com.trancong.dexworkspacetouch.workspace.apppicker.infrastructure.AndroidInstalledAppDataSource
import com.trancong.dexworkspacetouch.workspace.apppicker.model.DefaultInstalledAppCatalog
import com.trancong.dexworkspacetouch.workspace.designer.model.*
import com.trancong.dexworkspacetouch.workspace.launcher.WorkspaceLaunchRequestFactory
import com.trancong.dexworkspacetouch.workspace.launcher.model.*
import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import rikka.shizuku.Shizuku
import java.io.File

/** Bounded real-production transport smoke in a separate app. Never touches product data/license. */
class AutoRepairOrchestrationDeviceTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val app get()=context.applicationContext as DexWorkspaceTouchApplication
    private val evidence get()=File(context.filesDir,"swc004-evidence").apply { mkdirs() }

    @Test fun autoWithoutDockAndManualWithAutoOff() = runBlocking {
        check(context.packageName.endsWith(".swc004"))
        val args=InstrumentationRegistry.getArguments()
        val fixture=args.getString("fixture","s22")
        val displayId=args.getString("displayId")!!.toInt()
        val display=context.getSystemService(DisplayManager::class.java).getDisplay(displayId)
        check(display != null && displayId>0 && display.state==android.view.Display.STATE_ON) { "DeX display unavailable" }
        ensureQualificationPermission()
        val controller=app.workspaceRepairController
        assertEquals(ShizukuRuntimeState.READY,onMain { controller.refreshShizukuState() })
        val packageNames=when(fixture) {
            "s23" -> listOf("com.sec.android.app.popupcalculator","com.sec.android.app.clockpackage")
            "note9" -> listOf("com.android.chrome","com.sec.android.app.popupcalculator","com.ss.android.ugc.trill")
            else -> listOf("com.android.chrome","com.sec.android.app.sbrowser","com.samsung.android.app.tips")
        }
        val cuts=when(fixture) { "s23" -> listOf(0f,.5f,1f); "note9" -> listOf(0f,1f/3f,2f/3f,1f); else -> listOf(0f,.25f,.5f,1f) }
        val canvas=WorkspaceCanvas(packageNames.mapIndexed { i,pkg ->
            val component=checkNotNull(context.packageManager.getLaunchIntentForPackage(pkg)?.component) { "Fixture app missing: $pkg" }
            WorkspaceCell("cell-$i",NormalizedBounds(cuts[i],0f,cuts[i+1],1f),
                AssignedApp(component.packageName,component.className,pkg))
        })
        val workspace=Workspace("swc004-$fixture","SWC004 $fixture",canvas,1L,1,1L,1L)
        app.workspaceRepository.insert(workspace)
        val request=(WorkspaceLaunchRequestFactory(DefaultInstalledAppCatalog(AndroidInstalledAppDataSource.create(context)))
            .create(workspace.id,workspace.name,canvas) as LaunchReadiness.Ready).request
        val runtime=DisplayTargetWorkspaceLaunchRuntime(context,displayProvider={ display },repairController=controller)
        val originalAuto=app.carWorkspaceShortcutPreferences.autoRepairEnabled.value
        val originalDump=dump()
        File(evidence,"before.txt").writeText(originalDump)
        val original=ExistingTaskCorrelation.parse(originalDump)
        try {
            onMain { app.carFloatingDockCoordinator.hide(); app.carWorkspaceShortcutPreferences.setAutoRepairEnabled(true) }
            assertEquals(CarFloatingDockControlState.Hidden,app.carFloatingDockCoordinator.dockState.value)
            assertTrue(withContext(Dispatchers.Main.immediate) { runtime.launch(request) } is WorkspaceLaunchResult.Success)
            if(fixture=="note9") {
                // Explicit qualification setup: Classic can already be correct on this ROM.
                // Make only its freshly launched Chrome task wrong before the normal assessment.
                val chrome=checkNotNull(ExistingTaskCorrelation.select(
                    ExistingTaskCorrelation.parse(shell(WorkspaceTaskCorrelation.DUMP_COMMAND.joinToString(" "))),
                    "${request.targets.first().identity.packageName}/${request.targets.first().identity.activityName}",displayId))
                check(original.none { it.task.id==chrome.task.id }) { "Preserve pre-existing Chrome" }
                assertNull("Wrong fixture setup must precede assessment",controller.state.value.assessment)
                shell("am task resize ${chrome.task.id} 8 8 958 1020")
                File(evidence,"wrong-fixture.txt").writeText("Explicit pre-assessment setup: Chrome task ${chrome.task.id} -> [8,8,958,1020]")
            }
            await { controller.state.value.enabled &&
                (controller.state.value.assessment != null || controller.state.value.report != null) }
            val automatic=controller.state.value
            File(evidence,"auto-state.txt").writeText(automatic.toString())
            assertTrue("Auto result: $automatic", automatic.report?.complete==true ||
                automatic.assessment?.status==WorkspaceAssessmentStatus.LAYOUT_CORRECT)
            assertEquals(CarFloatingDockControlState.Hidden,app.carFloatingDockCoordinator.dockState.value)
            if(fixture=="s23") {
                assertEquals(WorkspaceAssessmentStatus.LAYOUT_CORRECT,automatic.assessment!!.status)
                assertNull("Correct layout requires no run/mutation",automatic.report)
            }
            if(fixture=="s22" || fixture=="note9") {
                assertEquals(3,automatic.report!!.cells.size)
                assertTrue(automatic.report.cells.any { it.status==RepairCellStatus.REPAIRED })
                automatic.report.cells.forEach { assertEquals(it.before!!.id,it.after!!.id) }
            }
            val after=dump()
            File(evidence,"after-auto.txt").writeText(after)
            val observed=ExistingTaskCorrelation.parse(after)
            val fixtureComponents=request.targets.map { "${it.identity.packageName}/${it.identity.activityName}" }
            val resolved=fixtureComponents.map { checkNotNull(ExistingTaskCorrelation.select(observed,it,displayId)) }
            assertEquals(packageNames.size,resolved.size)
            val columns=when(fixture) {
                "s23" -> listOf(8 to 958,962 to 1912)
                "note9" -> listOf(8 to 638,642 to 1278,1282 to 1912)
                else -> listOf(8 to 478,482 to 958,962 to 1912)
            }
            val bottom=if(fixture=="note9") 1020 else args.getString("expectedBottom","1016").toInt()
            val exact=columns.map { (left,right) -> PixelBounds(left,8,right,bottom) }
            assertEquals("Final exact bounds",exact,resolved.map { it.task.bounds })
            resolved.zipWithNext().forEach { (left,right) -> assertEquals(4,right.task.bounds!!.left-left.task.bounds!!.right) }
            assertEquals(8,resolved.first().task.bounds!!.left)
            val relevant=original.filter { it.task.component !in fixtureComponents &&
                it.task.component?.startsWith(context.packageName+"/")!=true }
            for(before in relevant) {
                val current=observed.singleOrNull { it.task.id==before.task.id && it.identity==before.identity } ?: continue
                assertEquals("Unrelated task ${before.task.id}",before.task.bounds,current.task.bounds)
                assertEquals(before.task.displayId,current.task.displayId)
            }
            if(fixture=="s22") {
                // Classic deliberately creates documents for some apps; close only this smoke's new windows
                // before the second launch so exact-correlation ambiguity is never hidden by the harness.
                for(task in resolved) {
                    check(original.none { it.task.id==task.task.id }) { "Fixture existed before smoke; preserve it" }
                    val b=task.task.bounds!!
                    shell("input -d $displayId tap ${b.right-32} ${b.top+20}")
                }
                delay(500)
                onMain { app.carWorkspaceShortcutPreferences.setAutoRepairEnabled(false) }
                assertTrue(withContext(Dispatchers.Main.immediate) { runtime.launch(request) } is WorkspaceLaunchResult.Success)
                delay(1500)
                assertNull(controller.state.value.assessment); assertNull(controller.state.value.report)
                val tasks=ExistingTaskCorrelation.parse(dump())
                val target=checkNotNull(ExistingTaskCorrelation.select(tasks,fixtureComponents.first(),displayId))
                val wrong="am task resize ${target.task.id} 100 100 600 700"
                shell(wrong) // Deliberately wrong fixture for explicit Manual Repair; no product engine changes.
                delay(300)
                assertEquals(PixelBounds(100,100,600,700),ExistingTaskCorrelation.select(
                    ExistingTaskCorrelation.parse(dump()),fixtureComponents.first(),displayId)!!.task.bounds)
                onMain { app.carFloatingDockCoordinator.show(AndroidCarOverlayHost(display)) }
                delay(300)
                assertTrue(onMain { app.carFloatingDockCoordinator.performRepairClickForTest() })
                await { controller.state.value.report != null }
                val manual=controller.state.value
                File(evidence,"manual-state.txt").writeText(manual.toString())
                assertTrue("Manual Dock Repair: $manual",manual.report!!.complete)
                assertFalse(app.carWorkspaceShortcutPreferences.autoRepairEnabled.value)
                assertEquals(target.task.id,manual.report.cells.first().after!!.id)
                assertEquals(exact,manual.report.cells.map { it.after!!.bounds })
                File(evidence,"after-manual.txt").writeText(dump())
            }
        } finally {
            onMain { app.carFloatingDockCoordinator.hide(); app.carWorkspaceShortcutPreferences.setAutoRepairEnabled(originalAuto) }
            // Restore pre-existing exact task bounds; never resize a replacement identity.
            val current=ExistingTaskCorrelation.parse(dump())
            for(before in original.filter { it.task.displayId==displayId && it.task.component in
                request.targets.map { "${it.identity.packageName}/${it.identity.activityName}" } }) {
                val same=current.singleOrNull { it.task.id==before.task.id && it.identity==before.identity } ?: continue
                val b=before.task.bounds ?: continue
                if(same.task.bounds!=b) shell("am task resize ${same.task.id} ${b.left} ${b.top} ${b.right} ${b.bottom}")
            }
            app.workspaceRepository.deleteById(workspace.id)
            File(evidence,"restore.txt").writeText("Auto=$originalAuto; Dock=${app.carFloatingDockCoordinator.dockState.value}; fixture deleted")
        }
    }

    private suspend fun dump():String=withContext(Dispatchers.IO) {
        AndroidShizukuCommandTransport(context,"swc004-evidence").use { it.execute(WorkspaceTaskCorrelation.DUMP_COMMAND).output }
    }
    private fun shell(command:String):String=ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes().toString(Charsets.UTF_8) }
    private fun <T> onMain(block:()->T):T {
        var value:Result<T>?=null
        instrumentation.runOnMainSync { value=runCatching(block) }
        return checkNotNull(value).getOrThrow()
    }
    private suspend fun await(condition:()->Boolean) {
        val deadline=SystemClock.uptimeMillis()+12000
        while(!condition()) { check(SystemClock.uptimeMillis()<deadline) { "Bounded smoke timed out: ${app.workspaceRepairController.state.value}" }; delay(100) }
    }
    private suspend fun ensureQualificationPermission() {
        // Explicit test setup, outside the automatic pipeline. Leaves Shizuku daemon/pairing untouched.
        check(Shizuku.pingBinder()) { "Start existing Shizuku before qualification" }
        if(Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED) return
        val info=instrumentation.uiAutomation.serviceInfo
        info.flags=info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        instrumentation.uiAutomation.serviceInfo=info
        onMain { Shizuku.requestPermission(41009) }
        val deadline=SystemClock.uptimeMillis()+12000
        while(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED) {
            val roots=if(android.os.Build.VERSION.SDK_INT>=30) {
                val windows=instrumentation.uiAutomation.windowsOnAllDisplays
                (0 until windows.size()).flatMap { windows.valueAt(it) }.mapNotNull { it.root }
            } else {
                instrumentation.uiAutomation.windows.mapNotNull { it.root } +
                    listOfNotNull(instrumentation.uiAutomation.rootInActiveWindow)
            }
            fun flatten(node:android.view.accessibility.AccessibilityNodeInfo):List<android.view.accessibility.AccessibilityNodeInfo> =
                listOf(node)+(0 until node.childCount).mapNotNull(node::getChild).flatMap(::flatten)
            val allow=roots.flatMap(::flatten)
                .firstOrNull { it.packageName?.toString()=="moe.shizuku.privileged.api" &&
                    it.text?.toString() in setOf("Allow all the time","Allow","Luôn cho phép","Cho phép","Cho phép mọi lúc") }
            allow?.let { node -> generateSequence(node) { it.parent }.firstOrNull { it.isClickable }
                ?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) }
            check(SystemClock.uptimeMillis()<deadline) { "Qualification setup permission unavailable" }
            delay(100)
        }
    }
}
