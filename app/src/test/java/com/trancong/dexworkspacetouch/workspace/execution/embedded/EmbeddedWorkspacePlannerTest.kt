package com.trancong.dexworkspacetouch.workspace.execution.embedded

import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTarget
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedWorkspacePlannerTest {
    private val planner = EmbeddedWorkspacePlanner()

    @Test
    fun `preserves workspace identity component bounds source identity and order`() {
        val left = NormalizedBounds(0f, 0f, 0.5f, 1f)
        val plan = planner.plan(request(target("left", "com.maps", "MapsActivity", left, 0)))

        assertEquals("workspace-1", plan.workspaceId)
        assertEquals("Travel", plan.workspaceName)
        assertEquals(
            EmbeddedWorkspacePlanItem("left", "com.maps", "MapsActivity", left, 0),
            plan.items.single(),
        )
    }

    @Test
    fun `sorts plan items by existing target order`() {
        val plan = planner.plan(
            request(
                target("second", "com.second", "Second", NormalizedBounds.FullCanvas, 1),
                target("first", "com.first", "First", NormalizedBounds.FullCanvas, 0),
            ),
        )

        assertEquals(listOf("first", "second"), plan.items.map { it.sourceCellId })
        assertEquals(listOf(0, 1), plan.items.map { it.order })
    }

    @Test
    fun `keeps duplicate components as distinct source cells`() {
        val plan = planner.plan(
            request(
                target("left", "com.app", "Main", NormalizedBounds.FullCanvas, 0),
                target("right", "com.app", "Main", NormalizedBounds.FullCanvas, 1),
            ),
        )

        assertEquals(listOf("left", "right"), plan.items.map { it.sourceCellId })
        assertEquals(listOf("Main", "Main"), plan.items.map { it.componentName })
    }

    @Test
    fun `plan model graph contains no Android or embedded runtime types`() {
        val prohibited = listOf(
            "android.",
            "EmbeddedAppSession",
            "EmbeddedAppTarget",
            "VirtualDevice",
            "Surface",
            "WorkspaceLibraryItem",
        )
        val fieldTypes = listOf(
            EmbeddedWorkspacePlan::class.java,
            EmbeddedWorkspacePlanItem::class.java,
        ).flatMap { type -> type.declaredFields.map { it.type.name } }

        assertTrue(fieldTypes.none { fieldType -> prohibited.any(fieldType::contains) })
    }

    private fun request(vararg targets: AppLaunchTarget) = WorkspaceLaunchRequest(
        workspaceId = "workspace-1",
        workspaceName = "Travel",
        targets = targets.toList(),
    )

    private fun target(
        sourceCellId: String,
        packageName: String,
        componentName: String,
        bounds: NormalizedBounds,
        order: Int,
    ) = AppLaunchTarget(
        sourceCellId = sourceCellId,
        identity = AppIdentity(packageName, componentName),
        bounds = bounds,
        order = order,
    )
}
