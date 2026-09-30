package com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime

import android.content.Context
import android.view.Surface
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppSession
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppSessionId
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppTarget
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionSnapshot
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedTouchEvent

interface AndroidEmbeddedAppSessionProvider {
    fun create(
        applicationContext: Context,
        target: EmbeddedAppTarget,
        observer: (EmbeddedSessionSnapshot) -> Unit,
    ): AndroidEmbeddedAppSession
}

interface AndroidEmbeddedAppSession {
    val sessionId: EmbeddedAppSessionId

    fun connect()
    fun startSession(surface: Surface)
    fun sendTouch(event: EmbeddedTouchEvent)
    fun stop()
    fun close()
}

class AndroidEmbeddedWorkspaceSessionFactory(
    private val applicationContext: Context,
    private val sessionProvider: AndroidEmbeddedAppSessionProvider = ProductionAndroidEmbeddedAppSessionProvider,
) : EmbeddedWorkspaceSessionFactory {
    override fun create(
        target: EmbeddedAppTarget,
        observer: (EmbeddedSessionSnapshot) -> Unit,
    ): EmbeddedWorkspaceSessionHandle {
        val session = sessionProvider.create(applicationContext, target, observer)
        return AndroidEmbeddedWorkspaceSessionHandle(session)
    }
}

private object ProductionAndroidEmbeddedAppSessionProvider : AndroidEmbeddedAppSessionProvider {
    override fun create(
        applicationContext: Context,
        target: EmbeddedAppTarget,
        observer: (EmbeddedSessionSnapshot) -> Unit,
    ): AndroidEmbeddedAppSession {
        val session = EmbeddedAppSession(
            context = applicationContext,
            target = target,
            lifecycleChanged = observer,
            changed = {},
        )
        return ProductionAndroidEmbeddedAppSession(session)
    }
}

private class ProductionAndroidEmbeddedAppSession(
    private val delegate: EmbeddedAppSession,
) : AndroidEmbeddedAppSession {
    override val sessionId: EmbeddedAppSessionId
        get() = delegate.sessionId

    override fun connect() = delegate.connect()

    override fun startSession(surface: Surface) = delegate.startSession(surface)

    override fun sendTouch(event: EmbeddedTouchEvent) = delegate.sendTouch(event)

    override fun stop() = delegate.stop()

    override fun close() = delegate.close()
}

private class AndroidEmbeddedWorkspaceSessionHandle(
    private val session: AndroidEmbeddedAppSession,
) : EmbeddedWorkspaceSessionHandle {
    override val sessionId: EmbeddedAppSessionId
        get() = session.sessionId

    override fun connect() = session.connect()

    override fun start(surface: EmbeddedWorkspaceExecutionSurface) {
        require(surface is AndroidEmbeddedWorkspaceExecutionSurface) {
            "Expected AndroidEmbeddedWorkspaceExecutionSurface"
        }
        session.startSession(surface.surface)
    }

    override fun sendTouch(event: EmbeddedTouchEvent): Boolean {
        session.sendTouch(event)
        return true
    }

    override fun stop() = session.stop()

    override fun close() = session.close()
}
