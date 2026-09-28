package com.trancong.dexworkspacetouch.feature.embeddedapp

import android.view.MotionEvent

data class EmbeddedAppGeometry(val width: Int, val height: Int, val densityDpi: Int) {
    init {
        require(width > 0 && height > 0 && densityDpi > 0)
    }
}

data class EmbeddedAppTarget(
    val packageName: String,
    val componentName: String,
    val geometry: EmbeddedAppGeometry,
) {
    init {
        require(packageName.isNotBlank())
        require(componentName.startsWith("$packageName."))
    }
}

data class VdmPoint(val x: Float, val y: Float)

fun mapPoint(
    x: Float,
    y: Float,
    viewWidth: Int,
    viewHeight: Int,
    geometry: EmbeddedAppGeometry,
): VdmPoint {
    require(viewWidth > 0 && viewHeight > 0)
    return VdmPoint(
        (x * geometry.width / viewWidth).coerceIn(0f, geometry.width.toFloat()),
        (y * geometry.height / viewHeight).coerceIn(0f, geometry.height.toFloat()),
    )
}

fun virtualAction(actionMasked: Int): Int = when (actionMasked) {
    MotionEvent.ACTION_DOWN -> 0
    MotionEvent.ACTION_UP -> 1
    MotionEvent.ACTION_MOVE -> 2
    MotionEvent.ACTION_CANCEL -> 3
    else -> error("Unsupported touch action=$actionMasked")
}

const val VIRTUAL_TOOL_TYPE_PALM = 5

fun virtualToolType(action: Int): Int =
    if (action == MotionEvent.ACTION_CANCEL) VIRTUAL_TOOL_TYPE_PALM else MotionEvent.TOOL_TYPE_FINGER

fun pressureFor(actionMasked: Int, sourcePressure: Float): Float =
    if (actionMasked == MotionEvent.ACTION_DOWN) 255f else sourcePressure.coerceAtLeast(0f)

enum class CleanupStep { INPUT, TASK, DEVICE, ASSOCIATION, REFERENCES }

class CleanupProgress {
    private val completed = linkedSetOf<CleanupStep>()

    fun next(): CleanupStep? = CleanupStep.entries.firstOrNull { it !in completed }

    fun complete(step: CleanupStep): Boolean {
        if (step in completed) return false
        require(step == next())
        completed += step
        return true
    }

    val finished: Boolean get() = completed.size == CleanupStep.entries.size
}

fun dispatcherReady(
    focusedWindow: Boolean,
    targetWindow: Boolean,
    touchableAtPoint: Boolean,
    transitionIdle: Boolean,
    layoutIdle: Boolean,
    targetRunning: Boolean,
    descriptorMatchesDisplay: Boolean,
): Boolean = focusedWindow && targetWindow && touchableAtPoint && transitionIdle &&
    layoutIdle && targetRunning && descriptorMatchesDisplay

class SessionStopGate {
    private var requested = false

    @Synchronized fun request(): Boolean {
        if (requested) return false
        requested = true
        return true
    }

    @Synchronized fun reset() {
        requested = false
    }
}

class SessionStopCoordinator {
    private var stopRequested = false
    private var closeRequested = false
    private var stopFinished = false
    var logicalStopCount = 0; private set
    @Synchronized fun requestStop(): Boolean {
        if (stopRequested) return false
        stopRequested = true; logicalStopCount++
        return true
    }
    @Synchronized fun requestClose() { closeRequested = true }
    @Synchronized fun stopCompleted() { stopFinished = true }
    val mayReleaseLease: Boolean @Synchronized get() = closeRequested && (!stopRequested || stopFinished)
}
