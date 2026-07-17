package com.trancong.dexworkspacetouch.workspace.apppicker.model

import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp

fun InstalledApp.toAssignedApp(): AssignedApp = AssignedApp(
    packageName = packageName,
    activityName = activityName,
    // Temporary presentation bridge until E3-003 resolves labels from the catalog.
    label = label,
)

fun AssignedApp.toIdentity(): AppIdentity = AppIdentity(packageName, activityName)

fun resolveAssignedApp(
    assignedApp: AssignedApp,
    catalog: InstalledAppCatalog,
): InstalledApp? = catalog.findByIdentity(assignedApp.toIdentity())
