package com.trancong.dexworkspacetouch.feature.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CarActionTest {
    @Test
    fun validActions_preserveTheirValues() {
        assertEquals("com.example.maps", CarAction.LaunchApp("com.example.maps").packageName)
        assertEquals("maps://home", CarAction.OpenUri("maps://home").uri)
        assertEquals(750L, CarAction.Delay(750L).durationMillis)
        assertEquals("workspace-drive", CarAction.Workspace("workspace-drive").workspaceId)
    }

    @Test
    fun launchApp_rejectsBlankPackageName() {
        assertThrows(IllegalArgumentException::class.java) {
            CarAction.LaunchApp("   ")
        }
    }

    @Test
    fun openUri_rejectsBlankUri() {
        assertThrows(IllegalArgumentException::class.java) {
            CarAction.OpenUri("")
        }
    }

    @Test
    fun workspace_rejectsBlankWorkspaceId() {
        assertThrows(IllegalArgumentException::class.java) {
            CarAction.Workspace("\t")
        }
    }

    @Test
    fun delay_acceptsZeroAndRejectsNegativeDuration() {
        assertEquals(0L, CarAction.Delay(0L).durationMillis)
        assertThrows(IllegalArgumentException::class.java) {
            CarAction.Delay(-1L)
        }
    }

    @Test
    fun actions_haveValueEquality() {
        assertEquals(
            CarAction.LaunchApp("com.example.music"),
            CarAction.LaunchApp("com.example.music"),
        )
        assertEquals(CarAction.OpenUri("https://example.com"), CarAction.OpenUri("https://example.com"))
        assertEquals(CarAction.Delay(1_000L), CarAction.Delay(1_000L))
        assertEquals(CarAction.Workspace("parking"), CarAction.Workspace("parking"))
    }
}
