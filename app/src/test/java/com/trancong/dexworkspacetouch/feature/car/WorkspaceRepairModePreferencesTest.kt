package com.trancong.dexworkspacetouch.feature.car

import org.junit.Assert.*
import org.junit.Test

class WorkspaceRepairModePreferencesTest {
    @Test fun absentOrInvalidPreferenceDefaultsToSuggest() {
        for (value in listOf(null, "", "UNKNOWN", "automatic")) {
            val storage = Storage().apply { write("workspaceRepairMode", value) }
            assertEquals(WorkspaceRepairMode.SUGGEST, StoredCarWorkspaceShortcutPreferences(storage).workspaceRepairMode.value)
        }
    }

    @Test fun modesSurviveRecreationWithoutChangingShortcutConfiguration() {
        val storage = Storage()
        val first = StoredCarWorkspaceShortcutPreferences(storage)
        first.setWorkspace(CarWorkspaceShortcutSlot.Slot1, "one")
        first.setVisibleSlotCount(4)
        for (mode in WorkspaceRepairMode.entries) {
            first.setWorkspaceRepairMode(mode)
            val recreated = StoredCarWorkspaceShortcutPreferences(storage)
            assertEquals(mode, first.workspaceRepairMode.value)
            assertEquals(mode, recreated.workspaceRepairMode.value)
            assertEquals("one", recreated.shortcuts.value[CarWorkspaceShortcutSlot.Slot1].workspaceId)
            assertEquals(4, recreated.visibleSlotCount.value)
        }
    }

    @Test fun refreshReadsModeChangedByAnotherPreferencesInstance() {
        val storage = Storage()
        val first = StoredCarWorkspaceShortcutPreferences(storage)
        StoredCarWorkspaceShortcutPreferences(storage).setWorkspaceRepairMode(WorkspaceRepairMode.OFF)
        first.refresh()
        assertEquals(WorkspaceRepairMode.OFF, first.workspaceRepairMode.value)
    }

    private class Storage : CarWorkspaceShortcutStorage {
        private val values = mutableMapOf<String, String>()
        override fun read(key: String) = values[key]
        override fun write(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
    }
}
