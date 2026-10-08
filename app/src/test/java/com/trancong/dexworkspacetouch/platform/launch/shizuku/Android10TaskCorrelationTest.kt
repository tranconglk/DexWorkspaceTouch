package com.trancong.dexworkspacetouch.platform.launch.shizuku

import com.trancong.dexworkspacetouch.feature.car.CarWorkflowExecutionArbiter
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.product.EmbeddedProductRunGate
import com.trancong.dexworkspacetouch.platform.launch.bounds.*
import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.launcher.model.*
import org.junit.Assert.*
import org.junit.Test

class Android10TaskCorrelationTest {
    @Test fun legacyTaskUsesOwnBoundsStackModeAndLiveActivityVisibility() {
        val text = dump(legacy(77, CHROME, WRONG), legacy(78, CALCULATOR, MIDDLE))
        val tasks = WorkspaceTaskCorrelation.parse(text)
        assertEquals(listOf(77, 78), tasks.map { it.id })
        assertEquals(listOf(WRONG, MIDDLE), tasks.map { it.bounds })
        assertTrue(tasks.all { it.displayId == 2 && it.userId == 0 && it.freeform && it.visible })
        assertEquals(CHROME, tasks.first().component)
        assertEquals("task77:act77:0:$CHROME", ExistingTaskCorrelation.select(
            ExistingTaskCorrelation.parse(text), CHROME, 2)!!.identity)
    }

    @Test fun threeColumnAssessmentAndAutomaticRepairResizeOnlyWrongChrome() = verifyRepair(true)
    @Test fun threeColumnManualRepairResizesOnlyWrongChrome() = verifyRepair(false)

    @Test fun hiddenOrMissingLiveVisibilityNeverUsesHasBeenVisible() {
        for (text in listOf(legacy(77, CHROME, WRONG, visible = false),
            legacy(77, CHROME, WRONG).replace("keysPaused=false inHistory=true visible=true", "keysPaused=false inHistory=true"))) {
            assertFalse(WorkspaceTaskCorrelation.parse(dump(text)).single().visible)
            assertRejected(dump(text))
        }
    }

    @Test fun visibleDifferentActivityCannotAuthorizeHiddenTarget() {
        val text = legacy(77, CHROME, WRONG, visible = false) + "\n" +
            "      * Hist #1: ActivityRecord{other u0 com.android.chrome/.Other t77}\n" +
            "          Intent { cmp=com.android.chrome/.Other }\n" +
            "          keysPaused=false inHistory=true visible=true"
        assertFalse(WorkspaceTaskCorrelation.parse(dump(text)).single().visible)
        assertRejected(dump(text))
    }

    @Test fun fullscreenWrongUserAndWrongDisplayRemainIneligible() {
        for (text in listOf(dump(legacy(77, CHROME, WRONG), mode = "fullscreen"),
            dump(legacy(77, CHROME, WRONG, user = 10)),
            dump(legacy(77, CHROME, WRONG), display = 0))) assertRejected(text)
    }

    @Test fun missingMismatchedAndChangedActivityIdentityFailClosed() {
        for (text in listOf(legacy(77, CHROME, WRONG).replace("* Hist #0:", "no activity:"),
            legacy(77, CHROME, WRONG).replace(" t77}", " t999}"),
            legacy(77, CHROME, WRONG).replace("ActivityRecord{act77 u0", "ActivityRecord{act77 u10"))) {
            assertRejected(dump(text))
        }
        var reads = 0
        val commands = mutableListOf<List<String>>()
        val report = engine(WorkspaceCommandShell { command ->
            commands += command
            reads++
            WorkspaceCommandResult(0, dump(legacy(77, CHROME, WRONG).let {
                if (reads == 1) it else it.replace("TaskRecord{task77", "TaskRecord{replacement")
            }))
        }).run(request(), SNAPSHOT)
        assertEquals(RepairCellStatus.UNRESOLVED, report.cells.single().status)
        assertTrue(commands.all { it == WorkspaceTaskCorrelation.DUMP_COMMAND })
    }

    @Test fun ambiguousAndDuplicateLegacyTasksRemainIneligible() {
        for (text in listOf(dump(legacy(77, CHROME, WRONG), legacy(78, CHROME, WRONG)),
            dump(legacy(77, CHROME, WRONG), legacy(77, CHROME, WRONG)))) assertRejected(text)
    }

    @Test fun taskPreambleMustMatchAndCannotBorrowOtherTaskBounds() {
        for (text in listOf(legacy(77, CHROME, WRONG).replace("Task id #77", "Task id #999"),
            legacy(77, CHROME, WRONG).replace("    mBounds=Rect(8, 8 - 958, 1020)\n", ""))) {
            val tasks = WorkspaceTaskCorrelation.parse(dump(legacy(99, OTHER, OTHER_BOUNDS), text))
            assertNull(tasks.last().bounds)
            assertRejected(dump(legacy(99, OTHER, OTHER_BOUNDS), text))
        }
    }

    @Test fun stackDisplayAndRunningSectionsCannotContaminateLegacyTask() {
        val first = legacy(77, CHROME, WRONG)
        val text = dump(first) + "\n    Running activities (most recent first):\n" +
            "      mBounds=Rect(1, 1 - 11, 11)\n" +
            "Display #0 (activities from top to bottom):\n" +
            "  Stack #0: type=home mode=fullscreen\n  mBounds=Rect(0, 0 - 0, 0)\n" +
            legacy(99, OTHER, OTHER_BOUNDS) + "\nActivityTaskSupervisor state:\n" +
            dump(legacy(100, CHROME, WRONG))
        val tasks = WorkspaceTaskCorrelation.parse(text)
        assertEquals(listOf(77, 99), tasks.map { it.id })
        assertEquals(WRONG, tasks.first().bounds)
        assertTrue(tasks.first().freeform)
        assertFalse(tasks.last().freeform)
        assertEquals(0, tasks.last().displayId)
    }

    @Test fun modernTaskFormatStillCorrelatesWithoutLegacyPreamble() {
        val text = "Display #2 (activities from top to bottom):\n" +
            "  * Task{modern #77 type=standard U=0 visible=true mode=freeform}\n" +
            "    mBounds=Rect(8, 8 - 958, 1020)\n" +
            "    * Hist #0: ActivityRecord{modernActivity u0 $CHROME t77}\n" +
            "      Intent { cmp=$CHROME }"
        val selected = ExistingTaskCorrelation.select(ExistingTaskCorrelation.parse(text), CHROME, 2)!!
        assertEquals(WRONG, selected.task.bounds)
        assertEquals("modern:modernActivity:0:$CHROME", selected.identity)
    }

    private fun verifyRepair(automatic: Boolean) {
        var chromeBounds = WRONG
        val commands = mutableListOf<List<String>>()
        val runner = engine(WorkspaceCommandShell { command ->
            commands += command
            when (command.take(3)) {
                WorkspaceTaskCorrelation.DUMP_COMMAND -> WorkspaceCommandResult(0, dump(
                    legacy(77, CHROME, chromeBounds), legacy(78, CALCULATOR, MIDDLE),
                    legacy(79, TIKTOK, RIGHT), legacy(99, OTHER, OTHER_BOUNDS)))
                listOf("am", "task", "resize") -> {
                    assertEquals(listOf("am", "task", "resize", "77", "8", "8", "638", "1020"), command)
                    chromeBounds = LEFT
                    WorkspaceCommandResult(0, "")
                }
                else -> error("Unexpected command: $command")
            }
        })
        val req = request(threeColumns = true)
        val assessment = runner.assess(req, SNAPSHOT)
        assertEquals(WorkspaceAssessmentStatus.REPAIR_AVAILABLE, assessment.status)
        assertEquals(listOf(AssessmentCellStatus.WRONG_BOUNDS, AssessmentCellStatus.CORRECT,
            AssessmentCellStatus.CORRECT), assessment.cells.map { it.status })
        val report = runner.run(req, SNAPSHOT, if (automatic) assessment else null)
        assertTrue(report.complete)
        assertEquals(listOf(RepairCellStatus.REPAIRED, RepairCellStatus.CORRECT, RepairCellStatus.CORRECT),
            report.cells.map { it.status })
        assertEquals(listOf(77, 78, 79), report.cells.map { it.after!!.id })
        assertEquals(listOf(LEFT, MIDDLE, RIGHT), report.cells.map { it.after!!.bounds })
        assertEquals(1, commands.count { it.first() == "am" })
        assertEquals(WorkspaceAssessmentStatus.LAYOUT_CORRECT, runner.assess(req, SNAPSHOT).status)
        val before = commands.count { it.first() == "am" }
        assertTrue(runner.run(req, SNAPSHOT).complete)
        assertEquals(before, commands.count { it.first() == "am" })
    }

    private fun assertRejected(text: String) {
        val commands = mutableListOf<List<String>>()
        val report = engine(WorkspaceCommandShell { commands += it; WorkspaceCommandResult(0, text) })
            .run(request(), SNAPSHOT)
        assertFalse(report.complete)
        assertTrue(commands.all { it == WorkspaceTaskCorrelation.DUMP_COMMAND })
    }

    private fun engine(shell: WorkspaceCommandShell) = ExistingWorkspaceRepair(
        EmbeddedProductRunGate(), CarWorkflowExecutionArbiter(), shell, pause = {}, pollAttempts = 3)

    private fun request(threeColumns: Boolean = false): WorkspaceLaunchRequest {
        val components = if (threeColumns) listOf(CHROME, CALCULATOR, TIKTOK) else listOf(CHROME)
        return WorkspaceLaunchRequest("note9", "Note 9", components.mapIndexed { i, c ->
            AppLaunchTarget("cell-$i", AppIdentity(c.substringBefore('/'), c.substringAfter('/')),
                NormalizedBounds(i / 3f, 0f, (i + 1) / 3f, 1f), i)
        })
    }

    private fun dump(vararg tasks: String, display: Int = 2, mode: String = "freeform") =
        "Display #$display (activities from top to bottom):\n" +
            "  Stack #5: type=standard mode=$mode\n  mBounds=Rect(0, 0 - 0, 0)\n" + tasks.joinToString("\n")

    private fun legacy(id: Int, component: String, bounds: PixelBounds, visible: Boolean = true, user: Int = 0) =
        "    Task id #$id\n" +
            "    mBounds=Rect(${bounds.left}, ${bounds.top} - ${bounds.right}, ${bounds.bottom})\n" +
            "    mLastNonFullscreenBounds=Rect(100, 100 - 800, 800)\n" +
            "    * TaskRecord{task$id #$id A=${component.substringBefore('/')} U=$user StackId=5 sz=1}\n" +
            "      hasBeenVisible=true\n" +
            "      * Hist #0: ActivityRecord{act$id u$user $component t$id}\n" +
            "          Intent { cmp=$component }\n" +
            "          mGlobalConfig={winConfig={ mBounds=Rect(0, 0 - 1920, 1080) mDisplayWindowingMode=freeform }}\n" +
            "          keysPaused=false inHistory=true visible=$visible sleeping=false idle=true\n"

    private companion object {
        const val CHROME = "com.android.chrome/com.google.android.apps.chrome.Main"
        const val CALCULATOR = "com.sec.android.app.popupcalculator/com.sec.android.app.popupcalculator.Calculator"
        const val TIKTOK = "com.zhiliaoapp.musically/com.ss.android.ugc.aweme.splash.SplashActivity"
        const val OTHER = "com.example.other/com.example.other.Main"
        val WRONG = PixelBounds(8, 8, 958, 1020)
        val LEFT = PixelBounds(8, 8, 638, 1020)
        val MIDDLE = PixelBounds(642, 8, 1278, 1020)
        val RIGHT = PixelBounds(1282, 8, 1912, 1020)
        val OTHER_BOUNDS = PixelBounds(20, 20, 80, 80)
        val SNAPSHOT = DisplayWorkAreaSnapshot(2, DisplayWorkArea(1920, 1080, insetBottomPx = 52),
            DiagnosticPixelBounds(0, 0, 1920, 1080), DiagnosticPixelBounds(0, 0, 1920, 1028),
            1f, HostWindowMode.MAXIMIZED)
    }
}
