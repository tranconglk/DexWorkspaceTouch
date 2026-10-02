package com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime

import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppSessionId
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppTarget
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionSnapshot
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedTouchEvent

interface EmbeddedWorkspaceSessionFactory {
    fun create(
        target: EmbeddedAppTarget,
        observer: (EmbeddedSessionSnapshot) -> Unit,
    ): EmbeddedWorkspaceSessionHandle
}

interface EmbeddedWorkspaceSessionHandle {
    val sessionId: EmbeddedAppSessionId
    /** Bounded local/enqueue request. READY/FAILED/death arrive through the factory observer. */
    fun connect()
    fun start(surface: EmbeddedWorkspaceExecutionSurface)
    fun sendTouch(event: EmbeddedTouchEvent): Boolean
    fun stop()
    fun close()
    /** Drops consumers independently of pending IPC; does not acknowledge cleanup. */
    fun detachNotifications() {}
}

sealed interface EmbeddedWorkspaceTouchResult {
    data object Accepted : EmbeddedWorkspaceTouchResult

    data class Rejected(
        val sourceCellId: String,
        val reason: String,
    ) : EmbeddedWorkspaceTouchResult
}
