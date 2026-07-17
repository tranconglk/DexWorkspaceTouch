package com.trancong.dexworkspacetouch.workspace.launcher

import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledApp
import com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledAppCatalog
import com.trancong.dexworkspacetouch.workspace.apppicker.model.toIdentity
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvasValidator
import com.trancong.dexworkspacetouch.workspace.designer.model.CanvasValidationIssue
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceLimits
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTarget
import com.trancong.dexworkspacetouch.workspace.launcher.model.LaunchApplicationIssue
import com.trancong.dexworkspacetouch.workspace.launcher.model.LaunchReadiness
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchRequest
import java.util.Locale

class WorkspaceLaunchRequestFactory(
    private val installedAppCatalog: InstalledAppCatalog,
    private val canvasValidator: WorkspaceCanvasValidator = WorkspaceCanvasValidator(),
) {
    fun create(
        workspaceId: String,
        workspaceName: String,
        canvas: WorkspaceCanvas,
    ): LaunchReadiness {
        val validationIssues = canvasValidator.validate(canvas)
        if (validationIssues == listOf(CanvasValidationIssue.EmptyCanvas)) {
            return LaunchReadiness.EmptyWorkspace
        }
        if (validationIssues.isNotEmpty()) return LaunchReadiness.InvalidCanvas(validationIssues)
        if (canvas.cells.size > WorkspaceLimits.MaxCells) {
            return LaunchReadiness.TooManyTargets(canvas.cells.size, WorkspaceLimits.MaxCells)
        }

        val emptyCellIds = canvas.cells.filter { it.app == null }.map { it.id }
        if (emptyCellIds.isNotEmpty()) return LaunchReadiness.EmptyCells(emptyCellIds)

        val installedApps = installedAppCatalog.getApps()
        val missing = mutableListOf<LaunchApplicationIssue>()
        val nonLaunchable = mutableListOf<LaunchApplicationIssue>()
        val resolvedIdentities = mutableListOf<AppIdentity>()

        canvas.cells.forEach { cell ->
            val assignedIdentity = requireNotNull(cell.app).toIdentity()
            val resolved = resolve(assignedIdentity, installedApps)
            when {
                resolved == null -> missing += LaunchApplicationIssue(cell.id, assignedIdentity)
                !resolved.launchable || resolved.activityName == null -> {
                    nonLaunchable += LaunchApplicationIssue(cell.id, assignedIdentity)
                }
                else -> resolvedIdentities += resolved.identity
            }
        }

        if (missing.isNotEmpty()) return LaunchReadiness.MissingApplications(missing)
        if (nonLaunchable.isNotEmpty()) {
            return LaunchReadiness.NonLaunchableApplications(nonLaunchable)
        }

        val targets = canvas.cells.mapIndexed { index, cell ->
            AppLaunchTarget(
                identity = resolvedIdentities[index],
                bounds = cell.bounds,
                order = index,
            )
        }
        return LaunchReadiness.Ready(
            WorkspaceLaunchRequest(
                workspaceId = workspaceId,
                workspaceName = workspaceName,
                targets = targets,
            ),
        )
    }

    private fun resolve(identity: AppIdentity, apps: List<InstalledApp>): InstalledApp? {
        if (identity.activityName != null) return apps.firstOrNull { it.identity == identity }

        val packageMatches = apps.filter { it.packageName == identity.packageName }
        return packageMatches
            .filter { it.launchable && it.activityName != null }
            .minWithOrNull(
                compareBy<InstalledApp>(
                    { it.activityName.orEmpty().lowercase(Locale.ROOT) },
                    { it.activityName.orEmpty() },
                ),
            )
            ?: packageMatches.firstOrNull()
    }
}
