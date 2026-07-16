package com.trancong.dexworkspacetouch.workspace.apppicker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoAppsTest {
    @Test fun searchIsCaseInsensitive() {
        assertEquals(listOf("Google Maps"), DemoApps.search("gOoGlE mApS").map { it.label })
    }

    @Test fun searchMatchesLabel() {
        assertEquals(listOf("Spotify"), DemoApps.search("Spotify").map { it.label })
    }

    @Test fun searchMatchesPackageName() {
        assertEquals(listOf("VLC"), DemoApps.search("videolan").map { it.label })
    }

    @Test fun searchWithNoMatchReturnsEmptyList() {
        assertTrue(DemoApps.search("not-installed-demo").isEmpty())
    }
}
