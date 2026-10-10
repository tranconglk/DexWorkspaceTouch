package com.trancong.dexworkspacetouch.feature.physicalbutton

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PhysicalButtonPreferencesTest {
    @Test fun newConfiguration_isDisabledAndUnconfigured() {
        val preferences = PhysicalButtonPreferences(MemoryStorage())
        assertFalse(preferences.configuration.value.enabled)
        assertNull(preferences.configuration.value.workspaceId)
    }
    @Test fun configuration_survivesRecreationAndDisablingKeepsWorkspace() {
        val storage = MemoryStorage()
        val preferences = PhysicalButtonPreferences(storage)
        preferences.setWorkspace("chosen-workspace")
        preferences.setEnabled(true)
        assertEquals(PhysicalButtonConfiguration(true, "chosen-workspace"),
            PhysicalButtonPreferences(storage).configuration.value)
        preferences.setEnabled(false)
        assertEquals(PhysicalButtonConfiguration(false, "chosen-workspace"),
            PhysicalButtonPreferences(storage).configuration.value)
    }
    @Test fun clearingWorkspace_clearsPersistedSelection() {
        val storage = MemoryStorage(PhysicalButtonConfiguration(true, "chosen-workspace"))
        val preferences = PhysicalButtonPreferences(storage)
        preferences.setWorkspace(null)
        assertNull(PhysicalButtonPreferences(storage).configuration.value.workspaceId)
    }
    @Test fun blankWorkspace_isUnconfigured() {
        val preferences = PhysicalButtonPreferences(MemoryStorage())
        preferences.setWorkspace("  ")
        assertNull(preferences.configuration.value.workspaceId)
    }
    private class MemoryStorage(var value: PhysicalButtonConfiguration = PhysicalButtonConfiguration()) : PhysicalButtonStorage {
        override fun read() = value
        override fun write(configuration: PhysicalButtonConfiguration) { value = configuration }
    }
}
