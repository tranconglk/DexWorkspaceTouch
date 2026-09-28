package com.trancong.dexworkspacetouch.feature.embeddedapp.remote

import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.view.Surface
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppGeometry
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppTarget
import com.trancong.dexworkspacetouch.feature.embeddedapp.RemoteSessionPhase
import com.trancong.dexworkspacetouch.feature.embeddedapp.virtualInputDeviceName

class RemoteSessionRuntime(sessionId: String) : RemoteSessionHandle {
    override var phase = RemoteSessionPhase.RESERVED
    override var hasLiveResources = false
    private var association: SessionAssociation? = null
    val inputDeviceName = virtualInputDeviceName(sessionId)
    private val vdm = EmbeddedAppVdm(inputDeviceName)

    @Synchronized fun start(surface: Surface, packageName: String, componentName: String,
        width: Int, height: Int, densityDpi: Int): Bundle {
        check(phase == RemoteSessionPhase.RESERVED)
        phase = RemoteSessionPhase.STARTING
        return try {
            val target = EmbeddedAppTarget(packageName, componentName,
                EmbeddedAppGeometry(width, height, densityDpi))
            association = AssociationShell.create().also { hasLiveResources = true }
            val display = vdm.create(surface, target, checkNotNull(association).id)
            check(display.getBoolean("success")) { display.getString("exception") ?: "Display creation failed" }
            val input = vdm.prepareTouchscreen()
            check(input.getBoolean("success")) { input.getString("exception") ?: "Touchscreen creation failed" }
            waitForStableFingerConfig(display.getInt("displayId"))
            val launch = vdm.launchTarget()
            check(launch.getBoolean("success")) { launch.getString("exception") ?: "Target launch failed" }
            phase = RemoteSessionPhase.ACTIVE
            result(true).apply {
                putInt("associationId", checkNotNull(association).id)
                putString("associationMac", checkNotNull(association).mac)
                putInt("deviceId", display.getInt("deviceId")); putInt("displayId", display.getInt("displayId"))
                putInt("inputDeviceId", input.getInt("inputDeviceId"))
                putString("inputDeviceName", inputDeviceName)
                putInt("launchResult", launch.getInt("result"))
            }
        } catch (error: Throwable) {
            phase = RemoteSessionPhase.FAILED
            runCatching { stop() }
            failure(error)
        }
    }

    @Synchronized fun sendTouch(action: Int, x: Float, y: Float, pressure: Float, time: Long) =
        if (phase == RemoteSessionPhase.ACTIVE) vdm.sendDirectTouch(action, x, y, pressure, time)
        else failure(IllegalStateException("Session is not active"))

    override fun touch() = Unit

    @Synchronized override fun stop() {
        if (phase == RemoteSessionPhase.STOPPED) return
        phase = RemoteSessionPhase.STOPPING
        var first: Throwable? = null
        try { val value = vdm.cleanup(); check(value.getBoolean("success")) { value.getString("exception") ?: "VDM cleanup failed" } }
        catch (error: Throwable) { first = error }
        try { AssociationShell.remove(association) } catch (error: Throwable) { if (first == null) first = error }
        finally { association = null; vdm.finishCleanupAfterAssociation() }
        hasLiveResources = first != null
        phase = if (first == null) RemoteSessionPhase.STOPPED else RemoteSessionPhase.FAILED
        first?.let { throw it }
    }

    private fun waitForStableFingerConfig(displayId: Int) {
        var previous = ""; var equal = 0; val end = SystemClock.uptimeMillis() + 3_000
        while (SystemClock.uptimeMillis() < end) {
            val dump = ProcessBuilder("dumpsys", "window", "displays").redirectErrorStream(true)
                .start().inputStream.bufferedReader().use { it.readText() }
            val section = dump.substringAfter("Display: mDisplayId=$displayId", "").substringBefore("Display: mDisplayId=")
            check(section.isNotEmpty())
            if (section.contains(" finger ") && section == previous) equal++ else equal = 0
            if (equal >= 1) return
            previous = section; Thread.sleep(100)
        }
        error("VDM input configuration did not settle to touchscreen=finger")
    }

    private fun result(success: Boolean) = Bundle().apply { putBoolean("success", success); putInt("remoteUid", Process.myUid()) }
    private fun failure(error: Throwable) = result(false).apply { putString("exception", "${error.javaClass.name}: ${error.message}") }
}
