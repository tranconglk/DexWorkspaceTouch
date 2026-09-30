package com.trancong.dexworkspacetouch.feature.embeddedworkspace

import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppGeometry
import com.trancong.dexworkspacetouch.feature.embeddedcalculator.CALCULATOR_EMBEDDED_TARGET
import com.trancong.dexworkspacetouch.feature.embeddedwaze.WAZE_EMBEDDED_TARGET
import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedGeometryPolicy
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTarget
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchRequest

object EmbeddedWorkspaceProofPlan {
    const val WAZE_SOURCE_ID = "proof-waze"
    const val CALCULATOR_SOURCE_ID = "proof-calculator"

    val proofGeometry = EmbeddedAppGeometry(width = 900, height = 675, densityDpi = 320)

    val geometryPolicy = EmbeddedGeometryPolicy { proofGeometry }

    fun createRequest(): WorkspaceLaunchRequest = WorkspaceLaunchRequest(
        workspaceId = "embedded-workspace-runner-proof",
        workspaceName = "Embedded Workspace Runner Proof",
        targets = listOf(
            AppLaunchTarget(
                sourceCellId = WAZE_SOURCE_ID,
                identity = AppIdentity(
                    packageName = WAZE_EMBEDDED_TARGET.packageName,
                    activityName = WAZE_EMBEDDED_TARGET.componentName,
                ),
                bounds = NormalizedBounds(left = 0f, top = 0f, right = 0.5f, bottom = 1f),
                order = 0,
            ),
            AppLaunchTarget(
                sourceCellId = CALCULATOR_SOURCE_ID,
                identity = AppIdentity(
                    packageName = CALCULATOR_EMBEDDED_TARGET.packageName,
                    activityName = CALCULATOR_EMBEDDED_TARGET.componentName,
                ),
                bounds = NormalizedBounds(left = 0.5f, top = 0f, right = 1f, bottom = 1f),
                order = 1,
            ),
        ),
    )
}
