package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import rikka.shizuku.Shizuku

/** Reads capability only. No UserService lease, bind, permission request, or session. */
class AndroidEmbeddedCapabilityProbe(context: Context? = null) : EmbeddedCapabilityProbe, EmbeddedShizukuCallbacks {
    private val appContext = context?.applicationContext
    private val callbackHandler by lazy { Handler(Looper.getMainLooper()) }

    override fun snapshot(): EmbeddedCapabilitySnapshot {
        val platform = Build.VERSION.SDK_INT >= 34 && runCatching {
            Class.forName("android.companion.virtual.VirtualDeviceManager")
        }.isSuccess
        val binder = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val permission = binder && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        return EmbeddedCapabilitySnapshot(platform, binder, permission, shizukuLaunchAvailable = launchIntent() != null)
    }

    override fun listenBinderReceived(listener: () -> Unit): AutoCloseable {
        val received = Shizuku.OnBinderReceivedListener { listener() }
        Shizuku.addBinderReceivedListener(received, callbackHandler)
        return AutoCloseable { Shizuku.removeBinderReceivedListener(received) }
    }

    override fun listenBinderDead(listener: () -> Unit): AutoCloseable {
        val dead = Shizuku.OnBinderDeadListener { listener() }
        Shizuku.addBinderDeadListener(dead, callbackHandler)
        return AutoCloseable { Shizuku.removeBinderDeadListener(dead) }
    }

    override fun listenPermissionResult(listener: (Int, Boolean) -> Unit): AutoCloseable {
        val permission = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            listener(requestCode, grantResult == PackageManager.PERMISSION_GRANTED)
        }
        Shizuku.addRequestPermissionResultListener(permission, callbackHandler)
        return AutoCloseable { Shizuku.removeRequestPermissionResultListener(permission) }
    }

    override fun requestPermission(requestCode: Int) = Shizuku.requestPermission(requestCode)

    fun openShizuku(): Boolean {
        val context = appContext ?: return false
        val intent = launchIntent() ?: return false
        return runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    private fun launchIntent(): Intent? = runCatching {
        val manager = appContext?.packageManager ?: return@runCatching null
        val intent = manager.getLaunchIntentForPackage(SHIZUKU_MANAGER_PACKAGE) ?: return@runCatching null
        val activity = manager.resolveActivity(intent, 0)?.activityInfo ?: return@runCatching null
        if (activity.packageName != SHIZUKU_MANAGER_PACKAGE || !activity.exported || !activity.enabled) null
        else intent
    }.getOrNull()

    private companion object {
        const val SHIZUKU_MANAGER_PACKAGE = "moe.shizuku.privileged.api"
    }
}
