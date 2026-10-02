package com.trancong.dexworkspacetouch.feature.embeddedapp

import java.util.UUID
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal fun interface WorkAdmission {
    fun tryExecute(task: Runnable): Boolean
}

/** Fixed resources; rejection never waits, retries or runs on the caller. */
internal class BoundedExecutionLane(name: String, workers: Int, queued: Int) : WorkAdmission {
    private val executor = ThreadPoolExecutor(
        workers, workers, 0L, TimeUnit.MILLISECONDS,
        if (queued == 0) SynchronousQueue() else ArrayBlockingQueue(queued),
        { task -> Thread(task, name).apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )
    override fun tryExecute(task: Runnable): Boolean = try {
        executor.execute(task)
        true
    } catch (_: RejectedExecutionException) {
        false
    }
}

internal object EmbeddedConnectionLanes {
    val transport: WorkAdmission = BoundedExecutionLane("embedded-transport", 1, 8)
    val verifier: WorkAdmission = BoundedExecutionLane("embedded-uid", 2, 0)
    val start: WorkAdmission = BoundedExecutionLane("embedded-start", 2, 0)
    val finalization: WorkAdmission = BoundedExecutionLane("embedded-finalization", 1, 8)
    private val notifications = BoundedExecutionLane("embedded-notification", 2, 32)
    val notificationExecutor = Executor { task ->
        if (!notifications.tryExecute(task)) throw RejectedExecutionException("Notification capacity exhausted")
    }
}

/** Pending IPC/queued delivery retains this destination, never a loaded consumer. */
internal class DetachableMailbox<T>(private val executor: Executor, consumer: (T) -> Unit) {
    private val consumer = AtomicReference<((T) -> Unit)?>(consumer)
    fun offer(value: T): Boolean {
        if (consumer.get() == null) return false
        return try {
            executor.execute(Delivery(this, value))
            true
        } catch (_: RejectedExecutionException) {
            false
        }
    }
    fun detach() { consumer.set(null) }
    private class Delivery<T>(val mailbox: DetachableMailbox<T>, val value: T) : Runnable {
        override fun run() { mailbox.consumer.get()?.invoke(value) }
    }
}

@JvmInline
value class EmbeddedAppSessionId(val value: String) {
    init { require(value.isNotBlank()) }
}

fun newEmbeddedAppSessionId(): EmbeddedAppSessionId =
    EmbeddedAppSessionId(UUID.randomUUID().toString())

enum class EmbeddedSessionPhase {
    IDLE, CONNECTING, READY, STARTING, ACTIVE,
    STOPPING, STOPPED, FAILED, CLEANUP_INCOMPLETE, REMOTE_DIED,
}

data class EmbeddedSessionFailure(val code: String, val message: String?)

data class EmbeddedSessionSnapshot(
    val phase: EmbeddedSessionPhase,
    val displayId: Int = -1,
    val failure: EmbeddedSessionFailure? = null,
)

data class EmbeddedTouchEvent(
    val action: Int,
    val x: Float,
    val y: Float,
    val pressure: Float,
    val eventTimeNanos: Long,
)

class EmbeddedAppLifecycleCoordinator {
    private val transitions = mutableListOf<EmbeddedSessionSnapshot>()
    private var stopRequested = false
    private var closeRequested = false

    var snapshot = EmbeddedSessionSnapshot(EmbeddedSessionPhase.IDLE)
        private set
    val history: List<EmbeddedSessionSnapshot> get() = transitions.toList()
    var remoteStopRequestCount: Int = 0
        private set

    @Synchronized fun beginConnect(): Boolean {
        if (snapshot.phase != EmbeddedSessionPhase.IDLE) return false
        transition(EmbeddedSessionSnapshot(EmbeddedSessionPhase.CONNECTING))
        return true
    }

    @Synchronized fun serviceReady() {
        if (snapshot.phase == EmbeddedSessionPhase.CONNECTING) {
            transition(EmbeddedSessionSnapshot(EmbeddedSessionPhase.READY))
        }
    }

    @Synchronized fun connectionFailed(failure: EmbeddedSessionFailure): Boolean {
        if (snapshot.phase != EmbeddedSessionPhase.CONNECTING) return false
        transition(EmbeddedSessionSnapshot(EmbeddedSessionPhase.FAILED, failure = failure))
        return true
    }

    @Synchronized fun beginStart(): Boolean {
        if (snapshot.phase != EmbeddedSessionPhase.READY) return false
        transition(EmbeddedSessionSnapshot(EmbeddedSessionPhase.STARTING))
        return true
    }

    @Synchronized fun startSucceeded(displayId: Int): Boolean {
        if (snapshot.phase == EmbeddedSessionPhase.STARTING) {
            transition(EmbeddedSessionSnapshot(EmbeddedSessionPhase.ACTIVE, displayId))
            return true
        }
        return false
    }

    @Synchronized fun startFailed(failure: EmbeddedSessionFailure): Boolean {
        if (snapshot.phase == EmbeddedSessionPhase.STARTING) {
            transition(EmbeddedSessionSnapshot(EmbeddedSessionPhase.FAILED, failure = failure))
            return true
        }
        return false
    }

    @Synchronized fun rejectStart(failure: EmbeddedSessionFailure): Boolean {
        if (snapshot.phase != EmbeddedSessionPhase.READY && snapshot.phase != EmbeddedSessionPhase.STARTING) return false
        transition(EmbeddedSessionSnapshot(EmbeddedSessionPhase.FAILED, failure = failure))
        return true
    }

    @Synchronized fun requestStop(): Boolean {
        if (isTerminal() || stopRequested) return false
        stopRequested = true
        remoteStopRequestCount++
        transition(EmbeddedSessionSnapshot(EmbeddedSessionPhase.STOPPING))
        return true
    }

    @Synchronized fun requestClose(): Boolean {
        if (isTerminal() || closeRequested) return false
        closeRequested = true
        if (!stopRequested) requestStop()
        return true
    }

    @Synchronized fun cleanupSucceeded(leaseReleased: Boolean) {
        if (snapshot.phase != EmbeddedSessionPhase.STOPPING || !leaseReleased) return
        transition(EmbeddedSessionSnapshot(EmbeddedSessionPhase.STOPPED))
    }

    @Synchronized fun cleanupFailed(failure: EmbeddedSessionFailure) {
        if (snapshot.phase == EmbeddedSessionPhase.STOPPING) {
            transition(EmbeddedSessionSnapshot(EmbeddedSessionPhase.CLEANUP_INCOMPLETE, failure = failure))
        }
    }

    @Synchronized fun remoteDied() {
        if (!isTerminal()) transition(EmbeddedSessionSnapshot(EmbeddedSessionPhase.REMOTE_DIED))
    }

    private fun isTerminal(): Boolean = snapshot.phase in setOf(
        EmbeddedSessionPhase.STOPPED,
        EmbeddedSessionPhase.FAILED,
        EmbeddedSessionPhase.CLEANUP_INCOMPLETE,
        EmbeddedSessionPhase.REMOTE_DIED,
    )

    private fun transition(next: EmbeddedSessionSnapshot) {
        if (snapshot == next || isTerminal()) return
        snapshot = next
        transitions += next
    }
}

enum class RemoteSessionPhase { RESERVED, STARTING, ACTIVE, FAILED, STOPPING, STOPPED }

data class EmbeddedAppServiceState(
    val activeSessionCount: Int,
    val startingSessionCount: Int,
    val stoppingSessionCount: Int,
    val liveResourceSessionCount: Int,
) {
    init {
        require(activeSessionCount >= 0)
        require(startingSessionCount >= 0)
        require(stoppingSessionCount >= 0)
        require(liveResourceSessionCount >= 0)
    }

    val provesEmpty: Boolean get() = activeSessionCount == 0 &&
        startingSessionCount == 0 && stoppingSessionCount == 0 &&
        liveResourceSessionCount == 0
}

fun canRemoveEmbeddedAppService(
    leaseCount: Int,
    operationInFlight: Boolean,
    binderCertain: Boolean,
    remoteState: EmbeddedAppServiceState?,
): Boolean = leaseCount == 0 && !operationInFlight && binderCertain &&
    remoteState?.provesEmpty == true

enum class SessionConnectionPhase { IDLE, CONNECTING, READY, REMOTE_DIED }

class SessionConnectionCoordinator {
    private val transitions = mutableListOf<SessionConnectionPhase>()
    var phase: SessionConnectionPhase = SessionConnectionPhase.IDLE
        private set
    val history: List<SessionConnectionPhase> get() = transitions.toList()

    @Synchronized fun beginConnect(): Boolean {
        if (phase != SessionConnectionPhase.IDLE) return false
        transitionTo(SessionConnectionPhase.CONNECTING)
        return true
    }

    @Synchronized fun serviceReady() {
        if (phase == SessionConnectionPhase.CONNECTING || phase == SessionConnectionPhase.READY) {
            transitionTo(SessionConnectionPhase.READY)
        }
    }

    @Synchronized fun serviceDied() {
        if (phase == SessionConnectionPhase.READY || phase == SessionConnectionPhase.CONNECTING) {
            transitionTo(SessionConnectionPhase.REMOTE_DIED)
        }
    }

    private fun transitionTo(next: SessionConnectionPhase) {
        if (phase == next) return
        phase = next
        transitions += next
    }
}

fun virtualInputDeviceName(sessionId: String): String {
    require(sessionId.isNotBlank())
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(sessionId.toByteArray(Charsets.UTF_8))
        .take(12).joinToString("") { "%02x".format(it) }
    return "DWT-VT-$digest"
}

enum class VirtualInputOwnershipPhase {
    NOT_ATTEMPTED, ATTEMPTED, NOT_CREATED, OWNED, UNCERTAIN, CLOSED
}

class VirtualInputOwnership(val expectedName: String) {
    var phase = VirtualInputOwnershipPhase.NOT_ATTEMPTED
        private set
    val shouldCloseHandle: Boolean get() = phase == VirtualInputOwnershipPhase.OWNED
    val isPotentiallyLive: Boolean get() = phase in setOf(
        VirtualInputOwnershipPhase.ATTEMPTED,
        VirtualInputOwnershipPhase.OWNED,
        VirtualInputOwnershipPhase.UNCERTAIN,
    )

    @Synchronized fun creationAttempted() {
        check(phase == VirtualInputOwnershipPhase.NOT_ATTEMPTED)
        phase = VirtualInputOwnershipPhase.ATTEMPTED
    }

    @Synchronized fun handleReturned() {
        check(phase == VirtualInputOwnershipPhase.ATTEMPTED)
        phase = VirtualInputOwnershipPhase.OWNED
    }

    @Synchronized fun creationFailed(observedNames: Set<String>) {
        if (phase != VirtualInputOwnershipPhase.ATTEMPTED) return
        phase = if (expectedName in observedNames) VirtualInputOwnershipPhase.UNCERTAIN
        else VirtualInputOwnershipPhase.NOT_CREATED
    }

    @Synchronized fun beginClose(): Boolean = phase == VirtualInputOwnershipPhase.OWNED

    @Synchronized fun closeCompleted(descriptorStillPresent: Boolean) {
        check(phase == VirtualInputOwnershipPhase.OWNED)
        phase = if (descriptorStillPresent) VirtualInputOwnershipPhase.UNCERTAIN
        else VirtualInputOwnershipPhase.CLOSED
    }
}
