package com.trancong.dexworkspacetouch.platform.launch.shizuku

import com.trancong.dexworkspacetouch.feature.car.CarWorkflowExecutionArbiter
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.product.EmbeddedProductRunGate
import com.trancong.dexworkspacetouch.platform.launch.android.*
import com.trancong.dexworkspacetouch.platform.launch.bounds.*
import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.launcher.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WorkspaceGutterConsumerTest {
    @Test
    fun `Classic assessment and Repair agree on S23 halves`() = verify(
        DisplayWorkArea(1920, 1144), listOf(
            NormalizedBounds(0f, 0f, 0.5f, 1f), NormalizedBounds(0.5f, 0f, 1f, 1f)),
        listOf(PixelBounds(8, 8, 958, 1136), PixelBounds(962, 8, 1912, 1136)),
    )

    @Test
    fun `Classic assessment and Repair agree on S22 quarters and half`() = verify(
        DisplayWorkArea(1920, 1024), listOf(
            NormalizedBounds(0f, 0f, 0.25f, 1f), NormalizedBounds(0.25f, 0f, 0.5f, 1f),
            NormalizedBounds(0.5f, 0f, 1f, 1f)),
        listOf(PixelBounds(8, 8, 478, 1016), PixelBounds(482, 8, 958, 1016),
            PixelBounds(962, 8, 1912, 1016)),
    )

    @Test
    fun `Classic assessment and Repair agree on odd two by two`() = verify(
        DisplayWorkArea(101, 99), listOf(
            NormalizedBounds(0f, 0f, 0.5f, 0.5f), NormalizedBounds(0.5f, 0f, 1f, 0.5f),
            NormalizedBounds(0f, 0.5f, 0.5f, 1f), NormalizedBounds(0.5f, 0.5f, 1f, 1f)),
        listOf(PixelBounds(8, 8, 49, 48), PixelBounds(53, 8, 93, 48),
            PixelBounds(8, 52, 49, 91), PixelBounds(53, 52, 93, 91)),
    )

    @Test
    fun `Classic assessment and Repair agree on single full canvas`() = verify(
        DisplayWorkArea(1920, 1144), listOf(NormalizedBounds.FullCanvas),
        listOf(PixelBounds(8, 8, 1912, 1136)),
    )

    @Test
    fun `Classic assessment and Repair agree on unequal cells with insets`() = verify(
        DisplayWorkArea(1040, 880, 10, 20, 30, 60), listOf(
            NormalizedBounds(0f, 0f, 0.3f, 1f), NormalizedBounds(0.3f, 0f, 1f, 0.4f),
            NormalizedBounds(0.3f, 0.4f, 1f, 1f)),
        listOf(PixelBounds(18, 28, 308, 812), PixelBounds(312, 28, 1002, 338),
            PixelBounds(312, 342, 1002, 812)),
    )

    private fun verify(area: DisplayWorkArea, cells: List<NormalizedBounds>, expected: List<PixelBounds>) = runBlocking {
        for (density in listOf(1f, 2.625f)) {
            val snapshot = DisplayWorkAreaSnapshot(2, area,
                DiagnosticPixelBounds(0, 0, area.widthPx, area.heightPx),
                DiagnosticPixelBounds(0, 0, area.widthPx, area.heightPx), density, HostWindowMode.MAXIMIZED)
            val targets = cells.mapIndexed { index, cell -> AppLaunchTarget("cell-$index",
                AppIdentity("com.example.app$index", "com.example.app$index.Main"), cell, index) }
            val request = WorkspaceLaunchRequest("gutter", "Gutter", targets)
            val started = mutableListOf<PixelBounds>()
            val platform = object : SingleAppLaunchPlatform {
                override fun currentSnapshot() = snapshot
                override fun verifyComponent(identity: AppIdentity) = ComponentVerificationResult.AVAILABLE
                override suspend fun start(target: AppLaunchTarget, bounds: PixelBounds, expectedDisplayId: Int): PlatformStartResult {
                    assertEquals(2, expectedDisplayId)
                    started += bounds
                    return PlatformStartResult.SUCCESS
                }
            }
            val classic = AndroidSingleAppLauncher(platform)
            targets.forEach { assertTrue(classic.launch(it) is SingleAppLaunchResult.Success) }
            assertEquals(expected, started)

            val tasks = targets.mapIndexed { index, target -> WorkspaceTask(77 + index, 2, 0,
                "${target.identity.packageName}/${target.identity.activityName}", started[index], true, true) }.toMutableList()
            val unrelated = WorkspaceTask(99, 2, 0, "com.example.other/com.example.other.Main",
                PixelBounds(1, 1, 11, 11), true, true)
            tasks += unrelated
            val commands = mutableListOf<List<String>>()
            val repair = ExistingWorkspaceRepair(EmbeddedProductRunGate(), CarWorkflowExecutionArbiter(),
                WorkspaceCommandShell { command ->
                    commands += command
                    when (command.take(3)) {
                        listOf("dumpsys", "activity", "activities") -> WorkspaceCommandResult(0, dump(tasks))
                        listOf("am", "task", "resize") -> {
                            val index = tasks.indexOfFirst { it.id == command[3].toInt() }
                            assertTrue(index in targets.indices)
                            tasks[index] = tasks[index].copy(bounds = PixelBounds(command[4].toInt(),
                                command[5].toInt(), command[6].toInt(), command[7].toInt()))
                            WorkspaceCommandResult(0, "")
                        }
                        else -> error("Unexpected command: $command")
                    }
                }, pause = {}, pollAttempts = 3)
            val correct = repair.assess(request, snapshot)
            assertEquals(WorkspaceAssessmentStatus.LAYOUT_CORRECT, correct.status)
            assertEquals(expected, correct.cells.map { it.expected })
            assertTrue(repair.run(request, snapshot).complete)
            assertTrue(commands.all { it == WorkspaceTaskCorrelation.DUMP_COMMAND })

            targets.indices.forEach { tasks[it] = tasks[it].copy(bounds = PixelBounds(20, 20, 80, 80)) }
            commands.clear()
            val wrong = repair.assess(request, snapshot)
            assertEquals(WorkspaceAssessmentStatus.REPAIR_AVAILABLE, wrong.status)
            val report = repair.run(request, snapshot, wrong)
            assertTrue(report.complete)
            assertEquals(expected, report.cells.map { it.expected })
            assertEquals(expected, report.cells.map { it.after!!.bounds })
            assertEquals(targets.indices.map { 77 + it }, report.cells.map { it.before!!.id })
            assertEquals(targets.indices.map { 77 + it }, report.cells.map { it.after!!.id })
            assertTrue(report.cells.all { it.status == RepairCellStatus.REPAIRED })
            assertEquals(targets.size, commands.count { it.take(3) == listOf("am", "task", "resize") })
            assertEquals(unrelated, tasks.last())
        }
    }

    private fun dump(tasks: List<WorkspaceTask>) = "Display #2 (activities from top to bottom):\n" +
        tasks.joinToString("\n") {
            "  * Task{task${it.id} #${it.id} type=standard U=0 visible=true mode=freeform}\n" +
                "    mBounds=Rect(${it.bounds!!.left}, ${it.bounds.top} - ${it.bounds.right}, ${it.bounds.bottom})\n" +
                "      * Hist  #0: ActivityRecord{activity${it.id} u0 ${it.component} t${it.id}}\n" +
                "      Intent { cmp=${it.component} }"
        }
}
