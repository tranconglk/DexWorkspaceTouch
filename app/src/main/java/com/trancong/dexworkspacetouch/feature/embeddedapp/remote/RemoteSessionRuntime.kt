package com.trancong.dexworkspacetouch.feature.embeddedapp.remote

import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.view.Surface
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppGeometry
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppTarget
import com.trancong.dexworkspacetouch.feature.embeddedapp.RemoteSessionPhase
import com.trancong.dexworkspacetouch.feature.embeddedapp.virtualInputDeviceName
import com.trancong.dexworkspacetouch.diagnostics.embedded.EmbeddedEvidence

class RemoteSessionRuntime(sessionId: String) : RemoteSessionHandle {
    override var phase = RemoteSessionPhase.RESERVED
    override var hasLiveResources = false
    private var association: SessionAssociation? = null
    val inputDeviceName = virtualInputDeviceName(sessionId)
    private val diagnosticSid = sessionId
    private val vdm = EmbeddedAppVdm(inputDeviceName, diagnosticSid)

    @Synchronized fun start(surface: Surface, packageName: String, componentName: String,
        width: Int, height: Int, densityDpi: Int): Bundle {
        check(phase == RemoteSessionPhase.RESERVED)
        phase = RemoteSessionPhase.STARTING
        EmbeddedEvidence.observe { EmbeddedEvidence.remote("start.entry", diagnosticSid, mapOf(
            "surface_identity" to System.identityHashCode(surface).toString(), "surface_valid" to surface.isValid.toString())) }
        var stage = "target_geometry"
        return try {
            val target = EmbeddedAppTarget(packageName, componentName,
                EmbeddedAppGeometry(width, height, densityDpi))
            stage = "association"
            association = EmbeddedEvidence.remoteStep(diagnosticSid, stage) { AssociationShell.create(diagnosticSid) }.also { hasLiveResources = true }
            EmbeddedEvidence.remote("allocation.association", diagnosticSid, mapOf("returned" to "true",
                "association_id" to checkNotNull(association).id.toString(), "id_observed" to "true"))
            stage = "display_create"
            val display = EmbeddedEvidence.remoteStep(diagnosticSid, stage) { vdm.create(surface, target, checkNotNull(association).id) }
            check(display.getBoolean("success")) { display.getString("exception") ?: "Display creation failed" }
            stage = "touchscreen_prepare"
            val input = EmbeddedEvidence.remoteStep(diagnosticSid, stage) { vdm.prepareTouchscreen() }
            check(input.getBoolean("success")) { input.getString("exception") ?: "Touchscreen creation failed" }
            stage = "input_stability"
            EmbeddedEvidence.remoteStep(diagnosticSid, stage) { waitForStableFingerConfig(display.getInt("displayId")) }
            stage = "target_launch"
            val launch = EmbeddedEvidence.remoteStep(diagnosticSid, stage) { vdm.launchTarget() }
            check(launch.getBoolean("success")) { launch.getString("exception") ?: "Target launch failed" }
            phase = RemoteSessionPhase.ACTIVE
            EmbeddedEvidence.remote("start.success", diagnosticSid, mapOf("stage" to stage))
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
            EmbeddedEvidence.observe { EmbeddedEvidence.remote("start.failure", diagnosticSid,
                EmbeddedEvidence.errorFields(error) + ("stage" to stage)) }
            EmbeddedEvidence.remote("rollback.begin", diagnosticSid)
            runCatching { stop() }
                .onSuccess { EmbeddedEvidence.remote("rollback.end", diagnosticSid, mapOf("success" to "true")) }
                .onFailure { rollback -> EmbeddedEvidence.observe { EmbeddedEvidence.remote("rollback.failure", diagnosticSid,
                    EmbeddedEvidence.errorFields(rollback)) } }
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
        try { val value = EmbeddedEvidence.remoteStep(diagnosticSid, "rollback_vdm") { vdm.cleanup() }; check(value.getBoolean("success")) { value.getString("exception") ?: "VDM cleanup failed" } }
        catch (error: Throwable) { first = error; EmbeddedEvidence.observe { EmbeddedEvidence.remote("rollback.vdm.failure", diagnosticSid, EmbeddedEvidence.errorFields(error)) } }
        try { EmbeddedEvidence.remoteStep(diagnosticSid, "rollback_association") { AssociationShell.remove(association, diagnosticSid) } }
        catch (error: Throwable) { if (first == null) first = error }
        finally {
            EmbeddedEvidence.remote("allocation.association_before_reset", diagnosticSid, mapOf(
                "association_returned" to (association != null).toString(),
                "association_id" to (association?.id?.toString() ?: "NOT_VERIFIED"), "observed_before_reset" to "true"))
            association = null; vdm.finishCleanupAfterAssociation()
        }
        hasLiveResources = first != null
        phase = if (first == null) RemoteSessionPhase.STOPPED else RemoteSessionPhase.FAILED
        EmbeddedEvidence.remote("rollback.state", diagnosticSid, mapOf("phase" to phase.name,
            "live_resources_flag" to hasLiveResources.toString()))
        first?.let { throw it }
    }

    private fun waitForStableFingerConfig(displayId: Int) {
        var previous = ""; var equal = 0; val end = SystemClock.uptimeMillis() + 3_000
        var sample = 0
        while (SystemClock.uptimeMillis() < end) {
            val dump = ProcessBuilder("dumpsys", "window", "displays").redirectErrorStream(true)
                .start().inputStream.bufferedReader().use { it.readText() }
            val section = dump.substringAfter("Display: mDisplayId=$displayId", "").substringBefore("Display: mDisplayId=")
            EmbeddedEvidence.observe { EmbeddedEvidence.remote("input.sample", diagnosticSid, mapOf(
                "stage" to "input_stability", "display_id" to displayId.toString(), "sample_index" to (++sample).toString(),
                "section_length" to section.length.toString(), "has_section" to section.isNotEmpty().toString(),
                "has_finger" to section.contains(" finger ").toString(), "equals_previous" to (section == previous).toString(),
                "equal_before" to equal.toString(), "remaining_ms" to (end - SystemClock.uptimeMillis()).toString())) }
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
