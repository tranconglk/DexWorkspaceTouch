package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlan
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlanItem
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.EmbeddedWorkspaceLayoutLoader
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.EmbeddedWorkspaceProofPlan
import com.trancong.dexworkspacetouch.golden.FakeInstalledAppCatalog
import com.trancong.dexworkspacetouch.golden.FakeWorkspaceRepository
import com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledApp
import com.trancong.dexworkspacetouch.workspace.launcher.WorkspaceLaunchRequestFactory
import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmbeddedWorkspaceEligibilityTest {
    @Test fun oneAndTwoTargetsRemainEligible() {
        assertNull(EmbeddedWorkspaceEligibility.evaluate(plan(1)))
        assertNull(EmbeddedWorkspaceEligibility.evaluate(plan(2)))
    }

    @Test fun threeTargetsAreBlockedByProductPolicy() {
        assertEquals(EmbeddedEligibilityFailure.UnsupportedEmbeddedItemCount(3),
            EmbeddedWorkspaceEligibility.evaluate(plan(3)))
    }

    @Test fun duplicateResolvedTargetIsRejected() {
        val items = plan(2).items
        val duplicate = items[1].copy(packageName = items[0].packageName, componentName = items[0].componentName)
        assertEquals(EmbeddedEligibilityFailure.DuplicateTarget("app0", "Main"),
            EmbeddedWorkspaceEligibility.evaluate(EmbeddedWorkspacePlan("ws", "Workspace", listOf(items[0], duplicate))))
    }

    @Test fun positiveOverlapIsRejected() {
        val items = plan(2).items
        val overlap = items[1].copy(normalizedBounds = NormalizedBounds(.4f, 0f, 1f, 1f))
        assertEquals(EmbeddedEligibilityFailure.UnsupportedLayout,
            EmbeddedWorkspaceEligibility.evaluate(EmbeddedWorkspacePlan("ws", "Workspace", listOf(items[0], overlap))))
    }

    @Test fun productLoaderUsesSelectedPersistedIdAndFrozenGeometry() = runTest {
        val repo = FakeWorkspaceRepository(listOf(workspace("other"), workspace("selected")))
        val catalog = FakeInstalledAppCatalog(listOf(InstalledApp("app0", "Main", "App", true)))
        val loader = EmbeddedWorkspaceProductLoader(EmbeddedWorkspaceLayoutLoader(repo, WorkspaceLaunchRequestFactory(catalog)))
        val ready = loader.loadEligible("selected", EmbeddedWorkspaceProofPlan.geometryPolicy) as EmbeddedEligibilityResult.Ready
        assertEquals("selected", ready.snapshot.planSnapshot.workspaceId)
        assertEquals(900, ready.snapshot.geometryFor("cell0").width)
        assertEquals(EmbeddedEligibilityResult.Rejected(EmbeddedEligibilityFailure.MissingWorkspace("deleted")),
            loader.loadEligible("deleted", EmbeddedWorkspaceProofPlan.geometryPolicy))
    }

    private fun workspace(id: String) = Workspace(id, "Workspace $id",
        WorkspaceCanvas(listOf(WorkspaceCell("cell0", NormalizedBounds(0f, 0f, 1f, 1f),
            AssignedApp("app0", "Main", "App")))),
        modifiedSequence = 0, schemaVersion = 1, createdAtEpochMillis = 0, updatedAtEpochMillis = 0)

    private fun plan(count: Int) = EmbeddedWorkspacePlan("ws", "Workspace", (0 until count).map { i ->
        EmbeddedWorkspacePlanItem("cell$i", "app$i", "Main",
            NormalizedBounds(i.toFloat() / count, 0f, (i + 1).toFloat() / count, 1f), i)
    })
}
