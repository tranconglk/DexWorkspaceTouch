package com.trancong.dexworkspacetouch.feature.car

import org.junit.Assert.*
import org.junit.Test

class WorkspaceRepairModePreferencesTest {
    @Test fun absentSuggestOrInvalidPreferenceDefaultsToOff() {
        for (value in listOf(null, "", "UNKNOWN", "automatic", "SUGGEST", "OFF")) {
            val storage = Storage().apply { write("workspaceRepairMode", value) }
            assertFalse(StoredCarWorkspaceShortcutPreferences(storage).autoRepairEnabled.value)
        }
    }

    @Test fun modesSurviveRecreationWithoutChangingShortcutConfiguration() {
        val storage = Storage()
        val first = StoredCarWorkspaceShortcutPreferences(storage)
        first.setWorkspace(CarWorkspaceShortcutSlot.Slot1, "one")
        first.setVisibleSlotCount(4)
        for (enabled in listOf(true, false)) {
            first.setAutoRepairEnabled(enabled)
            val recreated = StoredCarWorkspaceShortcutPreferences(storage)
            assertEquals(enabled, first.autoRepairEnabled.value)
            assertEquals(enabled, recreated.autoRepairEnabled.value)
            assertEquals("one", recreated.shortcuts.value[CarWorkspaceShortcutSlot.Slot1].workspaceId)
            assertEquals(4, recreated.visibleSlotCount.value)
        }
    }

    @Test fun refreshReadsModeChangedByAnotherPreferencesInstance() {
        val storage = Storage()
        val first = StoredCarWorkspaceShortcutPreferences(storage)
        StoredCarWorkspaceShortcutPreferences(storage).setAutoRepairEnabled(true)
        first.refresh()
        assertTrue(first.autoRepairEnabled.value)
    }

    @Test fun legacyAutomaticMigratesToTrueExactlyOnce() {
        val storage = Storage().apply { write("workspaceRepairMode", "AUTOMATIC") }
        val preferences = StoredCarWorkspaceShortcutPreferences(storage)
        assertTrue(preferences.autoRepairEnabled.value)
        assertEquals("true", storage.read("autoRepairEnabled"))
        preferences.setAutoRepairEnabled(false)
        assertFalse(StoredCarWorkspaceShortcutPreferences(storage).autoRepairEnabled.value)
    }

    @Test fun suggestOffAbsentAndCorruptMigrationPersistFalse() {
        for (legacy in listOf("SUGGEST", "OFF", null, "UNKNOWN", "automatic", "")) {
            val storage = Storage().apply { write("workspaceRepairMode", legacy) }
            assertFalse(StoredCarWorkspaceShortcutPreferences(storage).autoRepairEnabled.value)
            assertEquals("false", storage.read("autoRepairEnabled"))
        }
    }

    @Test fun newPreferenceOverridesHistoricalAutomaticIncludingCorruptValues() {
        for (value in listOf("false", "", "TRUE", "invalid")) {
            val storage = Storage().apply {
                write("workspaceRepairMode", "AUTOMATIC"); write("autoRepairEnabled", value)
            }
            assertFalse(StoredCarWorkspaceShortcutPreferences(storage).autoRepairEnabled.value)
        }
    }

    private class Storage : CarWorkspaceShortcutStorage {
        private val values = mutableMapOf<String, String>()
        override fun read(key: String) = values[key]
        override fun write(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
    }
}
