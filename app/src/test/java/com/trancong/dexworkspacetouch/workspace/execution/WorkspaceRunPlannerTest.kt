package com.trancong.dexworkspacetouch.workspace.execution

import com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledApp
import com.trancong.dexworkspacetouch.workspace.apppicker.model.ListInstalledAppCatalog
import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlanner
import com.trancong.dexworkspacetouch.workspace.launcher.WorkspaceLaunchRequestFactory
import com.trancong.dexworkspacetouch.workspace.launcher.model.LaunchReadiness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class WorkspaceRunPlannerTest {
    @Test
    fun `classic returns the exact resolved request without mutation`() {
        val planner = planner(installed("com.app", "Main"))
        val result = planner.plan(runRequest(WorkspaceRunMode.CLASSIC, readyCanvas()))
            as WorkspaceRunPlanningResult.Classic

        assertEquals("workspace", result.request.workspaceId)
        assertEquals("Workspace", result.request.workspaceName)
        assertEquals("cell", result.request.targets.single().sourceCellId)
        assertEquals("Main", result.request.targets.single().identity.activityName)
        assertEquals(NormalizedBounds.FullCanvas, result.request.targets.single().bounds)
    }

    @Test
    fun `embedded plans from the same resolved request fields`() {
        val planner = planner(installed("com.app", "Main"))

        val classic = planner.plan(runRequest(WorkspaceRunMode.CLASSIC, readyCanvas()))
            as WorkspaceRunPlanningResult.Classic
        val embedded = planner.plan(runRequest(WorkspaceRunMode.EMBEDDED, readyCanvas()))
            as WorkspaceRunPlanningResult.Embedded

        val target = classic.request.targets.single()
        val item = embedded.plan.items.single()
        assertEquals(classic.request.workspaceId, embedded.plan.workspaceId)
        assertEquals(classic.request.workspaceName, embedded.plan.workspaceName)
        assertEquals(target.sourceCellId, item.sourceCellId)
        assertEquals(target.identity.packageName, item.packageName)
        assertEquals(target.identity.activityName, item.componentName)
        assertEquals(target.bounds, item.normalizedBounds)
        assertEquals(target.order, item.order)
    }

    @Test
    fun `both modes preserve deterministic resolution for null assigned activity`() {
        val planner = planner(
            installed("com.app", "ZActivity"),
            installed("com.app", "AActivity"),
        )
        val canvas = singleCellCanvas(AssignedApp("com.app", null, "App"))

        val classic = planner.plan(runRequest(WorkspaceRunMode.CLASSIC, canvas))
            as WorkspaceRunPlanningResult.Classic
        val embedded = planner.plan(runRequest(WorkspaceRunMode.EMBEDDED, canvas))
            as WorkspaceRunPlanningResult.Embedded

        assertEquals("AActivity", classic.request.targets.single().identity.activityName)
        assertEquals("AActivity", embedded.plan.items.single().componentName)
    }

    @Test
    fun `both modes return the same rejection for every non-ready readiness class`() {
        val scenarios = listOf(
            planner() to WorkspaceCanvas(emptyList()),
            planner() to WorkspaceCanvas.singleCell(),
            planner(installed("com.app", "Main")) to overlappingCanvas(),
            planner(installed("com.app", "Main")) to stripedCanvas(6),
            planner() to readyCanvas(),
            planner(InstalledApp("com.app", "Main", "App", launchable = false)) to readyCanvas(),
        )

        scenarios.forEach { (planner, canvas) ->
            val classic = planner.plan(runRequest(WorkspaceRunMode.CLASSIC, canvas))
                as WorkspaceRunPlanningResult.Rejected
            val embedded = planner.plan(runRequest(WorkspaceRunMode.EMBEDDED, canvas))
                as WorkspaceRunPlanningResult.Rejected
            assertEquals(classic.readiness, embedded.readiness)
        }

        assertTrue((scenarios[0].first.plan(runRequest(WorkspaceRunMode.CLASSIC, scenarios[0].second)) as WorkspaceRunPlanningResult.Rejected).readiness is LaunchReadiness.EmptyWorkspace)
        assertTrue((scenarios[1].first.plan(runRequest(WorkspaceRunMode.CLASSIC, scenarios[1].second)) as WorkspaceRunPlanningResult.Rejected).readiness is LaunchReadiness.EmptyCells)
        assertTrue((scenarios[2].first.plan(runRequest(WorkspaceRunMode.CLASSIC, scenarios[2].second)) as WorkspaceRunPlanningResult.Rejected).readiness is LaunchReadiness.InvalidCanvas)
        assertTrue((scenarios[3].first.plan(runRequest(WorkspaceRunMode.CLASSIC, scenarios[3].second)) as WorkspaceRunPlanningResult.Rejected).readiness is LaunchReadiness.TooManyTargets)
        assertTrue((scenarios[4].first.plan(runRequest(WorkspaceRunMode.CLASSIC, scenarios[4].second)) as WorkspaceRunPlanningResult.Rejected).readiness is LaunchReadiness.MissingApplications)
        assertTrue((scenarios[5].first.plan(runRequest(WorkspaceRunMode.CLASSIC, scenarios[5].second)) as WorkspaceRunPlanningResult.Rejected).readiness is LaunchReadiness.NonLaunchableApplications)
    }

    @Test
    fun `planning invokes no launcher or execution resource`() {
        val fieldTypes = WorkspaceRunPlanner::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }
            .map { it.type.name }

        assertEquals(2, fieldTypes.size)
        assertTrue(fieldTypes.any { it.endsWith("WorkspaceLaunchRequestFactory") })
        assertTrue(fieldTypes.any { it.endsWith("EmbeddedWorkspacePlanner") })
        assertTrue(fieldTypes.none { it.contains("LaunchRuntime") || it.endsWith("WorkspaceLauncher") || it.startsWith("android.") })
    }

    private fun planner(vararg apps: InstalledApp) = WorkspaceRunPlanner(
        requestFactory = WorkspaceLaunchRequestFactory(ListInstalledAppCatalog(apps.toList())),
        embeddedPlanner = EmbeddedWorkspacePlanner(),
    )

    private fun runRequest(mode: WorkspaceRunMode, canvas: WorkspaceCanvas) = WorkspaceRunRequest(
        workspaceId = "workspace",
        workspaceName = "Workspace",
        canvas = canvas,
        mode = mode,
    )

    private fun readyCanvas() = singleCellCanvas(AssignedApp("com.app", "Main", "App"))

    private fun singleCellCanvas(app: AssignedApp) = WorkspaceCanvas(
        listOf(WorkspaceCell("cell", NormalizedBounds.FullCanvas, app)),
    )

    private fun overlappingCanvas(): WorkspaceCanvas {
        val app = AssignedApp("com.app", "Main", "App")
        return WorkspaceCanvas(
            listOf(
                WorkspaceCell("one", NormalizedBounds.FullCanvas, app),
                WorkspaceCell("two", NormalizedBounds.FullCanvas, app),
            ),
        )
    }

    private fun stripedCanvas(count: Int): WorkspaceCanvas {
        val app = AssignedApp("com.app", "Main", "App")
        return WorkspaceCanvas(
            List(count) { index ->
                WorkspaceCell(
                    id = "cell-$index",
                    bounds = NormalizedBounds(
                        left = index.toFloat() / count,
                        top = 0f,
                        right = (index + 1).toFloat() / count,
                        bottom = 1f,
                    ),
                    app = app,
                )
            },
        )
    }

    private fun installed(packageName: String, activityName: String) =
        InstalledApp(packageName, activityName, "App", launchable = true)
}
