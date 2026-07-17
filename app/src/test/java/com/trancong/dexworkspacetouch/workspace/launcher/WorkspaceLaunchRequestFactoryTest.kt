package com.trancong.dexworkspacetouch.workspace.launcher

import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledApp
import com.trancong.dexworkspacetouch.workspace.apppicker.model.ListInstalledAppCatalog
import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell
import com.trancong.dexworkspacetouch.workspace.launcher.model.LaunchReadiness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceLaunchRequestFactoryTest {
    @Test
    fun `valid workspace creates deterministic request and preserves bounds`() {
        val top = NormalizedBounds(0f, 0f, 1f, 0.5f)
        val bottom = NormalizedBounds(0f, 0.5f, 1f, 1f)
        val canvas = canvas(
            cell("top", top, assigned("com.maps", "MapsActivity", "Old Maps label")),
            cell("bottom", bottom, assigned("com.notes", "NotesActivity", "Old Notes label")),
        )

        val result = factory(
            installed("com.notes", "NotesActivity", "Notes"),
            installed("com.maps", "MapsActivity", "Maps"),
        ).create("workspace-7", "Travel", canvas) as LaunchReadiness.Ready

        assertEquals("workspace-7", result.request.workspaceId)
        assertEquals("Travel", result.request.workspaceName)
        assertEquals(listOf(0, 1), result.request.targets.map { it.order })
        assertEquals(listOf(top, bottom), result.request.targets.map { it.bounds })
        assertEquals(
            listOf(AppIdentity("com.maps", "MapsActivity"), AppIdentity("com.notes", "NotesActivity")),
            result.request.targets.map { it.identity },
        )
        assertFalse(result.request.targets.first()::class.java.declaredFields.any { it.name == "label" })
    }

    @Test
    fun `empty canvas returns EmptyWorkspace`() {
        assertSame(
            LaunchReadiness.EmptyWorkspace,
            factory().create("workspace", "Name", WorkspaceCanvas(emptyList())),
        )
    }

    @Test
    fun `empty cells are reported in cell order`() {
        val result = factory().create(
            "workspace",
            "Name",
            canvas(
                cell("left", NormalizedBounds(0f, 0f, 0.5f, 1f)),
                cell("right", NormalizedBounds(0.5f, 0f, 1f, 1f)),
            ),
        ) as LaunchReadiness.EmptyCells

        assertEquals(listOf("left", "right"), result.cellIds)
    }

    @Test
    fun `missing app returns MissingApplications`() {
        val result = factory().create(
            "workspace",
            "Name",
            canvas(cell("cell", NormalizedBounds.FullCanvas, assigned("missing", "Main", "Missing"))),
        ) as LaunchReadiness.MissingApplications

        assertEquals("cell", result.items.single().cellId)
        assertEquals(AppIdentity("missing", "Main"), result.items.single().identity)
    }

    @Test
    fun `non launchable app returns NonLaunchableApplications`() {
        val app = InstalledApp("com.app", "Main", "App", launchable = false)
        val result = factory(app).create(
            "workspace",
            "Name",
            canvas(cell("cell", NormalizedBounds.FullCanvas, assigned("com.app", "Main", "App"))),
        ) as LaunchReadiness.NonLaunchableApplications

        assertEquals(AppIdentity("com.app", "Main"), result.items.single().identity)
    }

    @Test
    fun `null assignment activity resolves deterministic launcher activity from catalog`() {
        val result = factory(
            installed("com.app", "ZActivity", "App"),
            installed("com.app", "AActivity", "App"),
        ).create(
            "workspace",
            "Name",
            canvas(cell("cell", NormalizedBounds.FullCanvas, assigned("com.app", null, "App"))),
        ) as LaunchReadiness.Ready

        assertEquals(AppIdentity("com.app", "AActivity"), result.request.targets.single().identity)
    }

    @Test
    fun `null activity in assignment and catalog is non launchable`() {
        val catalogApp = InstalledApp("com.app", null, "App", launchable = false)
        val result = factory(catalogApp).create(
            "workspace",
            "Name",
            canvas(cell("cell", NormalizedBounds.FullCanvas, assigned("com.app", null, "App"))),
        )

        assertTrue(result is LaunchReadiness.NonLaunchableApplications)
    }

    @Test
    fun `invalid overlapping canvas returns InvalidCanvas`() {
        val app = assigned("com.app", "Main", "App")
        val result = factory(installed("com.app", "Main", "App")).create(
            "workspace",
            "Name",
            canvas(
                cell("one", NormalizedBounds.FullCanvas, app),
                cell("two", NormalizedBounds.FullCanvas, app),
            ),
        ) as LaunchReadiness.InvalidCanvas

        assertTrue(result.issues.isNotEmpty())
    }

    @Test
    fun `duplicate app identity is retained for every cell`() {
        val identity = AppIdentity("com.app", "Main")
        val assigned = assigned(identity.packageName, identity.activityName, "App")
        val result = factory(installed("com.app", "Main", "App")).create(
            "workspace",
            "Name",
            canvas(
                cell("left", NormalizedBounds(0f, 0f, 0.5f, 1f), assigned),
                cell("right", NormalizedBounds(0.5f, 0f, 1f, 1f), assigned),
            ),
        ) as LaunchReadiness.Ready

        assertEquals(listOf(identity, identity), result.request.targets.map { it.identity })
    }

    @Test
    fun `launch request model graph contains no Android types`() {
        val modelClasses = listOf(
            com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchRequest::class.java,
            com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTarget::class.java,
            LaunchReadiness::class.java,
        )

        assertTrue(
            modelClasses.flatMap { it.declaredFields.toList() }
                .none { it.type.name.startsWith("android.") },
        )
    }

    @Test fun `five targets are ready`() {
        val app = assigned("com.app", "Main", "App")
        val result = factory(installed("com.app", "Main", "App")).create(
            "workspace", "Five", stripedCanvas(5, app),
        )

        assertTrue(result is LaunchReadiness.Ready)
        assertEquals(5, (result as LaunchReadiness.Ready).request.targets.size)
    }

    @Test fun `six targets return typed limit failure before launch`() {
        val app = assigned("com.app", "Main", "App")
        val result = factory(installed("com.app", "Main", "App")).create(
            "workspace", "Six", stripedCanvas(6, app),
        )

        assertEquals(LaunchReadiness.TooManyTargets(actual = 6, maximum = 5), result)
    }

    private fun factory(vararg apps: InstalledApp) =
        WorkspaceLaunchRequestFactory(ListInstalledAppCatalog(apps.toList()))

    private fun installed(packageName: String, activityName: String, label: String) =
        InstalledApp(packageName, activityName, label, launchable = true)

    private fun assigned(packageName: String, activityName: String?, label: String) =
        AssignedApp(packageName, activityName, label)

    private fun cell(
        id: String,
        bounds: NormalizedBounds,
        app: AssignedApp? = null,
    ) = WorkspaceCell(id, bounds, app)

    private fun canvas(vararg cells: WorkspaceCell) = WorkspaceCanvas(cells.toList())

    private fun stripedCanvas(count: Int, app: AssignedApp) = WorkspaceCanvas(
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
