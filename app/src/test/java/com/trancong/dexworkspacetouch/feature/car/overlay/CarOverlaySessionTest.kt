package com.trancong.dexworkspacetouch.feature.car.overlay

import com.trancong.dexworkspacetouch.feature.car.CarWorkspaceShortcutSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarOverlaySessionTest {
    @Test
    fun suppliedActivityHostIsSourceOfTruth_defaultRejected_andAlreadyShownDoesNotMigrate() {
        val permission = FakePermission(true)
        val factory = FakeFactory()
        val events = FakeEvents()
        val session = CarOverlaySession(permission, factory, events)
        val host41 = fakeHost(41)
        val host42 = fakeHost(42)

        assertEquals(CarOverlayShowResult.DisplayUnavailable, session.show(fakeHost(0)) {})
        assertEquals(CarOverlayShowResult.Shown(41), session.show(host41) {})
        assertEquals(CarOverlayShowResult.AlreadyShown(41), session.show(host42) {})
        assertEquals(CarOverlaySessionState.Shown(41, CarFloatingDockState.Collapsed), session.state)
        assertEquals(host41, session.activeHost)
        assertEquals(listOf(41), factory.createdDisplays)

        session.hide()
        assertEquals(CarOverlayShowResult.Shown(42), session.show(host42) {})
        assertEquals(CarOverlaySessionState.Shown(42, CarFloatingDockState.Collapsed), session.state)
        assertEquals(listOf(41, 42), factory.createdDisplays)
    }

    @Test
    fun showCreatesOneWindow_repeatShowHideAndDoubleDisposeAreSafe() {
        val fixture = Fixture()

        assertEquals(CarOverlayShowResult.Shown(21), fixture.session.show(fixture.host) {})
        assertEquals(CarOverlayShowResult.AlreadyShown(21), fixture.session.show(fixture.host) {})
        assertEquals(1, fixture.factory.createdDisplays.size)
        assertEquals(
            CarOverlaySessionState.Shown(21, CarFloatingDockState.Collapsed),
            fixture.session.state,
        )
        assertEquals(fixture.session.state, fixture.session.stateChanges.value)

        fixture.session.hide()
        fixture.session.hide()
        assertEquals(1, fixture.factory.windows.single().removes)
        assertEquals(CarOverlaySessionState.NotShown, fixture.session.state)

        fixture.session.dispose()
        fixture.session.dispose()
        assertEquals(CarOverlaySessionState.Disposed, fixture.session.state)
        assertEquals(CarOverlayShowResult.SessionDisposed, fixture.session.show(fixture.host) {})
    }

    @Test
    fun permissionDeniedAndUnavailableDisplayDoNotCreateWindow() {
        val denied = Fixture(permission = false)
        assertEquals(CarOverlayShowResult.PermissionDenied, denied.session.show(denied.host) {})
        assertTrue(denied.factory.createdDisplays.isEmpty())

        val unavailable = Fixture(hostDisplayId = 0)
        assertEquals(CarOverlayShowResult.DisplayUnavailable, unavailable.session.show(unavailable.host) {})
        assertTrue(unavailable.factory.createdDisplays.isEmpty())
    }

    @Test
    fun defaultDisplayHostIsRejected() {
        val fixture = Fixture(hostDisplayId = 0)
        assertEquals(CarOverlayShowResult.DisplayUnavailable, fixture.session.show(fixture.host) {})
        assertTrue(fixture.factory.createdDisplays.isEmpty())
    }

    @Test
    fun displayRemovedCleansWindowAndFreshShowUsesNewExplicitHost() {
        val fixture = Fixture()
        assertEquals(CarOverlayShowResult.Shown(21), fixture.session.show(fixture.host) {})
        val oldWindow = fixture.factory.windows.single()

        fixture.events.remove(21)
        assertEquals(1, oldWindow.removes)
        assertEquals(CarOverlaySessionState.NotShown, fixture.session.state)

        assertEquals(CarOverlayShowResult.Shown(37), fixture.session.show(fakeHost(37)) {})
        assertEquals(listOf(21, 37), fixture.factory.createdDisplays)
        assertEquals(
            CarOverlaySessionState.Shown(37, CarFloatingDockState.Collapsed),
            fixture.session.state,
        )
    }

    @Test
    fun windowFactoryRejectionDoesNotCreateSessionState() {
        val fixture = Fixture()
        fixture.factory.rejectCreate = true

        assertEquals(CarOverlayShowResult.WindowUnavailable, fixture.session.show(fixture.host) {})
        assertTrue(fixture.factory.windows.isEmpty())
        assertEquals(CarOverlaySessionState.NotShown, fixture.session.state)
    }

    @Test
    fun permissionRevocationRemovesExistingWindow() {
        val fixture = Fixture()
        fixture.session.show(fixture.host) {}
        fixture.permission.allowed = false

        assertEquals(CarOverlayShowResult.PermissionDenied, fixture.session.show(fixture.host) {})
        assertEquals(1, fixture.factory.windows.single().removes)
        assertEquals(CarOverlaySessionState.NotShown, fixture.session.state)
    }

    @Test
    fun dockStartsCollapsed_expands_emitsSemanticActionsAndCollapses() {
        val fixture = Fixture()
        val actions = mutableListOf<CarWorkspaceShortcutSlot>()
        fixture.session.show(fixture.host, actions::add)

        assertEquals(
            CarOverlaySessionState.Shown(21, CarFloatingDockState.Collapsed),
            fixture.session.state,
        )
        assertTrue(fixture.session.performHandleClickForTest())
        assertEquals(
            CarOverlaySessionState.Shown(21, CarFloatingDockState.Expanded),
            fixture.session.state,
        )

        CarWorkspaceShortcutSlot.entries.forEach {
            assertTrue(fixture.session.performShortcutClickForTest(it))
        }
        assertEquals(
            CarWorkspaceShortcutSlot.entries,
            actions,
        )
        assertEquals(
            CarOverlaySessionState.Shown(21, CarFloatingDockState.Expanded),
            fixture.session.state,
        )

        assertTrue(fixture.session.performCollapseClickForTest())
        assertEquals(
            CarOverlaySessionState.Shown(21, CarFloatingDockState.Collapsed),
            fixture.session.state,
        )
        fixture.session.hide()
        fixture.session.show(fixture.host, actions::add)
        assertEquals(
            CarOverlaySessionState.Shown(21, CarFloatingDockState.Collapsed),
            fixture.session.state,
        )
    }

    @Test
    fun coordinatorCollapseKeepsSessionShownAndDoesNotRemoveWindow() {
        val fixture = Fixture()
        fixture.session.show(fixture.host) {}
        fixture.session.performHandleClickForTest()

        fixture.session.collapse()

        assertEquals(
            CarOverlaySessionState.Shown(21, CarFloatingDockState.Collapsed),
            fixture.session.state,
        )
        assertEquals(fixture.session.state, fixture.session.stateChanges.value)
        assertEquals(0, fixture.factory.windows.single().removes)
        assertTrue(fixture.session.performHandleClickForTest())
        assertEquals(
            CarOverlaySessionState.Shown(21, CarFloatingDockState.Expanded),
            fixture.session.state,
        )
    }

    private class Fixture(
        permission: Boolean = true,
        hostDisplayId: Int = 21,
    ) {
        val permission = FakePermission(permission)
        val host = object : CarOverlayHost {
            override val display = CarOverlayDisplay(hostDisplayId)
        }
        val factory = FakeFactory()
        val events = FakeEvents()
        val session = CarOverlaySession(this.permission, factory, events)
    }

    private fun fakeHost(id: Int): CarOverlayHost = object : CarOverlayHost {
        override val display = CarOverlayDisplay(id)
    }

    private class FakePermission(var allowed: Boolean) : CarOverlayPermissionChecker {
        override fun canDrawOverlays(): Boolean = allowed
    }

    private class FakeFactory : CarFloatingDockWindowFactory {
        val createdDisplays = mutableListOf<Int>()
        val windows = mutableListOf<FakeWindow>()
        var rejectCreate = false
        override fun create(
            host: CarOverlayHost,
            onShortcut: (CarWorkspaceShortcutSlot) -> Unit,
            onDockStateChanged: () -> Unit,
        ): CarFloatingDockWindow? {
            if (rejectCreate) return null
            createdDisplays += host.display.id
            return FakeWindow(host.display, onShortcut, onDockStateChanged).also {
                windows += it
            }
        }
    }

    private class FakeWindow(
        override val display: CarOverlayDisplay,
        private val onAction: (CarWorkspaceShortcutSlot) -> Unit,
        private val onDockStateChanged: () -> Unit,
    ) : CarFloatingDockWindow {
        var removes = 0
        override var dockState = CarFloatingDockState.Collapsed
        override fun expand() { dockState = CarFloatingDockState.Expanded; onDockStateChanged() }
        override fun collapse() { dockState = CarFloatingDockState.Collapsed; onDockStateChanged() }
        override fun setActionsEnabled(enabled: Boolean) = Unit
        override fun updateShortcuts(shortcuts: List<CarFloatingWorkspaceShortcut>) = Unit
        override fun remove() { removes++ }
        override fun performHandleClickForTest(): Boolean {
            expand()
            return true
        }
        override fun performShortcutClickForTest(slot: CarWorkspaceShortcutSlot): Boolean {
            if (dockState != CarFloatingDockState.Expanded) return false
            onAction(slot)
            return true
        }
        override fun performCollapseClickForTest(): Boolean {
            collapse()
            return true
        }
    }

    private class FakeEvents : CarOverlayDisplayEvents {
        private var callback: ((Int) -> Unit)? = null
        override fun register(onDisplayRemoved: (Int) -> Unit): CarOverlayDisplayRegistration {
            callback = onDisplayRemoved
            return CarOverlayDisplayRegistration { callback = null }
        }
        fun remove(displayId: Int) = requireNotNull(callback).invoke(displayId)
    }
}
