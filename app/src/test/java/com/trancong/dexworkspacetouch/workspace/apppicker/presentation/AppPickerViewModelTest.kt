package com.trancong.dexworkspacetouch.workspace.apppicker.presentation

import com.trancong.dexworkspacetouch.workspace.apppicker.model.DefaultInstalledAppCatalog
import com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledApp
import com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledAppDataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class AppPickerViewModelTest {
    @Test fun allFilterReturnsEveryApp() {
        val viewModel = loadedViewModel(sampleApps)
        assertEquals(sampleApps.size, viewModel.filteredApps.size)
    }

    @Test fun userAndSystemFiltersUseSystemFlag() {
        val viewModel = loadedViewModel(sampleApps)

        viewModel.updateFilter(AppFilter.USER)
        assertEquals(listOf("Maps", "Notes"), viewModel.filteredApps.map(InstalledApp::label))

        viewModel.updateFilter(AppFilter.SYSTEM)
        assertEquals(listOf("Settings"), viewModel.filteredApps.map(InstalledApp::label))
    }

    @Test fun searchMatchesLabelAndPackageIgnoringCaseAndTrimsQuery() {
        val viewModel = loadedViewModel(sampleApps)

        viewModel.updateQuery("  mApS ")
        assertEquals(listOf("Maps"), viewModel.filteredApps.map(InstalledApp::label))

        viewModel.updateQuery("example.notes")
        assertEquals(listOf("Notes"), viewModel.filteredApps.map(InstalledApp::label))
    }

    @Test fun searchAndFilterAreCombinedWithoutChangingSourceOrder() {
        val viewModel = loadedViewModel(sampleApps)
        viewModel.updateFilter(AppFilter.SYSTEM)
        viewModel.updateQuery("set")

        assertEquals(listOf("Settings"), viewModel.filteredApps.map(InstalledApp::label))
        assertEquals(3, viewModel.state.apps.size)
    }

    @Test fun retryRecoversAfterDataSourceError() {
        val source = FakeInstalledAppDataSource(
            apps = sampleApps,
            failure = IllegalStateException("broken"),
        )
        val viewModel = AppPickerViewModel(DefaultInstalledAppCatalog(source))
        runBlocking { viewModel.loadApps() }
        assertTrue(viewModel.state.loadFailed)

        source.failure = null
        runBlocking { viewModel.retry() }

        assertFalse(viewModel.state.loadFailed)
        assertEquals(3, viewModel.state.apps.size)
    }

    @Test fun selectedIdentityMatchesOnlyCurrentAssignment() {
        val selected = sampleApps.first().identity
        val viewModel = loadedViewModel(sampleApps, selected)

        assertTrue(viewModel.isSelected(sampleApps.first()))
        assertFalse(viewModel.isSelected(sampleApps.last()))
    }

    @Test fun catalogSortsByLabelIgnoringCaseThenPackage() {
        val apps = listOf(
            app("com.z", "beta"),
            app("com.b", "Alpha"),
            app("com.a", "alpha"),
        )

        assertEquals(
            listOf("com.a", "com.b", "com.z"),
            catalog(apps).getApps().map(InstalledApp::packageName),
        )
    }

    @Test fun catalogKeepsOnlyLaunchableApps() {
        val launchable = app("com.launchable", "Launchable")
        val notLaunchable = InstalledApp("com.hidden", null, "Hidden", false)

        assertEquals(listOf(launchable), catalog(listOf(notLaunchable, launchable)).getApps())
    }

    @Test fun systemFlagIsPreservedForFutureFiltering() {
        val systemApp = app("com.system", "System", isSystemApp = true)

        assertTrue(catalog(listOf(systemApp)).getApps().single().isSystemApp)
    }

    @Test fun duplicateIdentityIsCollapsedButDifferentActivitiesRemain() {
        val first = app("com.example", "First", "FirstActivity")
        val duplicate = first.copy(label = "Duplicate")
        val secondActivity = app("com.example", "Second", "SecondActivity")

        assertEquals(
            listOf(first, secondActivity),
            catalog(listOf(first, duplicate, secondActivity)).getApps(),
        )
    }

    @Test fun emptyDataSourceProducesValidEmptyState() {
        val viewModel = loadedViewModel(emptyList())

        assertTrue(viewModel.state.apps.isEmpty())
        assertFalse(viewModel.state.loadFailed)
    }

    @Test fun dataSourceExceptionProducesReadableErrorState() {
        val viewModel = AppPickerViewModel(
            DefaultInstalledAppCatalog(FakeInstalledAppDataSource(failure = IllegalStateException("broken"))),
        )
        runBlocking { viewModel.loadApps() }

        assertTrue(viewModel.state.apps.isEmpty())
        assertTrue(viewModel.state.loadFailed)
    }

    private fun catalog(apps: List<InstalledApp>) =
        DefaultInstalledAppCatalog(FakeInstalledAppDataSource(apps))

    private fun loadedViewModel(
        apps: List<InstalledApp>,
        selectedIdentity: com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity? = null,
    ) = AppPickerViewModel(catalog(apps), selectedIdentity = selectedIdentity).also {
        runBlocking { it.loadApps() }
    }

    private fun app(
        packageName: String,
        label: String,
        activityName: String = "$packageName.MainActivity",
        isSystemApp: Boolean = false,
    ) = InstalledApp(packageName, activityName, label, true, isSystemApp)

    private val sampleApps = listOf(
        app("com.example.maps", "Maps"),
        app("com.example.notes", "Notes"),
        app("com.android.settings", "Settings", isSystemApp = true),
    )
}

private class FakeInstalledAppDataSource(
    private val apps: List<InstalledApp> = emptyList(),
    var failure: RuntimeException? = null,
) : InstalledAppDataSource {
    override fun getInstalledApps(): List<InstalledApp> {
        failure?.let { throw it }
        return apps
    }
}
