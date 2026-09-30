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
    fun connect()
    fun start(surface: EmbeddedWorkspaceExecutionSurface)
    fun sendTouch(event: EmbeddedTouchEvent): Boolean
    fun stop()
    fun close()
}

sealed interface EmbeddedWorkspaceTouchResult {
    data object Accepted : EmbeddedWorkspaceTouchResult

    data class Rejected(
        val sourceCellId: String,
        val reason: String,
    ) : EmbeddedWorkspaceTouchResult
}
