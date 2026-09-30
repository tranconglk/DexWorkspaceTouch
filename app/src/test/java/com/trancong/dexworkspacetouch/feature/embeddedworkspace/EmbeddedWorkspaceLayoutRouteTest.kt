package com.trancong.dexworkspacetouch.feature.embeddedworkspace

import com.trancong.dexworkspacetouch.golden.FakeWorkspaceRepository
import com.trancong.dexworkspacetouch.golden.FakeInstalledAppCatalog
import com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledApp
import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell
import com.trancong.dexworkspacetouch.workspace.launcher.WorkspaceLaunchRequestFactory
import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedWorkspaceLayoutRouteTest {
    private val catalog = FakeInstalledAppCatalog(listOf(
        InstalledApp("com.waze", "com.waze.Main", "Waze", true),
        InstalledApp("com.calc", "com.calc.Main", "Calculator", true),
    ))

    @Test fun loadsExactlySelectedWorkspaceAndRealPlan() = runTest {
        val other = workspace("other", .5f)
        val selected = workspace("selected", .625f)
        val repo = FakeWorkspaceRepository(listOf(other, selected))
        val result = EmbeddedWorkspaceLayoutLoader(repo, WorkspaceLaunchRequestFactory(catalog)).load("selected")
        val plan = (result as EmbeddedWorkspaceLayoutLoadResult.Ready).plan
        assertEquals("selected", plan.workspaceId)
        assertEquals(listOf("waze", "calc"), plan.items.map { it.sourceCellId })
        assertEquals(.625f, plan.items.first().normalizedBounds.right)
        assertEquals(.625f, plan.items.last().normalizedBounds.left)
    }

    @Test fun deletedWorkspaceDoesNotFallBackToAnotherRow() = runTest {
        val repo = FakeWorkspaceRepository(listOf(workspace("other", .625f)))
        val result = EmbeddedWorkspaceLayoutLoader(repo, WorkspaceLaunchRequestFactory(catalog)).load("deleted")
        assertEquals(EmbeddedWorkspaceLayoutLoadResult.MissingWorkspace("deleted"), result)
    }

    @Test fun unlaunchableWorkspaceReturnsTypedNotReady() = runTest {
        val repo = FakeWorkspaceRepository(listOf(workspace("selected", .625f)))
        val emptyCatalog = FakeInstalledAppCatalog(emptyList())
        val result = EmbeddedWorkspaceLayoutLoader(repo, WorkspaceLaunchRequestFactory(emptyCatalog)).load("selected")
        assertTrue(result is EmbeddedWorkspaceLayoutLoadResult.LaunchNotReady)
    }

    private fun workspace(id: String, split: Float) = Workspace(
        id, "Workspace $id",
        WorkspaceCanvas(listOf(
            WorkspaceCell("waze", NormalizedBounds(0f, 0f, split, 1f), AssignedApp("com.waze", "com.waze.Main", "Waze")),
            WorkspaceCell("calc", NormalizedBounds(split, 0f, 1f, 1f), AssignedApp("com.calc", "com.calc.Main", "Calculator")),
        )),
        modifiedSequence = 0, schemaVersion = 1, createdAtEpochMillis = 0, updatedAtEpochMillis = 0,
    )
}
