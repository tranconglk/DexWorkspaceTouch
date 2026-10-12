package com.trancong.dexworkspacetouch.feature.physicalbutton

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Cùng chạy trên main thread; timer dùng cùng uptime với timestamp của KeyEvent. */
class PhysicalButtonGestureController(
    private val scope: CoroutineScope,
    private val configuration: () -> PhysicalButtonConfiguration,
    private val availableWorkspaceIds: () -> Set<String>,
    private val bindingRequested: () -> Boolean,
    private val nowMillis: () -> Long,
    private val onGesture: (PhysicalButtonGesture, PhysicalButtonKeyEvent, PhysicalButtonConfiguration) -> Unit,
    private val keyHandler: PhysicalButtonKeyHandler = PhysicalButtonKeyHandler(),
) {
    private val timers = mutableMapOf<Pair<Int, Int>, Job>()

    fun handle(event: PhysicalButtonKeyEvent): PhysicalButtonKeyDecision {
        val snapshot = configuration()
        val decision = keyHandler.handle(event, snapshot, availableWorkspaceIds(), bindingRequested())
        val key = event.deviceId to event.keyCode
        when (decision) {
            PhysicalButtonKeyDecision.TRACK_PRESS -> {
                timers.remove(key)?.cancel()
                timers[key] = scope.launch {
                    delay((event.downTime + keyHandler.longPressThresholdMs - nowMillis()).coerceAtLeast(0))
                    val timeoutDecision = keyHandler.handleLongPress(event, configuration(),
                        availableWorkspaceIds(), bindingRequested(), nowMillis())
                    timers.remove(key)
                    emit(timeoutDecision, event, snapshot)
                }
            }
            PhysicalButtonKeyDecision.SHORT_PRESS, PhysicalButtonKeyDecision.LONG_PRESS -> {
                timers.remove(key)?.cancel()
                emit(decision, event, snapshot)
            }
            PhysicalButtonKeyDecision.CONSUME -> {
                if (event.action == 1) timers.remove(key)?.cancel()
            }
            else -> Unit
        }
        return decision
    }

    fun invalidatePresses() {
        keyHandler.invalidatePresses(configuration(), availableWorkspaceIds(), bindingRequested())
    }

    fun clear() {
        timers.values.forEach(Job::cancel)
        timers.clear()
        keyHandler.clear()
    }

    private fun emit(
        decision: PhysicalButtonKeyDecision,
        event: PhysicalButtonKeyEvent,
        snapshot: PhysicalButtonConfiguration,
    ) {
        val gesture = when (decision) {
            PhysicalButtonKeyDecision.SHORT_PRESS -> PhysicalButtonGesture.SHORT_PRESS
            PhysicalButtonKeyDecision.LONG_PRESS -> PhysicalButtonGesture.LONG_PRESS
            else -> return
        }
        onGesture(gesture, event, snapshot)
    }
}
