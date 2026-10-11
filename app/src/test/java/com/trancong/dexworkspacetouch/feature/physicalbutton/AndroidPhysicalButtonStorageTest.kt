package com.trancong.dexworkspacetouch.feature.physicalbutton

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidPhysicalButtonStorageTest {
    @Test fun legacyConfiguration_migratesOnlyUpAndPreservesRemoteAndEnabled() {
        val fixture = PreferencesFixture(legacyValues())
        val configuration = AndroidPhysicalButtonStorage(fixture.preferences).read()
        assertEquals(PhysicalButtonConfiguration(true, "legacy-workspace", null, "remote-descriptor", "AB Shutter3"), configuration)
        assertEquals(configuration, AndroidPhysicalButtonStorage(fixture.preferences).read())
        assertEquals("legacy-workspace", fixture.values["workspaceId"])
        assertEquals(1, fixture.appliedEdits)
    }

    @Test fun changedMappings_surviveRecreationWithoutLegacyOverwrite() {
        val fixture = PreferencesFixture(legacyValues())
        val preferences = PhysicalButtonPreferences(AndroidPhysicalButtonStorage(fixture.preferences))
        preferences.setVolumeUpWorkspace("new-up")
        preferences.setVolumeDownWorkspace("new-down")
        val reopened = PhysicalButtonPreferences(AndroidPhysicalButtonStorage(fixture.preferences))
        assertEquals("new-up", reopened.configuration.value.volumeUpWorkspaceId)
        assertEquals("new-down", reopened.configuration.value.volumeDownWorkspaceId)
        assertEquals("remote-descriptor", reopened.configuration.value.deviceDescriptor)
        assertTrue(reopened.configuration.value.enabled)
    }

    @Test fun clearedUp_staysUnconfiguredAfterRestartDespiteLegacyKey() {
        val fixture = PreferencesFixture(legacyValues())
        val preferences = PhysicalButtonPreferences(AndroidPhysicalButtonStorage(fixture.preferences))
        preferences.setVolumeDownWorkspace("down-only")
        preferences.setVolumeUpWorkspace(null)
        val reopened = AndroidPhysicalButtonStorage(fixture.preferences).read()
        assertNull(reopened.volumeUpWorkspaceId)
        assertEquals("down-only", reopened.volumeDownWorkspaceId)
        assertEquals("legacy-workspace", fixture.values["workspaceId"])
    }

    @Test fun existingNewMapping_withoutMarkerIsNeverOverwrittenByLegacy() {
        val fixture = PreferencesFixture(legacyValues() + mapOf("volumeDownWorkspaceId" to "down-only"))
        val first = AndroidPhysicalButtonStorage(fixture.preferences).read()
        assertNull(first.volumeUpWorkspaceId)
        assertEquals("down-only", first.volumeDownWorkspaceId)
        assertEquals(first, AndroidPhysicalButtonStorage(fixture.preferences).read())
    }

    @Test fun blankAndMalformedStoredValues_remainUnconfigured() {
        val fixture = PreferencesFixture(mapOf("workspaceId" to 123, "enabled" to "true", "volumeDownWorkspaceId" to "  "))
        val configuration = AndroidPhysicalButtonStorage(fixture.preferences).read()
        assertFalse(configuration.enabled)
        assertNull(configuration.volumeUpWorkspaceId)
        assertNull(configuration.volumeDownWorkspaceId)
        assertNull(configuration.deviceDescriptor)
    }

    @Test fun freshInstall_hasNoMappingAndSubsequentReadDoesNotMigrateAgain() {
        val fixture = PreferencesFixture(emptyMap())
        assertEquals(PhysicalButtonConfiguration(), AndroidPhysicalButtonStorage(fixture.preferences).read())
        assertEquals(PhysicalButtonConfiguration(), AndroidPhysicalButtonStorage(fixture.preferences).read())
        assertEquals(1, fixture.appliedEdits)
    }

    private fun legacyValues() = mapOf<String, Any>(
        "enabled" to true, "workspaceId" to "legacy-workspace",
        "deviceDescriptor" to "remote-descriptor", "deviceName" to "AB Shutter3",
    )

    // JVM test double for the Android preference boundary; the real storage adapter is exercised.
    private class PreferencesFixture(initial: Map<String, Any>) {
        val values = initial.toMutableMap()
        var appliedEdits = 0
        val preferences: SharedPreferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getAll" -> values.toMap()
                "edit" -> editor()
                else -> error("Unexpected SharedPreferences method: ${method.name}")
            }
        } as SharedPreferences

        private fun editor(): SharedPreferences.Editor {
            val pending = mutableMapOf<String, Any?>()
            return Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java),
            ) { proxy, method, args ->
                when (method.name) {
                    "putBoolean", "putString" -> {
                        pending[args!![0] as String] = args[1]
                        proxy
                    }
                    "remove" -> { pending[args!![0] as String] = null; proxy }
                    "apply" -> {
                        pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                        appliedEdits++
                        null
                    }
                    else -> error("Unexpected Editor method: ${method.name}")
                }
            } as SharedPreferences.Editor
        }
    }
}
