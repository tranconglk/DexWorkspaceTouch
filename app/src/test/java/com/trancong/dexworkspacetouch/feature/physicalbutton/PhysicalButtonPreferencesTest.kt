package com.trancong.dexworkspacetouch.feature.physicalbutton

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalButtonPreferencesTest {
    @Test fun newConfiguration_isDisabledAndUnconfigured() {
        val preferences = PhysicalButtonPreferences(MemoryStorage())
        assertFalse(preferences.configuration.value.enabled)
        assertNull(preferences.configuration.value.volumeUpWorkspaceId)
        assertNull(preferences.configuration.value.volumeDownWorkspaceId)
        assertNull(preferences.configuration.value.deviceDescriptor)
        assertFalse(preferences.bindingRequested.value)
    }
    @Test fun independentMappings_surviveRecreationAndCanBeClearedSeparately() {
        val storage = MemoryStorage()
        val preferences = PhysicalButtonPreferences(storage)
        preferences.setVolumeUpWorkspace("up-id")
        preferences.setVolumeDownWorkspace("down-id")
        preferences.setEnabled(true)
        preferences.beginBinding()
        preferences.captureRemote("remote-descriptor", "AB Shutter3")
        preferences.setEnabled(false)
        assertEquals(PhysicalButtonConfiguration(false, "up-id", "down-id", "remote-descriptor", "AB Shutter3"),
            PhysicalButtonPreferences(storage).configuration.value)
        preferences.setVolumeUpWorkspace(null)
        assertEquals("down-id", PhysicalButtonPreferences(storage).configuration.value.volumeDownWorkspaceId)
        preferences.setVolumeUpWorkspace("up-again")
        preferences.setVolumeDownWorkspace("  ")
        assertEquals("up-again", PhysicalButtonPreferences(storage).configuration.value.volumeUpWorkspaceId)
        assertNull(PhysicalButtonPreferences(storage).configuration.value.volumeDownWorkspaceId)
    }

    @Test fun clearAndRebind_preserveBothMappings() {
        val storage = MemoryStorage(PhysicalButtonConfiguration(true, "up-id", "down-id", "old-remote", "AB Shutter3"))
        val preferences = PhysicalButtonPreferences(storage)
        preferences.clearRemoteBinding()
        assertEquals(PhysicalButtonConfiguration(true, "up-id", "down-id"), storage.value)
        preferences.beginBinding()
        preferences.captureRemote("new-remote", "AB Shutter3")
        assertEquals(PhysicalButtonConfiguration(true, "up-id", "down-id", "new-remote", "AB Shutter3"), storage.value)
    }
    @Test fun configuration_survivesRecreationAndDisablingKeepsWorkspace() {
        val storage = MemoryStorage()
        val preferences = PhysicalButtonPreferences(storage)
        preferences.setVolumeUpWorkspace("chosen-workspace")
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
        preferences.setVolumeUpWorkspace(null)
        assertNull(PhysicalButtonPreferences(storage).configuration.value.volumeUpWorkspaceId)
    }
    @Test fun blankWorkspace_isUnconfigured() {
        val preferences = PhysicalButtonPreferences(MemoryStorage())
        preferences.setVolumeUpWorkspace("  ")
        assertNull(preferences.configuration.value.volumeUpWorkspaceId)
    }

    @Test fun successfulBinding_persistsDescriptorAndDisplayNameWithoutChangingWorkspace() {
        val storage = MemoryStorage(PhysicalButtonConfiguration(true, "chosen-workspace"))
        val preferences = PhysicalButtonPreferences(storage)
        preferences.beginBinding()
        assertTrue(preferences.captureRemote("remote-descriptor", "AB Shutter3"))
        assertFalse(preferences.bindingRequested.value)
        assertEquals(PhysicalButtonConfiguration(true, "chosen-workspace", null, "remote-descriptor", "AB Shutter3"),
            PhysicalButtonPreferences(storage).configuration.value)
        preferences.setEnabled(false)
        assertEquals("remote-descriptor", PhysicalButtonPreferences(storage).configuration.value.deviceDescriptor)
    }

    @Test fun bindingRequiresExplicitRequest() {
        val storage = MemoryStorage()
        val preferences = PhysicalButtonPreferences(storage)
        assertFalse(preferences.captureRemote("remote-descriptor", "AB Shutter3"))
        assertNull(storage.value.deviceDescriptor)
        assertEquals(0, storage.writes)
    }

    @Test fun missingAndBlankDescriptor_doNotFinishBindingOrPersistAnything() {
        val storage = MemoryStorage()
        val preferences = PhysicalButtonPreferences(storage)
        preferences.beginBinding()
        assertFalse(preferences.captureRemote(null, "AB Shutter3"))
        assertFalse(preferences.captureRemote("  ", "AB Shutter3"))
        assertTrue(preferences.bindingRequested.value)
        assertEquals(0, storage.writes)
    }

    @Test fun bindingRequest_isNotPersistedAndRecreationDoesNotResumeLearning() {
        val storage = MemoryStorage()
        val preferences = PhysicalButtonPreferences(storage)
        preferences.beginBinding()
        assertTrue(preferences.bindingRequested.value)
        assertFalse(PhysicalButtonPreferences(storage).bindingRequested.value)
        preferences.cancelBinding()
        assertFalse(preferences.bindingRequested.value)
        assertEquals(0, storage.writes)
    }

    @Test fun cancellingRebind_keepsExistingBinding() {
        val stored = PhysicalButtonConfiguration(true, "chosen-workspace", null, "old-remote", "Old remote")
        val storage = MemoryStorage(stored)
        val preferences = PhysicalButtonPreferences(storage)
        preferences.beginBinding()
        preferences.cancelBinding()
        assertEquals(stored, PhysicalButtonPreferences(storage).configuration.value)
    }

    @Test fun rebind_replacesDescriptorWithoutUsingDeviceId() {
        val storage = MemoryStorage(PhysicalButtonConfiguration(true, "chosen-workspace", null, "old-remote", "Old remote"))
        val preferences = PhysicalButtonPreferences(storage)
        preferences.beginBinding()
        assertTrue(preferences.captureRemote("new-remote", "AB Shutter3"))
        assertEquals(PhysicalButtonConfiguration(true, "chosen-workspace", null, "new-remote", "AB Shutter3"), storage.value)
    }

    @Test fun missingDisplayName_doesNotInvalidateDescriptorBinding() {
        val storage = MemoryStorage()
        val preferences = PhysicalButtonPreferences(storage)
        preferences.beginBinding()
        assertTrue(preferences.captureRemote("remote-descriptor", "  "))
        assertEquals("remote-descriptor", storage.value.deviceDescriptor)
        assertNull(storage.value.deviceName)
    }

    private class MemoryStorage(var value: PhysicalButtonConfiguration = PhysicalButtonConfiguration()) : PhysicalButtonStorage {
        var writes = 0
        override fun read() = value
        override fun write(configuration: PhysicalButtonConfiguration) { value = configuration; writes++ }
    }
}
