package com.trancong.dexworkspacetouch.feature.embeddedapp.remote

import android.os.Bundle
import android.os.Process
import android.view.Surface
import com.trancong.dexworkspacetouch.feature.embeddedapp.RemoteSessionPhase
import kotlin.system.exitProcess

class EmbeddedAppUserService : IEmbeddedAppService.Stub() {
    private val registry = RemoteSessionRegistry { sessionId -> RemoteSessionRuntime(sessionId) }
    override fun getUid() = Process.myUid()

    override fun startSession(sessionId: String, surface: Surface, packageName: String,
        componentName: String, width: Int, height: Int, densityDpi: Int): Bundle {
        if (!registry.tryReserve(sessionId)) return failure(sessionId, "DUPLICATE_OR_INVALID_SESSION")
        return (registry.require(sessionId) as RemoteSessionRuntime)
            .start(surface, packageName, componentName, width, height, densityDpi)
            .apply { putString("sessionId", sessionId) }
    }

    override fun sendTouch(sessionId: String, action: Int, x: Float, y: Float,
        pressure: Float, eventTimeNanos: Long): Bundle = runCatching {
        (registry.require(sessionId) as RemoteSessionRuntime)
            .sendTouch(action, x, y, pressure, eventTimeNanos).apply { putString("sessionId", sessionId) }
    }.getOrElse { failure(sessionId, "UNKNOWN_SESSION", it) }

    override fun stopSession(sessionId: String): Bundle = runCatching {
        registry.stop(sessionId); success(sessionId)
    }.getOrElse { failure(sessionId, "STOP_FAILED", it) }

    override fun getSessionState(sessionId: String): Bundle = runCatching {
        val runtime = registry.require(sessionId)
        success(sessionId).apply {
            putString("phase", runtime.phase.name)
            putBoolean("liveResources", runtime.hasLiveResources)
        }
    }.getOrElse { failure(sessionId, "UNKNOWN_SESSION", it) }

    override fun getServiceState(): Bundle {
        val state = registry.serviceState()
        return Bundle().apply {
            putBoolean("success", true); putInt("remoteUid", Process.myUid())
            putInt("activeSessionCount", state.activeSessionCount)
            putInt("startingSessionCount", state.startingSessionCount)
            putInt("stoppingSessionCount", state.stoppingSessionCount)
            putInt("liveResourceSessionCount", state.liveResourceSessionCount)
        }
    }

    override fun destroy() {
        registry.snapshot().forEach { runCatching { if (it.phase != RemoteSessionPhase.STOPPED) it.stop() } }
        exitProcess(0)
    }

    private fun success(sessionId: String) = Bundle().apply {
        putBoolean("success", true); putInt("remoteUid", Process.myUid()); putString("sessionId", sessionId)
    }
    private fun failure(sessionId: String, code: String, error: Throwable? = null) = Bundle().apply {
        putBoolean("success", false); putInt("remoteUid", Process.myUid()); putString("sessionId", sessionId)
        putString("failureCode", code); putString("exception", error?.let { "${it.javaClass.name}: ${it.message}" } ?: code)
    }
}
