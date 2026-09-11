package com.trancong.dexworkspacetouch.feature.car.shortcut

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarDockShortcutDeviceTest {
    @Test
    fun shortcutInfoUsesStableIdentityLabelAndExplicitBootstrapActivity() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val shortcut = buildCarDockShortcutInfo(instrumentation.targetContext)
        val shortcutIntent = requireNotNull(shortcut.intent)
        assertEquals(CarDockPinnedShortcut.Id, shortcut.id)
        assertEquals(CarDockPinnedShortcut.Label, shortcut.shortLabel.toString())
        assertEquals(CarDockPinnedShortcut.Action, shortcutIntent.action)
        assertEquals(
            CarDockShortcutActivity::class.java.name,
            shortcutIntent.component?.className,
        )
        assertTrue(shortcutIntent.component != null)
    }
}
