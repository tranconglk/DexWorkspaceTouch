package com.trancong.dexworkspacetouch.feature.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CarWorkspaceShortcutTest {
    @Test
    fun defaultsContainExactlyEightUnconfiguredStableSlots() {
        val shortcuts = StoredCarWorkspaceShortcutPreferences(MemoryStorage()).shortcuts.value

        assertEquals(
            listOf("slot-1", "slot-2", "slot-3", "slot-4", "slot-5", "slot-6", "slot-7", "slot-8"),
            CarWorkspaceShortcutSlot.entries.map { it.stableKey },
        )
        assertEquals(8, shortcuts.configurations.size)
        shortcuts.configurations.forEach { assertNull(it.workspaceId) }
    }

    @Test
    fun setReplaceClearAndRestartPreserveIndependentSlots() {
        val storage = MemoryStorage()
        val first = StoredCarWorkspaceShortcutPreferences(storage)
        first.setWorkspace(CarWorkspaceShortcutSlot.Slot1, "workspace-a")
        first.setWorkspace(CarWorkspaceShortcutSlot.Slot2, "workspace-b")
        first.setWorkspace(CarWorkspaceShortcutSlot.Slot3, "workspace-c")

        val restarted = StoredCarWorkspaceShortcutPreferences(storage)
        assertEquals("workspace-a", restarted.shortcuts.value[CarWorkspaceShortcutSlot.Slot1].workspaceId)
        assertEquals("workspace-b", restarted.shortcuts.value[CarWorkspaceShortcutSlot.Slot2].workspaceId)
        assertEquals("workspace-c", restarted.shortcuts.value[CarWorkspaceShortcutSlot.Slot3].workspaceId)
        assertNull(restarted.shortcuts.value[CarWorkspaceShortcutSlot.Slot4].workspaceId)
        assertNull(restarted.shortcuts.value[CarWorkspaceShortcutSlot.Slot5].workspaceId)
        assertNull(restarted.shortcuts.value[CarWorkspaceShortcutSlot.Slot6].workspaceId)
        assertNull(restarted.shortcuts.value[CarWorkspaceShortcutSlot.Slot7].workspaceId)
        assertNull(restarted.shortcuts.value[CarWorkspaceShortcutSlot.Slot8].workspaceId)

        restarted.setWorkspace(CarWorkspaceShortcutSlot.Slot1, "workspace-new")
        assertEquals("workspace-new", restarted.shortcuts.value[CarWorkspaceShortcutSlot.Slot1].workspaceId)
        assertEquals("workspace-b", restarted.shortcuts.value[CarWorkspaceShortcutSlot.Slot2].workspaceId)
        assertEquals("workspace-c", restarted.shortcuts.value[CarWorkspaceShortcutSlot.Slot3].workspaceId)
        restarted.clear(CarWorkspaceShortcutSlot.Slot2)
        assertNull(restarted.shortcuts.value[CarWorkspaceShortcutSlot.Slot2].workspaceId)
    }

    @Test
    fun sameWorkspaceCanBeAssignedToMultipleSlots() {
        val preferences = StoredCarWorkspaceShortcutPreferences(MemoryStorage())
        CarWorkspaceShortcutSlot.entries.forEach {
            preferences.setWorkspace(it, "shared-workspace")
        }

        assertEquals(
            List(8) { "shared-workspace" },
            preferences.shortcuts.value.configurations.map { it.workspaceId },
        )
    }

    @Test
    fun snapshotsCannotMutateInternalConfiguration() {
        val preferences = StoredCarWorkspaceShortcutPreferences(MemoryStorage())
        val configurations = preferences.shortcuts.value.configurations

        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (configurations as MutableList<CarWorkspaceShortcut>).clear()
        }
        assertEquals(8, preferences.shortcuts.value.configurations.size)
    }

    @Test
    fun resolverUsesStableSlotIdentityAndLatestWorkspaceId() {
        val preferences = StoredCarWorkspaceShortcutPreferences(MemoryStorage())
        val provider = PreferencesCarWorkspaceShortcutWorkflowProvider(preferences)
        assertNull(provider.workflowFor(CarWorkspaceShortcutSlot.Slot1))

        preferences.setWorkspace(CarWorkspaceShortcutSlot.Slot1, "missing-is-still-a-valid-id")
        val firstWorkflow = provider.workflowFor(CarWorkspaceShortcutSlot.Slot1)
        assertEquals(
            "workspace-shortcut-slot-1-v1",
            firstWorkflow?.id,
        )
        assertEquals(
            listOf(CarAction.Workspace("missing-is-still-a-valid-id")),
            firstWorkflow?.actions,
        )

        preferences.setWorkspace(CarWorkspaceShortcutSlot.Slot1, "replacement")
        assertEquals(
            listOf(CarAction.Workspace("replacement")),
            provider.workflowFor(CarWorkspaceShortcutSlot.Slot1)?.actions,
        )
        assertEquals(
            listOf(
                "workspace-shortcut-slot-1-v1",
                "workspace-shortcut-slot-2-v1",
                "workspace-shortcut-slot-3-v1",
                "workspace-shortcut-slot-4-v1",
                "workspace-shortcut-slot-5-v1",
                "workspace-shortcut-slot-6-v1",
                "workspace-shortcut-slot-7-v1",
                "workspace-shortcut-slot-8-v1",
            ),
            CarWorkspaceShortcutSlot.entries.map { it.stableWorkflowId },
        )
    }

    @Test
    fun legacyThreeKeysSurviveAndNewSlotsCanSetReplaceAndClear() {
        val storage = MemoryStorage(mutableMapOf(
            "slot-1" to "legacy-one",
            "slot-2" to "legacy-two",
            "slot-3" to "legacy-three",
        ))
        val preferences = StoredCarWorkspaceShortcutPreferences(storage)
        assertEquals(
            listOf("legacy-one", "legacy-two", "legacy-three", null, null, null, null, null),
            preferences.shortcuts.value.configurations.map { it.workspaceId },
        )

        listOf(CarWorkspaceShortcutSlot.Slot4, CarWorkspaceShortcutSlot.Slot5,
            CarWorkspaceShortcutSlot.Slot6).forEachIndexed { index, slot ->
            preferences.setWorkspace(slot, "new-$index")
            preferences.setWorkspace(slot, "replacement-$index")
        }
        preferences.clear(CarWorkspaceShortcutSlot.Slot5)
        assertEquals("replacement-0", preferences.shortcuts.value[CarWorkspaceShortcutSlot.Slot4].workspaceId)
        assertNull(preferences.shortcuts.value[CarWorkspaceShortcutSlot.Slot5].workspaceId)
        assertEquals("replacement-2", preferences.shortcuts.value[CarWorkspaceShortcutSlot.Slot6].workspaceId)
    }

    @Test
    fun blankWorkspaceIdIsRejected() {
        val preferences = StoredCarWorkspaceShortcutPreferences(MemoryStorage())
        assertThrows(IllegalArgumentException::class.java) {
            preferences.setWorkspace(CarWorkspaceShortcutSlot.Slot1, "  ")
        }
    }

    @Test
    fun visibleCountDefaultsValidatesAndSurvivesRecreation() {
        assertEquals(6, StoredCarWorkspaceShortcutPreferences(MemoryStorage()).visibleSlotCount.value)

        (3..8).forEach { count ->
            val storage = MemoryStorage()
            StoredCarWorkspaceShortcutPreferences(storage).setVisibleSlotCount(count)
            assertEquals(count, StoredCarWorkspaceShortcutPreferences(storage).visibleSlotCount.value)
        }

        assertEquals(
            3,
            StoredCarWorkspaceShortcutPreferences(
                MemoryStorage(mutableMapOf("visibleSlotCount" to "-20")),
            ).visibleSlotCount.value,
        )
        assertEquals(
            8,
            StoredCarWorkspaceShortcutPreferences(
                MemoryStorage(mutableMapOf("visibleSlotCount" to "99")),
            ).visibleSlotCount.value,
        )
        assertEquals(
            6,
            StoredCarWorkspaceShortcutPreferences(
                MemoryStorage(mutableMapOf("visibleSlotCount" to "not-a-number")),
            ).visibleSlotCount.value,
        )
    }

    @Test
    fun reducingVisibleCountDoesNotClearOrReorderHiddenBindings() {
        val storage = MemoryStorage()
        val preferences = StoredCarWorkspaceShortcutPreferences(storage)
        preferences.setWorkspace(CarWorkspaceShortcutSlot.Slot7, "workspace-a")
        preferences.setWorkspace(CarWorkspaceShortcutSlot.Slot8, "workspace-b")
        preferences.setVisibleSlotCount(8)

        assertEquals(
            listOf(CarWorkspaceShortcutSlot.Slot7, CarWorkspaceShortcutSlot.Slot8),
            resolveCarWorkspaceShortcutRows(
                preferences.shortcuts.value,
                listOf(
                    CarWorkspaceOption("workspace-a", "A", 1),
                    CarWorkspaceOption("workspace-b", "B", 1),
                ),
                preferences.visibleSlotCount.value,
            ).takeLast(2).map { it.slot },
        )

        preferences.setVisibleSlotCount(4)
        assertEquals(
            CarWorkspaceShortcutSlot.entries.take(4),
            resolveCarWorkspaceShortcutRows(
                preferences.shortcuts.value,
                emptyList(),
                preferences.visibleSlotCount.value,
            ).map { it.slot },
        )
        assertEquals("workspace-a", preferences.shortcuts.value[CarWorkspaceShortcutSlot.Slot7].workspaceId)
        assertEquals("workspace-b", preferences.shortcuts.value[CarWorkspaceShortcutSlot.Slot8].workspaceId)

        val restarted = StoredCarWorkspaceShortcutPreferences(storage)
        assertEquals(4, restarted.visibleSlotCount.value)
        restarted.setVisibleSlotCount(8)
        assertEquals("workspace-a", restarted.shortcuts.value[CarWorkspaceShortcutSlot.Slot7].workspaceId)
        assertEquals("workspace-b", restarted.shortcuts.value[CarWorkspaceShortcutSlot.Slot8].workspaceId)
    }

    @Test
    fun slotSevenAndEightSetReplaceClearAndPersistIndependently() {
        val storage = MemoryStorage()
        val preferences = StoredCarWorkspaceShortcutPreferences(storage)
        assertNull(preferences.shortcuts.value[CarWorkspaceShortcutSlot.Slot7].workspaceId)
        assertNull(preferences.shortcuts.value[CarWorkspaceShortcutSlot.Slot8].workspaceId)

        preferences.setWorkspace(CarWorkspaceShortcutSlot.Slot7, "shared")
        preferences.setWorkspace(CarWorkspaceShortcutSlot.Slot8, "shared")
        preferences.setWorkspace(CarWorkspaceShortcutSlot.Slot7, "replacement")
        preferences.clear(CarWorkspaceShortcutSlot.Slot8)

        val restarted = StoredCarWorkspaceShortcutPreferences(storage)
        assertEquals("replacement", restarted.shortcuts.value[CarWorkspaceShortcutSlot.Slot7].workspaceId)
        assertNull(restarted.shortcuts.value[CarWorkspaceShortcutSlot.Slot8].workspaceId)
    }

    @Test
    fun presentationAlwaysUsesLeadingStableSlotsForCountsThreeThroughEight() {
        (3..8).forEach { count ->
            assertEquals(
                CarWorkspaceShortcutSlot.entries.take(count),
                resolveCarWorkspaceShortcutRows(
                    CarWorkspaceShortcuts.defaults(),
                    emptyList(),
                    count,
                ).map { it.slot },
            )
        }
    }

    @Test
    fun uiRowsResolveDefaultsConfiguredAndMissingWithoutClearingStoredId() {
        val preferences = StoredCarWorkspaceShortcutPreferences(MemoryStorage())
        preferences.setWorkspace(CarWorkspaceShortcutSlot.Slot1, "available")
        preferences.setWorkspace(CarWorkspaceShortcutSlot.Slot2, "deleted")

        val rows = resolveCarWorkspaceShortcutRows(
            preferences.shortcuts.value,
            listOf(CarWorkspaceOption("available", "Road workspace", 2)),
        )

        assertEquals("Road workspace", rows[0].statusText)
        assertEquals(6, rows.size)
        assertEquals(CarWorkspaceShortcutSlot.entries.take(6), rows.map { it.slot })
        assertEquals(CarWorkspaceShortcutStatus.Configured, rows[0].status)
        assertEquals(2, rows[0].appCount)
        assertEquals("Workspace unavailable", rows[1].statusText)
        assertEquals(CarWorkspaceShortcutStatus.Unavailable, rows[1].status)
        assertEquals("deleted", rows[1].workspaceId)
        assertEquals("Not configured", rows[2].statusText)
        assertEquals(CarWorkspaceShortcutStatus.Unconfigured, rows[2].status)

        preferences.setWorkspace(CarWorkspaceShortcutSlot.Slot2, "available")
        val rebound = resolveCarWorkspaceShortcutRows(
            preferences.shortcuts.value,
            listOf(CarWorkspaceOption("available", "Road workspace", 2)),
        )
        assertEquals("Road workspace", rebound[1].statusText)
        assertEquals("available", rebound[1].workspaceId)

        val renamed = resolveCarWorkspaceShortcutRows(
            preferences.shortcuts.value,
            listOf(CarWorkspaceOption("available", "Renamed road workspace", 3)),
        )
        assertEquals("Renamed road workspace", renamed[0].statusText)
        assertEquals(3, renamed[0].appCount)
    }

    @Test
    fun selectorMarksOnlyCurrentWorkspaceAsSelected() {
        val first = CarWorkspaceOption("first", "First", 1)
        val second = CarWorkspaceOption("second", "Second", 2)

        assertEquals(true, first.isSelected("first"))
        assertEquals(false, second.isSelected("first"))
        assertEquals(false, first.isSelected(null))
    }

    private class MemoryStorage(
        private val values: MutableMap<String, String> = mutableMapOf(),
    ) : CarWorkspaceShortcutStorage {
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String?) {
            if (value == null) values.remove(key) else values[key] = value
        }
    }
}
