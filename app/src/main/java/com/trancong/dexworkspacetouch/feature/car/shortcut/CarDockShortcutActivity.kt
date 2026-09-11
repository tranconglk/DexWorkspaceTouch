package com.trancong.dexworkspacetouch.feature.car.shortcut

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.trancong.dexworkspacetouch.DexWorkspaceTouchApplication
import com.trancong.dexworkspacetouch.feature.car.overlay.createActivityCarOverlayHost

class CarDockShortcutActivity : Activity() {
    private var attempted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action != CarDockPinnedShortcut.Action) finish()
    }

    override fun onPostResume() {
        super.onPostResume()
        if (attempted || isFinishing) return
        window.decorView.post(::showDockFromAttachedDisplay)
    }

    private fun showDockFromAttachedDisplay() {
        if (attempted || isFinishing || isDestroyed) return
        attempted = true
        val coordinator = (application as DexWorkspaceTouchApplication).carFloatingDockCoordinator
        val result = CarDockShortcutBootstrap { host ->
            coordinator.show(host)
            coordinator.dockState.value
        }.show(createActivityCarOverlayHost(this))
        when (result) {
            CarDockShortcutBootstrapResult.Shown -> Unit
            CarDockShortcutBootstrapResult.NotOnExternalDisplay ->
                showMessage("Open this shortcut on DeX desktop")
            CarDockShortcutBootstrapResult.PermissionRequired ->
                showMessage("Allow Floating Dock permission in DexWorkspaceTouch")
            is CarDockShortcutBootstrapResult.Failed -> showMessage(result.message)
        }
        finish()
    }

    private fun showMessage(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }
}
