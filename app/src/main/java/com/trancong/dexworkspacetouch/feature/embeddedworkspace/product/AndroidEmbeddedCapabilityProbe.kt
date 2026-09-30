package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import android.content.pm.PackageManager
import android.os.Build
import rikka.shizuku.Shizuku

/** Reads capability only. No UserService lease, bind, permission request, or session. */
class AndroidEmbeddedCapabilityProbe : EmbeddedCapabilityProbe {
    override fun snapshot(): EmbeddedCapabilitySnapshot {
        val platform = Build.VERSION.SDK_INT >= 34 && runCatching {
            Class.forName("android.companion.virtual.VirtualDeviceManager")
        }.isSuccess
        val binder = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val permission = binder && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        return EmbeddedCapabilitySnapshot(platform, binder, permission)
    }
}
