package com.trancong.dexworkspacetouch.feature.embeddedworkspace

import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppGeometry
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlan
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlanner
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedWorkspaceProofPlanTest {
    @Test
    fun `proof request is real launch request and real planner preserves Waze Calculator order`() {
        val request: WorkspaceLaunchRequest = EmbeddedWorkspaceProofPlan.createRequest()
        val plan: EmbeddedWorkspacePlan = EmbeddedWorkspacePlanner().plan(request)

        assertEquals(2, request.targets.size)
        assertEquals(
            listOf(EmbeddedWorkspaceProofPlan.WAZE_SOURCE_ID, EmbeddedWorkspaceProofPlan.CALCULATOR_SOURCE_ID),
            plan.items.map { it.sourceCellId },
        )
        assertEquals(
            listOf(
                "com.waze" to "com.waze.FreeMapAppActivity",
                "com.sec.android.app.popupcalculator" to "com.sec.android.app.popupcalculator.Calculator",
            ),
            plan.items.map { it.packageName to it.componentName },
        )
        assertEquals(listOf(0, 1), plan.items.map { it.order })
        assertNotEquals(plan.items[0].sourceCellId, plan.items[1].sourceCellId)
        assertTrue(plan.items.all { it.normalizedBounds.width > 0f && it.normalizedBounds.height > 0f })
    }

    @Test
    fun `proof geometry is fixed and independent from normalized bounds`() {
        val planner = EmbeddedWorkspacePlanner()
        val plan = planner.plan(EmbeddedWorkspaceProofPlan.createRequest())
        val policy = EmbeddedWorkspaceProofPlan.geometryPolicy

        assertNotEquals(plan.items[0].normalizedBounds, plan.items[1].normalizedBounds)
        assertEquals(EmbeddedAppGeometry(900, 675, 320), policy.geometryFor(plan.items[0]))
        assertEquals(EmbeddedAppGeometry(900, 675, 320), policy.geometryFor(plan.items[1]))
    }
}
