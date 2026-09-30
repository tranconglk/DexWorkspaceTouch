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
    private val lifecycleChanged: (EmbeddedSessionSnapshot) -> Unit = {},
    private val changed: (EmbeddedAppState) -> Unit,
) {
    val sessionId = newEmbeddedAppSessionId()
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val stopCoordinator = SessionStopCoordinator()
    private val connectionCoordinator = SessionConnectionCoordinator()
    private val lifecycle = EmbeddedAppLifecycleCoordinator()
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
            lifecycle.serviceReady()
            publishLifecycle()
            val uid = runCatching { service.uid }.getOrDefault(-1)
            update(state.copy(shellReady = uid == 2000, busy = false,
                status = if (uid == 2000) "Shell UID 2000 ready" else "Unexpected remote UID $uid"))
        }
        override fun onServiceDisconnected() {
            connectionCoordinator.serviceDied()
            lifecycle.remoteDied()
            publishLifecycle()
            remote = null
            update(EmbeddedAppState(status = "Shell UserService disconnected / REMOTE_DIED"))
        }
    }

    fun start() { changed(state) }

    fun connect() {
        if (closed) return
        if (!connectionCoordinator.beginConnect()) return
        lifecycle.beginConnect()
        publishLifecycle()
        update(state.copy(busy = true, status = "Connecting shell UserService"))
        val owned = lease ?: manager.acquire(sessionId, listener).also { lease = it }
        owned.connect()
    }

    fun startSession(surface: Surface) {
        val service = remote ?: return rejectStart("REMOTE_UNAVAILABLE", "Connect Shizuku first")
        if (runCatching { surface.isValid }.getOrDefault(false).not()) {
            return rejectStart("SURFACE_INVALID", "Surface is not valid")
        }
        val ownedLease = lease ?: return rejectStart("START_REJECTED", "Connection lease unavailable")
        if (!lifecycle.beginStart()) return
        publishLifecycle()
        update(state.copy(busy = true, status = "Creating trusted VDM session"))
        ownedLease.operationStarted()
        worker.execute {
            val geometry = target.geometry
            val result = runCatching { service.startSession(sessionId.value, surface, target.packageName,
                target.componentName, geometry.width, geometry.height, geometry.densityDpi) }
            // A failed remote call can still have reserved the session or acquired resources.
            startedRemotely = true
            ownedLease.operationFinished(terminal = false)
            main.post {
                result.fold({ bundle ->
                    if (bundle.getBoolean("success")) {
                        if (lifecycle.startSucceeded(bundle.getInt("displayId"))) {
                            publishLifecycle()
                            update(state.copy(busy = false, active = true, displayId = bundle.getInt("displayId"),
                                status = "App launched on trusted display ${bundle.getInt("displayId")}"))
                        }
                    } else {
                        val message = bundle.getString("exception") ?: "Session start failed"
                        if (lifecycle.startFailed(EmbeddedSessionFailure("START_FAILED", message))) {
                            publishLifecycle()
                            update(state.copy(busy = false, status = message))
                        }
                    }
                }, {
                    if (lifecycle.startFailed(EmbeddedSessionFailure("START_FAILED", it.message))) {
                        publishLifecycle()
                        update(state.copy(busy = false, status = "Session start failed: ${it.message}"))
                    }
                })
            }
        }
    }

    fun sendTouch(event: EmbeddedTouchEvent) = touch(
        event.action,
        event.x,
        event.y,
        event.pressure,
        event.eventTimeNanos,
    )

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
        if (!lifecycle.requestStop()) return
        publishLifecycle()
        if (service == null) {
            lifecycle.cleanupFailed(EmbeddedSessionFailure("REMOTE_UNAVAILABLE", "Remote service unavailable"))
            publishLifecycle()
            return update(state.copy(active = false, displayId = -1))
        }
        if (!stopCoordinator.requestStop()) return
        val ownedLease = lease ?: return
        ownedLease.operationStarted()
        update(state.copy(busy = true, status = "Stopping embedded session"))
        val generation = manager.generationFor(service)
        worker.execute {
            val result = runCatching { service.stopSession(sessionId.value) }.getOrNull()
            val stopped = result?.getBoolean("success") == true
            stopCoordinator.stopCompleted()
            ownedLease.operationFinished(terminal = stopped)
            val verifiedAbsent = !stopped &&
                manager.reconcileAbsentSession(ownedLease, service, generation)
            startedRemotely = !stopped && !verifiedAbsent
            if (!stopped && !verifiedAbsent) {
                lifecycle.cleanupFailed(EmbeddedSessionFailure(
                    "CLEANUP_FAILED",
                    result?.getString("exception") ?: "Cleanup failed",
                ))
                publishLifecycle()
            }
            main.post { update(EmbeddedAppState(shellReady = true,
                status = if (stopped || verifiedAbsent) "Embedded session stopped"
                else result?.getString("exception") ?: "Cleanup failed")) }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        lifecycle.requestClose()
        publishLifecycle()
        stopCoordinator.requestClose()
        worker.execute {
            val ownedLease = lease
            var cleanupSucceeded = !startedRemotely
            if (startedRemotely && stopCoordinator.requestStop() && ownedLease != null) {
                ownedLease.operationStarted()
                val service = remote
                val generation = service?.let(manager::generationFor)
                val stopped = runCatching { service?.stopSession(sessionId.value) }.getOrNull()
                    ?.getBoolean("success") == true
                stopCoordinator.stopCompleted()
                ownedLease.operationFinished(terminal = stopped)
                val verifiedAbsent = !stopped && service != null &&
                    manager.reconcileAbsentSession(ownedLease, service, generation)
                startedRemotely = !stopped && !verifiedAbsent
                cleanupSucceeded = stopped || verifiedAbsent
            }
            if (stopCoordinator.mayReleaseLease) {
                ownedLease?.close()
                lease = null
                if (cleanupSucceeded) lifecycle.cleanupSucceeded(leaseReleased = true)
                else lifecycle.cleanupFailed(EmbeddedSessionFailure("CLEANUP_FAILED", "Remote cleanup failed"))
                publishLifecycle()
            }
        }
        worker.shutdown()
    }

    private fun update(value: EmbeddedAppState) { state = value; if (!closed) changed(value) }
    private fun rejectStart(code: String, message: String) {
        if (lifecycle.rejectStart(EmbeddedSessionFailure(code, message))) {
            publishLifecycle()
            update(state.copy(busy = false, status = message))
        }
    }
    private fun publishLifecycle() = lifecycleChanged(lifecycle.snapshot)
}
