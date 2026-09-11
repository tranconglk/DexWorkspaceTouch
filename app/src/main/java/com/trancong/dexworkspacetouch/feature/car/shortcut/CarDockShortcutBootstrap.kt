package com.trancong.dexworkspacetouch.feature.car.shortcut

import com.trancong.dexworkspacetouch.feature.car.overlay.CarFloatingDockControlState
import com.trancong.dexworkspacetouch.feature.car.overlay.CarOverlayHost

internal sealed interface CarDockShortcutBootstrapResult {
    data object Shown : CarDockShortcutBootstrapResult
    data object NotOnExternalDisplay : CarDockShortcutBootstrapResult
    data object PermissionRequired : CarDockShortcutBootstrapResult
    data class Failed(val message: String) : CarDockShortcutBootstrapResult
}

internal fun interface CarDockShowRequest {
    fun show(host: CarOverlayHost): CarFloatingDockControlState
}

internal class CarDockShortcutBootstrap(
    private val showRequest: CarDockShowRequest,
) {
    fun show(host: CarOverlayHost?): CarDockShortcutBootstrapResult {
        if (host == null || host.display.id == 0) {
            return CarDockShortcutBootstrapResult.NotOnExternalDisplay
        }
        return when (val state = showRequest.show(host)) {
            is CarFloatingDockControlState.Visible -> CarDockShortcutBootstrapResult.Shown
            CarFloatingDockControlState.PermissionRequired ->
                CarDockShortcutBootstrapResult.PermissionRequired
            CarFloatingDockControlState.DisplayUnavailable,
            CarFloatingDockControlState.Hidden ->
                CarDockShortcutBootstrapResult.NotOnExternalDisplay
            is CarFloatingDockControlState.Error ->
                CarDockShortcutBootstrapResult.Failed(state.message)
        }
    }
}
