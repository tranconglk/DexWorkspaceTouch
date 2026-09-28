package com.trancong.dexworkspacetouch.feature.embeddedapp.remote

import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppServiceState
import com.trancong.dexworkspacetouch.feature.embeddedapp.RemoteSessionPhase

interface RemoteSessionHandle {
    var phase: RemoteSessionPhase
    var hasLiveResources: Boolean
    fun touch()
    fun stop()
}

class RemoteSessionRegistry(
    private val factory: (String) -> RemoteSessionHandle,
) {
    private val lock = Any()
    private val entries = linkedMapOf<String, RemoteSessionHandle>()

    fun tryReserve(sessionId: String): Boolean = synchronized(lock) {
        if (sessionId.isBlank() || entries.containsKey(sessionId)) false
        else { entries[sessionId] = factory(sessionId); true }
    }

    fun reserve(sessionId: String): RemoteSessionHandle {
        check(tryReserve(sessionId)) { "Duplicate or invalid sessionId" }
        return require(sessionId)
    }

    fun require(sessionId: String): RemoteSessionHandle = synchronized(lock) {
        entries[sessionId] ?: throw NoSuchElementException("Unknown sessionId")
    }

    fun stop(sessionId: String): Boolean {
        val entry = require(sessionId)
        synchronized(entry) {
            if (entry.phase != RemoteSessionPhase.STOPPED) entry.stop()
        }
        return true
    }

    fun snapshot(): List<RemoteSessionHandle> = synchronized(lock) { entries.values.toList() }

    fun serviceState(): EmbeddedAppServiceState {
        val values = snapshot()
        return EmbeddedAppServiceState(
            activeSessionCount = values.count { it.phase == RemoteSessionPhase.ACTIVE },
            startingSessionCount = values.count { it.phase in setOf(RemoteSessionPhase.RESERVED, RemoteSessionPhase.STARTING) },
            stoppingSessionCount = values.count { it.phase == RemoteSessionPhase.STOPPING },
            liveResourceSessionCount = values.count { it.hasLiveResources },
        )
    }
}
