package com.trancong.dexworkspacetouch.feature.car

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarWorkspaceShortcutPreferencesDeviceTest {
    @Test
    fun autoRepairDefaultsOffAndSurvivesSharedPreferencesRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("swc003_mode_roundtrip_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val first = StoredCarWorkspaceShortcutPreferences(SharedPreferencesCarWorkspaceShortcutStorage(prefs))
            assertEquals(false, first.autoRepairEnabled.value)
            for (enabled in listOf(true, false)) {
                first.setAutoRepairEnabled(enabled)
                val reopened = StoredCarWorkspaceShortcutPreferences(SharedPreferencesCarWorkspaceShortcutStorage(
                    context.getSharedPreferences("swc003_mode_roundtrip_test", Context.MODE_PRIVATE)))
                assertEquals(enabled, reopened.autoRepairEnabled.value)
            }
        } finally { prefs.edit().clear().commit() }
    }
    @Test
    fun sharedPreferencesAdapterSurvivesRecreationAndClearsIndependently() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fileName = "car_workspace_shortcuts_device_test"
        val sharedPreferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().commit()
        try {
            val first = StoredCarWorkspaceShortcutPreferences(
                SharedPreferencesCarWorkspaceShortcutStorage(sharedPreferences),
            )
            first.setWorkspace(CarWorkspaceShortcutSlot.Slot1, "workspace-a")
            first.setWorkspace(CarWorkspaceShortcutSlot.Slot2, "workspace-b")
            first.setWorkspace(CarWorkspaceShortcutSlot.Slot7, "workspace-a")
            first.setWorkspace(CarWorkspaceShortcutSlot.Slot8, "workspace-b")
            first.setVisibleSlotCount(8)

            val recreated = StoredCarWorkspaceShortcutPreferences(
                SharedPreferencesCarWorkspaceShortcutStorage(
                    context.getSharedPreferences(fileName, Context.MODE_PRIVATE),
                ),
            )
            assertEquals(
                "workspace-a",
                recreated.shortcuts.value[CarWorkspaceShortcutSlot.Slot1].workspaceId,
            )
            assertEquals(
                "workspace-b",
                recreated.shortcuts.value[CarWorkspaceShortcutSlot.Slot2].workspaceId,
            )
            assertNull(recreated.shortcuts.value[CarWorkspaceShortcutSlot.Slot3].workspaceId)
            assertEquals("workspace-a", recreated.shortcuts.value[CarWorkspaceShortcutSlot.Slot7].workspaceId)
            assertEquals("workspace-b", recreated.shortcuts.value[CarWorkspaceShortcutSlot.Slot8].workspaceId)
            assertEquals(8, recreated.visibleSlotCount.value)
            recreated.setVisibleSlotCount(4)
            assertEquals("workspace-a", recreated.shortcuts.value[CarWorkspaceShortcutSlot.Slot7].workspaceId)
            assertEquals("workspace-b", recreated.shortcuts.value[CarWorkspaceShortcutSlot.Slot8].workspaceId)
            recreated.clear(CarWorkspaceShortcutSlot.Slot1)
            assertNull(recreated.shortcuts.value[CarWorkspaceShortcutSlot.Slot1].workspaceId)
            assertEquals(
                "workspace-b",
                recreated.shortcuts.value[CarWorkspaceShortcutSlot.Slot2].workspaceId,
            )
            assertEquals(4, StoredCarWorkspaceShortcutPreferences(
                SharedPreferencesCarWorkspaceShortcutStorage(sharedPreferences),
            ).visibleSlotCount.value)
        } finally {
            sharedPreferences.edit().clear().commit()
        }
    }
}
