package com.trancong.dexworkspacetouch.workspace.apppicker.model

import java.util.Locale

interface InstalledAppCatalog {
    fun getApps(): List<InstalledApp>

    fun findByIdentity(identity: AppIdentity): InstalledApp?
}

class ListInstalledAppCatalog(apps: List<InstalledApp>) : InstalledAppCatalog {
    private val installedApps = apps.toList()

    init {
        require(installedApps.map(InstalledApp::identity).distinct().size == installedApps.size) {
            "Installed app identities must be unique"
        }
    }

    override fun getApps(): List<InstalledApp> = installedApps

    override fun findByIdentity(identity: AppIdentity): InstalledApp? =
        installedApps.firstOrNull { it.identity == identity }
}

class DefaultInstalledAppCatalog(
    private val dataSource: InstalledAppDataSource,
) : InstalledAppCatalog {
    override fun getApps(): List<InstalledApp> = dataSource.getInstalledApps()
        .asSequence()
        .filter { it.launchable && it.activityName != null }
        .distinctBy(InstalledApp::identity)
        .sortedWith(
            compareBy<InstalledApp>(
                { it.label.lowercase(Locale.ROOT) },
                { it.packageName.lowercase(Locale.ROOT) },
                { it.activityName?.lowercase(Locale.ROOT).orEmpty() },
            ),
        )
        .toList()

    override fun findByIdentity(identity: AppIdentity): InstalledApp? =
        getApps().firstOrNull { it.identity == identity }
}

fun InstalledAppCatalog.search(query: String): List<InstalledApp> {
    val term = query.trim()
    if (term.isEmpty()) return getApps()
    return getApps().filter { app ->
        app.label.contains(term, ignoreCase = true) ||
            app.packageName.contains(term, ignoreCase = true)
    }
}
