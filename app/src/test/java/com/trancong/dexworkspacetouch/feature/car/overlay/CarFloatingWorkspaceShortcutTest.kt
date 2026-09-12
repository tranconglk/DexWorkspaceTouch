package com.trancong.dexworkspacetouch.feature.car.overlay

import com.trancong.dexworkspacetouch.feature.car.CarWorkspaceShortcutSlot
import com.trancong.dexworkspacetouch.feature.car.CarWorkspaceShortcuts
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarFloatingWorkspaceShortcutTest {
    @Test fun eightSlotsUseStableTwoByFourGridOrdering() {
        assertEquals(8, CarWorkspaceShortcutSlot.entries.size)
        assertEquals(2, CarFloatingDockGrid.Columns)
        assertEquals(4, CarFloatingDockGrid.rows(8))
        assertEquals(
            listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1, 2 to 0, 2 to 1, 3 to 0, 3 to 1),
            CarWorkspaceShortcutSlot.entries.map {
                CarFloatingDockGrid.row(it) to CarFloatingDockGrid.column(it)
            },
        )
    }

    @Test fun resolvesAllSixSlotsWithConfiguredUnconfiguredAndUnavailableStates() {
        val shortcuts = CarWorkspaceShortcuts.from { slot ->
            when (slot) {
                CarWorkspaceShortcutSlot.Slot1 -> "one"
                CarWorkspaceShortcutSlot.Slot2 -> "deleted"
                CarWorkspaceShortcutSlot.Slot3 -> null
                else -> null
            }
        }

        val items = resolveFloatingWorkspaceShortcuts(shortcuts, mapOf("one" to workspace("one", "Car test")), 8)

        assertEquals(CarWorkspaceShortcutSlot.entries, items.map { it.slot })
        assertEquals("Slot 1 — Car test", items[0].accessibilityLabel)
        assertTrue(items[0].state is CarFloatingWorkspaceShortcutState.Configured)
        assertTrue(items[1].state is CarFloatingWorkspaceShortcutState.Unavailable)
        assertTrue(items[2].state is CarFloatingWorkspaceShortcutState.Unconfigured)
        assertTrue(items[0].enabled)
        assertFalse(items[1].enabled)
        assertFalse(items[2].enabled)
    }

    @Test fun renamedAndLongWorkspaceNameFlowsThroughWithoutChangingSlotIdentity() {
        val shortcuts = CarWorkspaceShortcuts.from { if (it == CarWorkspaceShortcutSlot.Slot1) "one" else null }
        val longName = "A very long external display workspace name that the window will ellipsize"

        val renamed = resolveFloatingWorkspaceShortcuts(
            shortcuts,
            mapOf("one" to workspace("one", longName)),
            6,
        ).first()

        assertEquals(CarWorkspaceShortcutSlot.Slot1, renamed.slot)
        assertEquals("Slot 1 — $longName", renamed.accessibilityLabel)
        assertEquals("one", (renamed.state as CarFloatingWorkspaceShortcutState.Configured).workspaceId)
    }

    @Test fun rowCountIsDerivedForEverySupportedVisibleCount() {
        assertEquals(listOf(2, 2, 3, 3, 4, 4), (3..8).map(CarFloatingDockGrid::rows))
        assertEquals(
            listOf(192, 192, 260, 260, 328, 328),
            (3..8).map(CarFloatingDockGrid::expandedHeightDp),
        )
    }

    @Test fun visualContractKeepsDockCompactAndAccessibilityActionsExplicit() {
        assertEquals(72, CarFloatingDockVisual.CollapsedSizeDp)
        assertEquals(360, CarFloatingDockVisual.ExpandedWidthDp)
        assertEquals("Open Car Dock", CarFloatingDockVisual.OpenDescription)
        assertEquals(
            "Car Dock. Collapse Floating Dock",
            CarFloatingDockVisual.CollapseDescription,
        )
    }

    @Test fun visibleItemsAreAlwaysTheStableLeadingSlots() {
        val shortcuts = CarWorkspaceShortcuts.defaults()
        (3..8).forEach { count ->
            assertEquals(
                CarWorkspaceShortcutSlot.entries.take(count),
                resolveFloatingWorkspaceShortcuts(shortcuts, emptyMap(), count).map { it.slot },
            )
        }
    }

    private fun workspace(id: String, name: String) = Workspace(
        id = id,
        name = name,
        canvas = WorkspaceCanvas.singleCell(),
        modifiedSequence = 0,
        schemaVersion = 1,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
    )
}
