package com.trancong.dexworkspacetouch.feature.car.overlay

import kotlin.math.hypot

enum class CarFloatingDockEdge { Left, Right }

data class CarFloatingDockPosition(
    val x: Int,
    val y: Int,
    val edge: CarFloatingDockEdge,
)

data class CarFloatingDockWorkArea(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(right >= left)
        require(bottom >= top)
    }
}

object CarFloatingDockPositioner {
    fun defaultPosition(
        workArea: CarFloatingDockWorkArea,
        width: Int,
        height: Int,
    ): CarFloatingDockPosition = CarFloatingDockPosition(
        x = workArea.left,
        y = (workArea.top + (workArea.bottom - workArea.top - height) / 2)
            .coerceIn(workArea.top, maxY(workArea, height)),
        edge = CarFloatingDockEdge.Left,
    )

    fun clamp(
        x: Int,
        y: Int,
        edge: CarFloatingDockEdge,
        workArea: CarFloatingDockWorkArea,
        width: Int,
        height: Int,
    ) = CarFloatingDockPosition(
        x = x.coerceIn(workArea.left, maxX(workArea, width)),
        y = y.coerceIn(workArea.top, maxY(workArea, height)),
        edge = edge,
    )

    fun snap(
        x: Int,
        y: Int,
        workArea: CarFloatingDockWorkArea,
        width: Int,
        height: Int,
    ): CarFloatingDockPosition {
        val clamped = clamp(x, y, CarFloatingDockEdge.Left, workArea, width, height)
        val rightX = maxX(workArea, width)
        val edge = if (clamped.x <= (workArea.left + rightX) / 2) {
            CarFloatingDockEdge.Left
        } else {
            CarFloatingDockEdge.Right
        }
        return clamped.copy(x = if (edge == CarFloatingDockEdge.Left) workArea.left else rightX, edge = edge)
    }

    fun forSize(
        position: CarFloatingDockPosition,
        workArea: CarFloatingDockWorkArea,
        width: Int,
        height: Int,
    ): CarFloatingDockPosition = clamp(
        x = if (position.edge == CarFloatingDockEdge.Left) workArea.left else maxX(workArea, width),
        y = position.y,
        edge = position.edge,
        workArea = workArea,
        width = width,
        height = height,
    )

    private fun maxX(area: CarFloatingDockWorkArea, width: Int) =
        (area.right - width).coerceAtLeast(area.left)

    private fun maxY(area: CarFloatingDockWorkArea, height: Int) =
        (area.bottom - height).coerceAtLeast(area.top)
}

enum class CarDockTouchAction { Down, Move, Up, Cancel }

sealed interface CarDockGestureResult {
    data object None : CarDockGestureResult
    data object Tap : CarDockGestureResult
    data class Drag(val x: Int, val y: Int) : CarDockGestureResult
    data class EndDrag(val x: Int, val y: Int) : CarDockGestureResult
}

class CarDockDragGesture(private val touchSlop: Float) {
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false
    private var active = false

    fun onTouch(
        action: CarDockTouchAction,
        rawX: Float,
        rawY: Float,
        windowX: Int,
        windowY: Int,
    ): CarDockGestureResult = when (action) {
        CarDockTouchAction.Down -> {
            downX = rawX
            downY = rawY
            startX = windowX
            startY = windowY
            dragging = false
            active = true
            CarDockGestureResult.None
        }
        CarDockTouchAction.Move -> {
            if (!active) {
                CarDockGestureResult.None
            } else {
                val dx = rawX - downX
                val dy = rawY - downY
                if (!dragging && hypot(dx, dy) > touchSlop) dragging = true
                if (dragging) CarDockGestureResult.Drag(startX + dx.toInt(), startY + dy.toInt())
                else CarDockGestureResult.None
            }
        }
        CarDockTouchAction.Up -> finish(rawX, rawY, allowTap = true)
        CarDockTouchAction.Cancel -> finish(rawX, rawY, allowTap = false)
    }

    private fun finish(rawX: Float, rawY: Float, allowTap: Boolean): CarDockGestureResult {
        if (!active) return CarDockGestureResult.None
        active = false
        val wasDragging = dragging
        dragging = false
        return if (wasDragging) {
            CarDockGestureResult.EndDrag(
                startX + (rawX - downX).toInt(),
                startY + (rawY - downY).toInt(),
            )
        } else if (allowTap) {
            CarDockGestureResult.Tap
        } else {
            CarDockGestureResult.None
        }
    }
}
