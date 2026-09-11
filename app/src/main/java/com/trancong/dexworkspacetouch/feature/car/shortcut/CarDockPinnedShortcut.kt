package com.trancong.dexworkspacetouch.feature.car.shortcut

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import com.trancong.dexworkspacetouch.R

internal object CarDockPinnedShortcut {
    const val Id = "car-floating-dock-show-v1"
    const val Label = "Car Dock"
    const val Action = "com.trancong.dexworkspacetouch.action.SHOW_CAR_DOCK"
}

internal enum class CarDockPinRequestResult {
    Requested,
    Unsupported,
    Rejected,
}

internal fun interface CarDockShortcutPinPlatform {
    fun requestPin(): Boolean
}

internal class CarDockShortcutPinController(
    private val supported: Boolean,
    private val platform: CarDockShortcutPinPlatform,
) {
    val isSupported: Boolean get() = supported

    fun requestPin(): CarDockPinRequestResult {
        if (!supported) return CarDockPinRequestResult.Unsupported
        return if (platform.requestPin()) {
            CarDockPinRequestResult.Requested
        } else {
            CarDockPinRequestResult.Rejected
        }
    }
}

internal class AndroidCarDockShortcutPinPlatform(
    private val activity: Activity,
) : CarDockShortcutPinPlatform {
    private val shortcutManager = activity.getSystemService(ShortcutManager::class.java)

    val isSupported: Boolean
        get() = shortcutManager?.isRequestPinShortcutSupported == true

    override fun requestPin(): Boolean {
        val manager = shortcutManager ?: return false
        if (!manager.isRequestPinShortcutSupported) return false
        return manager.requestPinShortcut(buildCarDockShortcutInfo(activity), null)
    }
}

internal fun buildCarDockShortcutInfo(context: Context): ShortcutInfo = ShortcutInfo.Builder(
    context,
    CarDockPinnedShortcut.Id,
).setShortLabel(CarDockPinnedShortcut.Label)
    .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
    .setIntent(
        Intent(CarDockPinnedShortcut.Action).apply {
            component = ComponentName(context, CarDockShortcutActivity::class.java)
        },
    )
    .build()
