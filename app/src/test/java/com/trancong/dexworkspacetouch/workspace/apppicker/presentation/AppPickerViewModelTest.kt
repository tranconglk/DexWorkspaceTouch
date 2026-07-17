package com.trancong.dexworkspacetouch.workspace.apppicker.presentation

import com.trancong.dexworkspacetouch.workspace.apppicker.model.DefaultInstalledAppCatalog
import com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledApp
import com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledAppDataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPickerViewModelTest {
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
        val viewModel = AppPickerViewModel(catalog(emptyList()))

        assertTrue(viewModel.state.apps.isEmpty())
        assertFalse(viewModel.state.loadFailed)
    }

    @Test fun dataSourceExceptionProducesReadableErrorState() {
        val viewModel = AppPickerViewModel(
            DefaultInstalledAppCatalog(FakeInstalledAppDataSource(failure = IllegalStateException("broken"))),
        )

        assertTrue(viewModel.state.apps.isEmpty())
        assertTrue(viewModel.state.loadFailed)
    }

    private fun catalog(apps: List<InstalledApp>) =
        DefaultInstalledAppCatalog(FakeInstalledAppDataSource(apps))

    private fun app(
        packageName: String,
        label: String,
        activityName: String = "$packageName.MainActivity",
        isSystemApp: Boolean = false,
    ) = InstalledApp(packageName, activityName, label, true, isSystemApp)
}

private class FakeInstalledAppDataSource(
    private val apps: List<InstalledApp> = emptyList(),
    private val failure: RuntimeException? = null,
) : InstalledAppDataSource {
    override fun getInstalledApps(): List<InstalledApp> {
        failure?.let { throw it }
        return apps
    }
}
