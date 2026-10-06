package com.trancong.dexworkspacetouch.feature.embeddedapp
import com.trancong.dexworkspacetouch.diagnostics.embedded.EmbeddedEvidence

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.trancong.dexworkspacetouch.feature.embeddedapp.remote.IEmbeddedAppService
import java.util.concurrent.Executors
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService

internal data class UidCompletion(val identity: ConnectionIdentity, val operation: Long, val uid: Int?, val error: String?)

/** No session, listener, UI callback or consumer is loaded across the Binder call. */
internal class UidVerificationTask(
    private val service: IEmbeddedAppService,
    private val identity: ConnectionIdentity,
    private val operation: Long,
    private val destination: DetachableMailbox<UidCompletion>,
) : Runnable {
    override fun run() {
        val value = try {
            UidCompletion(identity, operation, service.uid, null)
        } catch (_: Exception) {
            UidCompletion(identity, operation, null, "UID_VERIFICATION_FAILED")
        }
        destination.offer(value)
    }
}

private fun embeddedMainExecutor(): Executor {
    val handler = Handler(Looper.getMainLooper())
    return Executor { task -> handler.post(task) }
}

data class EmbeddedAppState(
    val shellReady: Boolean = false,
    val busy: Boolean = false,
    val active: Boolean = false,
    val status: String = "Connect Shizuku to begin",
    val displayId: Int = -1,
)

class EmbeddedAppSession internal constructor(
    private val target: EmbeddedAppTarget,
    private val manager: EmbeddedAppServiceConnectionManager,
    private val main: Executor,
    private val worker: ExecutorService,
    private val verifier: WorkAdmission,
    lifecycleChanged: (EmbeddedSessionSnapshot) -> Unit = {},
    changed: (EmbeddedAppState) -> Unit,
    private val startLane: WorkAdmission = EmbeddedConnectionLanes.start,
    private val startCompletionExecutor: Executor = EmbeddedConnectionLanes.notificationExecutor,
) {
    constructor(
        context: Context,
        target: EmbeddedAppTarget,
        lifecycleChanged: (EmbeddedSessionSnapshot) -> Unit = {},
        changed: (EmbeddedAppState) -> Unit,
    ) : this(target, EmbeddedAppServiceConnectionManager.get(context), embeddedMainExecutor(),
        Executors.newSingleThreadExecutor(), EmbeddedConnectionLanes.verifier, lifecycleChanged, changed)

    val sessionId = newEmbeddedAppSessionId()
    private val stateLock = Any()
    private sealed interface Notification {
        data class Lifecycle(val value: EmbeddedSessionSnapshot) : Notification
        data class State(val value: EmbeddedAppState) : Notification
    }
    private val notifications = DetachableMailbox<Notification>(main) { event ->
        when (event) {
            is Notification.Lifecycle -> lifecycleChanged(event.value)
            is Notification.State -> changed(event.value)
        }
    }
    private val stopCoordinator = SessionStopCoordinator()
    private val connectionCoordinator = SessionConnectionCoordinator()
    private val lifecycle = EmbeddedAppLifecycleCoordinator()
    @Volatile private var lease: EmbeddedAppServiceLease? = null
    @Volatile private var state = EmbeddedAppState()
    @Volatile private var remote: IEmbeddedAppService? = null
    @Volatile private var closed = false
    @Volatile private var stopping = false
    private var connectionOperation = 0L
    private var candidate: EmbeddedConnectionEvent.Ready? = null
    private var uidMailbox: DetachableMailbox<UidCompletion>? = null
    @Volatile private var startSubmitted = false
    @Volatile private var startedRemotely = false
    @Volatile private var cleanupConfirmed = false
    private var startOperation: Long? = null
    private var startMailbox: DetachableMailbox<StartCompletion>? = null
    private val ingress = DetachableMailbox<EmbeddedConnectionEvent>(main, ::onConnectionEvent)
    private val listener = object : EmbeddedAppServiceListener {
        override fun onConnectionEvent(event: EmbeddedConnectionEvent) { ingress.offer(event) }
    }

    fun start() { notifications.offer(Notification.State(state)) }

    fun connect() {
        val owned = try {
            synchronized(stateLock) {
                if (closed || stopping || !connectionCoordinator.beginConnect()) return
                lifecycle.beginConnect()
                connectionOperation++
                lease ?: manager.acquire(sessionId, listener).also { lease = it }
            }
        } catch (_: IllegalStateException) {
            connectionRejected("CONNECTION_ADMISSION_REJECTED")
            return
        }
        publishLifecycle()
        update(state.copy(busy = true, status = "Connecting shell UserService"))
        try {
            owned.connect()
        } catch (_: IllegalStateException) {
            connectionRejected("CONNECTION_REQUEST_REJECTED")
        }
    }

    private fun connectionRejected(code: String) {
        synchronized(stateLock) {
            invalidateConnection()
            lifecycle.connectionFailed(EmbeddedSessionFailure(code, "Connection request rejected"))
        }
        publishLifecycle()
        update(state.copy(busy = false, status = "Connection request rejected"))
    }

    private fun onConnectionEvent(event: EmbeddedConnectionEvent) {
        EmbeddedEvidence.app("session.service_operation", sessionId.value, fields = mapOf(
            "service_generation" to event.identity.generation.toString(), "service_operation" to event.identity.operation.toString()))
        var task: UidVerificationTask? = null
        synchronized(stateLock) {
            if (lease?.accepts(event) != true) return
            when (event) {
                is EmbeddedConnectionEvent.Ready -> {
                    if (closed || stopping) return
                    if (lifecycle.snapshot.phase != EmbeddedSessionPhase.CONNECTING) return
                    uidMailbox?.detach()
                    candidate = event
                    val operation = ++connectionOperation
                    val mailbox = DetachableMailbox<UidCompletion>(main, ::onUidCompleted)
                    uidMailbox = mailbox
                    task = UidVerificationTask(event.service, event.identity, operation, mailbox)
                }
                is EmbeddedConnectionEvent.Disconnected -> {
                    invalidateConnection()
                    remote = null
                    connectionCoordinator.serviceDied()
                    lifecycle.remoteDied()
                }
                is EmbeddedConnectionEvent.Failed -> {
                    if (closed || stopping) return
                    invalidateConnection()
                    lifecycle.connectionFailed(EmbeddedSessionFailure(event.code, "Connection request failed"))
                }
            }
        }
        if (task != null) {
            if (!verifier.tryExecute(checkNotNull(task))) {
                onUidCompleted(UidCompletion(event.identity, connectionOperation, null, "UID_CAPACITY_EXHAUSTED"))
            }
        } else {
            publishLifecycle()
            update(state.copy(shellReady = false, busy = false, status = "Shell UserService unavailable"))
        }
    }

    private fun onUidCompleted(value: UidCompletion) {
        synchronized(stateLock) {
            val exact = candidate ?: return
            if (closed || stopping || value.operation != connectionOperation || exact.identity != value.identity ||
                lease?.accepts(exact) != true || lifecycle.snapshot.phase != EmbeddedSessionPhase.CONNECTING) return
            if (!exact.readiness.claim()) return
            uidMailbox?.detach(); uidMailbox = null
            if (value.error != null || value.uid != 2000) {
                lifecycle.connectionFailed(EmbeddedSessionFailure(value.error ?: "UNEXPECTED_SERVICE_UID",
                    "Exact UserService UID verification failed"))
                candidate = null
            } else {
                remote = exact.service
                connectionCoordinator.serviceReady()
                lifecycle.serviceReady()
            }
        }
        publishLifecycle()
        val ready = lifecycle.snapshot.phase == EmbeddedSessionPhase.READY
        update(state.copy(shellReady = ready, busy = false,
            status = if (ready) "Shell UID 2000 ready" else "UserService UID verification failed"))
    }

    /** Local invalidation does not interrupt/join Binder or claim cleanup success. */
    private fun invalidateConnection() {
        connectionOperation++
        uidMailbox?.detach(); uidMailbox = null; candidate = null
    }

    fun detachNotifications() {
        synchronized(stateLock) {
            stopping = true
            invalidateConnection()
            startMailbox?.detach(); startMailbox = null
        }
        ingress.detach()
        notifications.detach()
        lease?.detachNotifications()
    }

    fun startSession(surface: Surface) {
        EmbeddedEvidence.observe { EmbeddedEvidence.app("session.start", sessionId.value,
            fields = mapOf("surface_identity" to System.identityHashCode(surface).toString(), "surface_valid" to surface.isValid.toString())) }
        val (task, ownedLease) = synchronized(stateLock) {
            if (closed || stopping) return
            val service = remote ?: return rejectStart("REMOTE_UNAVAILABLE", "Connect Shizuku first")
            if (!runCatching { surface.isValid }.getOrDefault(false)) {
                return rejectStart("SURFACE_INVALID", "Surface is not valid")
            }
            val ownedLease = lease ?: return rejectStart("START_REJECTED", "Connection lease unavailable")
            if (!lifecycle.beginStart()) return
            // Record may-allocate before the lane can execute any Binder work.
            val operation = ownedLease.operationStarted()
            startSubmitted = true
            startOperation = operation
            EmbeddedEvidence.observe { EmbeddedEvidence.app("session.operation", sessionId.value, fields = buildMap {
                put("ipc_operation", operation.toString())
                manager.generationFor(service)?.let { put("service_generation", it.toString()) }
            }) }
            val destination = DetachableMailbox(startCompletionExecutor, ::onStartCompleted)
            startMailbox = destination
            val geometry = target.geometry
            StartIpcTask(service, sessionId.value, target.packageName, target.componentName,
                geometry.width, geometry.height, geometry.densityDpi, surface, operation, destination) to ownedLease
        }
        publishLifecycle()
        update(state.copy(busy = true, status = "Creating trusted VDM session"))
        if (!startLane.tryExecute(task)) {
            // Rejected admission guarantees that this task never entered Start transport.
            ownedLease.operationFinished(terminal = true, operation = task.operation)
            synchronized(stateLock) {
                if (startOperation != task.operation) return
                startSubmitted = false; startOperation = null
                startMailbox?.detach(); startMailbox = null
            }
            rejectStart("START_CAPACITY_EXHAUSTED", "Start execution capacity exhausted")
        }
    }

    private fun onStartCompleted(value: StartCompletion) {
        synchronized(stateLock) {
            if (startMailbox == null || startOperation != value.operation) {
                EmbeddedEvidence.app("session.fence", sessionId.value, fields = mapOf("ipc_operation" to value.operation.toString(), "disposition" to "rejected"))
                return
            }
            EmbeddedEvidence.app("session.fence", sessionId.value, fields = mapOf("ipc_operation" to value.operation.toString(), "disposition" to "matched"))
            startMailbox?.detach(); startMailbox = null; startOperation = null
            // Failure/exception may still have allocated. Detachment deliberately leaves this
            // operation pending in the manager instead of accepting a late completion as clean.
            startedRemotely = true
            lease?.operationFinished(terminal = false, operation = value.operation)
            if (closed || stopping) return
            if (value.success) {
                if (!lifecycle.startSucceeded(value.displayId)) return
                state = state.copy(busy = false, active = true, displayId = value.displayId,
                    status = "App launched on trusted display ${value.displayId}")
            } else {
                if (!lifecycle.startFailed(EmbeddedSessionFailure("START_FAILED", value.error))) return
                state = state.copy(busy = false, status = value.error ?: "Session start failed")
            }
        }
        publishLifecycle()
        notifications.offer(Notification.State(state))
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
            if (result?.getBoolean("success") != true) main.execute {
                update(state.copy(status = result?.getString("exception") ?: "Touch delivery failed"))
            }
        }
    }

    fun stop() {
        synchronized(stateLock) { stopping = true; invalidateConnection() }
        val service = remote
        if (!lifecycle.requestStop()) return
        publishLifecycle()
        // No Start was submitted: only Close's acknowledged local lease release can prove clean.
        if (!startSubmitted) return
        if (service == null) {
            lifecycle.cleanupFailed(EmbeddedSessionFailure("REMOTE_UNAVAILABLE", "Remote service unavailable"))
            publishLifecycle()
            return update(state.copy(active = false, displayId = -1))
        }
        if (!stopCoordinator.requestStop()) return
        val ownedLease = lease ?: return
        val operation = ownedLease.operationStarted()
        update(state.copy(busy = true, status = "Stopping embedded session"))
        val generation = manager.generationFor(service)
        worker.execute {
            val result = runCatching { service.stopSession(sessionId.value) }.getOrNull()
            val remoteStopped = result?.getBoolean("success") == true
            stopCoordinator.stopCompleted()
            val stopped = ownedLease.operationFinished(terminal = remoteStopped, operation = operation)
            val verifiedAbsent = !remoteStopped &&
                manager.reconcileAbsentSession(ownedLease, service, generation)
            cleanupConfirmed = stopped || verifiedAbsent
            startedRemotely = !stopped && !verifiedAbsent
            if (!stopped && !verifiedAbsent) {
                lifecycle.cleanupFailed(EmbeddedSessionFailure(
                    "CLEANUP_FAILED",
                    result?.getString("exception") ?: "Cleanup failed",
                ))
                publishLifecycle()
            }
            main.execute { update(EmbeddedAppState(shellReady = true,
                status = if (stopped || verifiedAbsent) "Embedded session stopped"
                else result?.getString("exception") ?: "Cleanup failed")) }
        }
    }

    fun close() {
        synchronized(stateLock) {
            if (closed) return
            closed = true; stopping = true; invalidateConnection()
        }
        lifecycle.requestClose()
        publishLifecycle()
        stopCoordinator.requestClose()
        worker.execute {
            val ownedLease = lease
            var cleanupSucceeded = !startSubmitted || cleanupConfirmed
            if (startSubmitted && !cleanupConfirmed && stopCoordinator.requestStop() && ownedLease != null) {
                val operation = ownedLease.operationStarted()
                val service = remote
                val generation = service?.let(manager::generationFor)
                val remoteStopped = runCatching { service?.stopSession(sessionId.value) }.getOrNull()
                    ?.getBoolean("success") == true
                stopCoordinator.stopCompleted()
                val stopped = ownedLease.operationFinished(terminal = remoteStopped, operation = operation)
                val verifiedAbsent = !remoteStopped && service != null &&
                    manager.reconcileAbsentSession(ownedLease, service, generation)
                cleanupConfirmed = stopped || verifiedAbsent
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

    private fun update(value: EmbeddedAppState) { state = value; if (!closed) notifications.offer(Notification.State(value)) }
    private fun rejectStart(code: String, message: String) {
        if (lifecycle.rejectStart(EmbeddedSessionFailure(code, message))) {
            publishLifecycle()
            update(state.copy(busy = false, status = message))
        }
    }
    private fun publishLifecycle() {
        val snapshot = lifecycle.snapshot
        val admitted = notifications.offer(Notification.Lifecycle(snapshot))
        EmbeddedEvidence.observe { EmbeddedEvidence.app("session.snapshot", sessionId.value, fields = buildMap {
            put("phase", snapshot.phase.name); put("display_id", snapshot.displayId.toString()); put("admitted", admitted.toString())
            snapshot.failure?.let { put("failure_code", it.code); it.message?.let { message -> put("message", message) } }
        }) }
    }
}
