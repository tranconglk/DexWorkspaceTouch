package com.trancong.dexworkspacetouch.feature.embeddedapp.remote

import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppGeometry
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppTarget
import kotlin.system.exitProcess

class EmbeddedAppUserService : IEmbeddedAppService.Stub() {
    private var association: SessionAssociation? = null

    @Synchronized override fun getUid() = Process.myUid()

    @Synchronized
    override fun startSession(
        surface: Surface,
        packageName: String,
        componentName: String,
        width: Int,
        height: Int,
        densityDpi: Int,
    ): Bundle = runCatching {
        check(Process.myUid() == 2000)
        check(association == null)
        val target = EmbeddedAppTarget(packageName, componentName,
            EmbeddedAppGeometry(width, height, densityDpi))
        val created = AssociationShell.create()
        association = created
        val display = EmbeddedAppVdm.create(surface, target, created.id)
        check(display.getBoolean("success")) { display.getString("exception") ?: "Display creation failed" }
        val input = EmbeddedAppVdm.prepareTouchscreen()
        check(input.getBoolean("success")) { input.getString("exception") ?: "Touchscreen creation failed" }
        waitForStableFingerConfig(display.getInt("displayId"))
        val launch = EmbeddedAppVdm.launchTarget()
        check(launch.getBoolean("success")) { launch.getString("exception") ?: "Target launch failed" }
        Bundle().apply {
            putBoolean("success", true)
            putInt("remoteUid", Process.myUid())
            putInt("associationId", created.id)
            putString("associationMac", created.mac)
            putInt("deviceId", display.getInt("deviceId"))
            putInt("displayId", display.getInt("displayId"))
            putInt("inputDeviceId", input.getInt("inputDeviceId"))
            putInt("launchResult", launch.getInt("result"))
        }
    }.getOrElse { error ->
        Log.e(TAG, "start failed", error)
        runCatching { stopInternal() }
        Bundle().apply {
            putBoolean("success", false)
            putInt("remoteUid", Process.myUid())
            putString("exception", "${error.javaClass.name}: ${error.message}")
        }
    }

    @Synchronized
    override fun sendTouch(action: Int, x: Float, y: Float, pressure: Float,
        eventTimeNanos: Long): Bundle =
        EmbeddedAppVdm.sendDirectTouch(action, x, y, pressure, eventTimeNanos)

    @Synchronized
    override fun stopSession(): Bundle = runCatching {
        stopInternal()
        Bundle().apply { putBoolean("success", true); putInt("remoteUid", Process.myUid()) }
    }.getOrElse {
        Bundle().apply { putBoolean("success", false); putString("exception", "${it.javaClass.name}: ${it.message}") }
    }

    private fun stopInternal() {
        var first: Throwable? = null
        try {
            val cleanup = EmbeddedAppVdm.cleanup()
            check(cleanup.getBoolean("success")) { cleanup.getString("exception") ?: "VDM cleanup failed" }
        } catch (error: Throwable) { first = error }
        try { AssociationShell.remove(association) }
        catch (error: Throwable) { if (first == null) first = error }
        finally {
            association = null
            EmbeddedAppVdm.finishCleanupAfterAssociation()
        }
        first?.let { throw it }
    }

    private fun waitForStableFingerConfig(displayId: Int) {
        var previous = ""
        var equal = 0
        val end = SystemClock.uptimeMillis() + 3_000
        while (SystemClock.uptimeMillis() < end) {
            val dump = ProcessBuilder("dumpsys", "window", "displays").redirectErrorStream(true)
                .start().inputStream.bufferedReader().use { it.readText() }
            val section = dump.substringAfter("Display: mDisplayId=$displayId", "")
                .substringBefore("Display: mDisplayId=")
            check(section.isNotEmpty())
            if (section.contains(" finger ") && section == previous) equal++ else equal = 0
            if (equal >= 1) return
            previous = section
            Thread.sleep(100)
        }
        error("VDM input configuration did not settle to touchscreen=finger")
    }

    override fun destroy() {
        runCatching { stopInternal() }
        exitProcess(0)
    }

    private companion object { const val TAG = "DWT.EmbeddedApp" }
}
