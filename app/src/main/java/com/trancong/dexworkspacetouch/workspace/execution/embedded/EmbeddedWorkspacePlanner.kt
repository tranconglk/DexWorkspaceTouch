package com.trancong.dexworkspacetouch.workspace.execution.embedded

import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchRequest

class EmbeddedWorkspacePlanner {
    fun plan(request: WorkspaceLaunchRequest): EmbeddedWorkspacePlan = EmbeddedWorkspacePlan(
        workspaceId = request.workspaceId,
        workspaceName = request.workspaceName,
        items = request.targets
            .sortedBy { it.order }
            .map { target ->
                EmbeddedWorkspacePlanItem(
                    sourceCellId = target.sourceCellId,
                    packageName = target.identity.packageName,
                    componentName = requireNotNull(target.identity.activityName),
                    normalizedBounds = target.bounds,
                    order = target.order,
                )
            },
    )
}
