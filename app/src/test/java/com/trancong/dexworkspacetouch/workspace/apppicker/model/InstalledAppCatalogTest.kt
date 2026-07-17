package com.trancong.dexworkspacetouch.workspace.apppicker.model

import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class InstalledAppCatalogTest {
    @Test fun catalogPreservesDeterministicOrderAndFindsExactIdentity() {
        val catalog = ListInstalledAppCatalog(listOf(firstActivity, secondActivity))

        assertEquals(listOf(firstActivity, secondActivity), catalog.getApps())
        assertSame(firstActivity, catalog.findByIdentity(firstActivity.identity))
        assertSame(secondActivity, catalog.findByIdentity(secondActivity.identity))
    }

    @Test fun missingOrDifferentActivityReturnsNull() {
        val catalog = ListInstalledAppCatalog(listOf(firstActivity))

        assertNull(catalog.findByIdentity(AppIdentity("missing", null)))
        assertNull(catalog.findByIdentity(secondActivity.identity))
    }

    @Test fun duplicateIdentityIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ListInstalledAppCatalog(listOf(firstActivity, firstActivity.copy(label = "Other label")))
        }
    }

    @Test fun mapperPreservesIdentityWithTemporaryPresentationBridge() {
        val assigned = firstActivity.toAssignedApp()

        assertEquals(firstActivity.packageName, assigned.packageName)
        assertEquals(firstActivity.activityName, assigned.activityName)
        assertEquals(firstActivity.label, assigned.label)
    }

    @Test fun resolverFindsExactActivityAndReturnsNullForMissingApp() {
        val catalog = ListInstalledAppCatalog(listOf(firstActivity, secondActivity))

        assertSame(firstActivity, resolveAssignedApp(firstActivity.toAssignedApp(), catalog))
        assertSame(secondActivity, resolveAssignedApp(secondActivity.toAssignedApp(), catalog))
        assertNull(
            resolveAssignedApp(
                AssignedApp("com.example", "com.example.Missing", "Stale label"),
                catalog,
            ),
        )
    }

    @Test fun catalogSearchMatchesLabelPackageAndMissingValues() {
        val catalog = ListInstalledAppCatalog(
            listOf(
                InstalledApp("com.google.android.apps.maps", "MapsActivity", "Google Maps", true),
                InstalledApp("org.videolan.vlc", "StartActivity", "VLC", true),
            ),
        )
        assertEquals(
            listOf("Google Maps"),
            catalog.search("gOoGlE mApS").map(InstalledApp::label),
        )
        assertEquals(
            listOf("VLC"),
            catalog.search("videolan").map(InstalledApp::label),
        )
        assertEquals(emptyList<InstalledApp>(), catalog.search("not-installed-demo"))
    }

    private val firstActivity = InstalledApp(
        packageName = "com.example",
        activityName = "com.example.First",
        label = "First",
        launchable = true,
    )
    private val secondActivity = InstalledApp(
        packageName = "com.example",
        activityName = "com.example.Second",
        label = "Second",
        launchable = true,
    )
}
