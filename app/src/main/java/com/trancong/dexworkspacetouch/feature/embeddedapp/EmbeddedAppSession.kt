package com.trancong.dexworkspacetouch.feature.embeddedapp

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.trancong.dexworkspacetouch.feature.embeddedapp.remote.IEmbeddedAppService
import java.util.concurrent.Executors

data class EmbeddedAppState(
    val shellReady: Boolean = false,
    val busy: Boolean = false,
    val active: Boolean = false,
    val status: String = "Connect Shizuku to begin",
    val displayId: Int = -1,
)

class EmbeddedAppSession(
    context: Context,
    private val target: EmbeddedAppTarget,
    private val changed: (EmbeddedAppState) -> Unit,
) {
    val sessionId = newEmbeddedAppSessionId()
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val stopCoordinator = SessionStopCoordinator()
    private val connectionCoordinator = SessionConnectionCoordinator()
    private val manager = EmbeddedAppServiceConnectionManager.get(context)
    private var lease: EmbeddedAppServiceLease? = null
    private var state = EmbeddedAppState()
    private var remote: IEmbeddedAppService? = null
    private var closed = false
    @Volatile private var startedRemotely = false
    private val listener = object : EmbeddedAppServiceListener {
        override fun onServiceReady(service: IEmbeddedAppService) {
            connectionCoordinator.serviceReady()
            remote = service
            val uid = runCatching { service.uid }.getOrDefault(-1)
            update(state.copy(shellReady = uid == 2000, busy = false,
                status = if (uid == 2000) "Shell UID 2000 ready" else "Unexpected remote UID $uid"))
        }
        override fun onServiceDisconnected() {
            connectionCoordinator.serviceDied()
            remote = null
            update(EmbeddedAppState(status = "Shell UserService disconnected / REMOTE_DIED"))
        }
    }

    fun start() { changed(state) }

    fun connect() {
        if (closed) return
        if (!connectionCoordinator.beginConnect()) return
        update(state.copy(busy = true, status = "Connecting shell UserService"))
        val owned = lease ?: manager.acquire(sessionId, listener).also { lease = it }
        owned.connect()
    }

    fun startSession(surface: Surface) {
        val service = remote ?: return update(state.copy(status = "Connect Shizuku first"))
        if (!surface.isValid) return update(state.copy(status = "Surface is not valid"))
        update(state.copy(busy = true, status = "Creating trusted VDM session"))
        val ownedLease = lease ?: return update(state.copy(busy = false, status = "Connect Shizuku first"))
        ownedLease.operationStarted()
        worker.execute {
            val geometry = target.geometry
            val result = runCatching { service.startSession(sessionId.value, surface, target.packageName,
                target.componentName, geometry.width, geometry.height, geometry.densityDpi) }
            startedRemotely = result.getOrNull()?.getBoolean("success") == true
            ownedLease.operationFinished(terminal = !startedRemotely)
            main.post {
                result.fold({ bundle ->
                    if (bundle.getBoolean("success")) {
                        update(state.copy(busy = false, active = true, displayId = bundle.getInt("displayId"),
                            status = "App launched on trusted display ${bundle.getInt("displayId")}"))
                    } else update(state.copy(busy = false,
                        status = bundle.getString("exception") ?: "Session start failed"))
                }, { update(state.copy(busy = false, status = "Session start failed: ${it.message}")) })
            }
        }
    }

    fun touch(action: Int, x: Float, y: Float, pressure: Float, time: Long) {
        val service = remote ?: return
        if (!state.active) return
        worker.execute {
            val result = runCatching { service.sendTouch(sessionId.value, action, x, y, pressure, time) }.getOrNull()
            if (result?.getBoolean("success") != true) main.post {
                update(state.copy(status = result?.getString("exception") ?: "Touch delivery failed"))
            }
        }
    }

    fun stop() {
        val service = remote
        if (service == null) return update(state.copy(active = false, displayId = -1))
        if (!stopCoordinator.requestStop()) return
        val ownedLease = lease ?: return
        ownedLease.operationStarted()
        update(state.copy(busy = true, status = "Stopping embedded session"))
        worker.execute {
            val result = runCatching { service.stopSession(sessionId.value) }.getOrNull()
            val stopped = result?.getBoolean("success") == true
            startedRemotely = !stopped
            stopCoordinator.stopCompleted()
            ownedLease.operationFinished(terminal = stopped)
            main.post { update(EmbeddedAppState(shellReady = true,
                status = if (result?.getBoolean("success") == true) "Embedded session stopped"
                else result?.getString("exception") ?: "Cleanup failed")) }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        stopCoordinator.requestClose()
        worker.execute {
            val ownedLease = lease
            if (startedRemotely && stopCoordinator.requestStop() && ownedLease != null) {
                ownedLease.operationStarted()
                val stopped = runCatching { remote?.stopSession(sessionId.value) }.getOrNull()
                    ?.getBoolean("success") == true
                startedRemotely = !stopped
                stopCoordinator.stopCompleted()
                ownedLease.operationFinished(terminal = stopped)
            }
            if (stopCoordinator.mayReleaseLease) { ownedLease?.close(); lease = null }
        }
        worker.shutdown()
    }

    private fun update(value: EmbeddedAppState) { state = value; if (!closed) changed(value) }
}
