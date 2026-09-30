package com.trancong.dexworkspacetouch.feature.embeddedapp

import java.util.UUID
import java.security.MessageDigest

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
