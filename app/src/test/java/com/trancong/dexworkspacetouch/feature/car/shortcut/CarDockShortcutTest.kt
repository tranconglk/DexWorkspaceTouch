package com.trancong.dexworkspacetouch.feature.car.shortcut

import com.trancong.dexworkspacetouch.feature.car.overlay.CarFloatingDockControlState
import com.trancong.dexworkspacetouch.feature.car.overlay.CarOverlayDisplay
import com.trancong.dexworkspacetouch.feature.car.overlay.CarOverlayHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CarDockShortcutTest {
    @Test
    fun shortcutIdentityIsStableAndNotRuntimeGenerated() {
        assertEquals("car-floating-dock-show-v1", CarDockPinnedShortcut.Id)
        assertEquals("Car Dock", CarDockPinnedShortcut.Label)
        assertEquals(
            "com.trancong.dexworkspacetouch.action.SHOW_CAR_DOCK",
            CarDockPinnedShortcut.Action,
        )
    }

    @Test
    fun unsupportedLauncherDoesNotRequestPin() {
        var requests = 0
        val controller = CarDockShortcutPinController(false) {
            requests += 1
            true
        }

        assertFalse(controller.isSupported)
        assertSame(CarDockPinRequestResult.Unsupported, controller.requestPin())
        assertEquals(0, requests)
    }

    @Test
    fun supportedLauncherRequestsPinOnceAndReportsPlatformDecision() {
        var acceptedRequests = 0
        val accepted = CarDockShortcutPinController(true) {
            acceptedRequests += 1
            true
        }
        val rejected = CarDockShortcutPinController(true) { false }

        assertTrue(accepted.isSupported)
        assertSame(CarDockPinRequestResult.Requested, accepted.requestPin())
        assertEquals(1, acceptedRequests)
        assertSame(CarDockPinRequestResult.Rejected, rejected.requestPin())
    }

    @Test
    fun bootstrapRejectsDefaultDisplayWithoutShowing() {
        var requestedHost: CarOverlayHost? = null
        val bootstrap = CarDockShortcutBootstrap { host ->
            requestedHost = host
            CarFloatingDockControlState.Visible(host.display.id)
        }

        assertSame(
            CarDockShortcutBootstrapResult.NotOnExternalDisplay,
            bootstrap.show(host(0)),
        )
        assertEquals(null, requestedHost)
    }

    @Test
    fun bootstrapShowsExactExternalHostAndAcceptsAlreadyVisibleState() {
        val expected = host(41)
        var requestedHost: CarOverlayHost? = null
        val bootstrap = CarDockShortcutBootstrap { host ->
            requestedHost = host
            CarFloatingDockControlState.Visible(21)
        }

        assertSame(CarDockShortcutBootstrapResult.Shown, bootstrap.show(expected))
        assertSame(expected, requestedHost)
    }

    @Test
    fun bootstrapMapsPermissionAndFailureWithoutThrowing() {
        assertSame(
            CarDockShortcutBootstrapResult.PermissionRequired,
            CarDockShortcutBootstrap { CarFloatingDockControlState.PermissionRequired }.show(host(5)),
        )
        assertEquals(
            CarDockShortcutBootstrapResult.Failed("window failed"),
            CarDockShortcutBootstrap {
                CarFloatingDockControlState.Error("window failed")
            }.show(host(5)),
        )
    }

    private fun host(displayId: Int) = object : CarOverlayHost {
        override val display = CarOverlayDisplay(displayId)
    }
}
