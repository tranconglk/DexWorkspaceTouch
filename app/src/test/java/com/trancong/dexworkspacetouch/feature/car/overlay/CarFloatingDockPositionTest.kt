package com.trancong.dexworkspacetouch.feature.car.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarFloatingDockPositionTest {
    private val area = CarFloatingDockWorkArea(10, 20, 1010, 620)

    @Test
    fun clampKeepsDockInsideEveryEdge() {
        assertEquals(CarFloatingDockPosition(10, 20, CarFloatingDockEdge.Left),
            CarFloatingDockPositioner.clamp(-50, -40, CarFloatingDockEdge.Left, area, 100, 100))
        assertEquals(CarFloatingDockPosition(910, 520, CarFloatingDockEdge.Right),
            CarFloatingDockPositioner.clamp(2_000, 2_000, CarFloatingDockEdge.Right, area, 100, 100))
    }

    @Test
    fun snapChoosesNearestEdgeAndMiddleDeterministicallyChoosesLeft() {
        assertEquals(CarFloatingDockEdge.Left,
            CarFloatingDockPositioner.snap(100, 250, area, 100, 100).edge)
        assertEquals(CarFloatingDockEdge.Right,
            CarFloatingDockPositioner.snap(850, 250, area, 100, 100).edge)
        assertEquals(CarFloatingDockEdge.Left,
            CarFloatingDockPositioner.snap(460, 250, area, 100, 100).edge)
        assertEquals(250, CarFloatingDockPositioner.snap(100, 250, area, 100, 100).y)
    }

    @Test
    fun expandedDockOpensInwardFromBothEdgesWithoutClipping() {
        val left = CarFloatingDockPositioner.forSize(
            CarFloatingDockPosition(10, 300, CarFloatingDockEdge.Left), area, 220, 300,
        )
        val right = CarFloatingDockPositioner.forSize(
            CarFloatingDockPosition(910, 500, CarFloatingDockEdge.Right), area, 220, 300,
        )
        assertEquals(10, left.x)
        assertEquals(790, right.x)
        assertTrue(left.y + 300 <= area.bottom)
        assertTrue(right.y + 300 <= area.bottom)
    }

    @Test
    fun collapseAfterExpansionPreservesEdgeAndCurrentYForBothAnchors() {
        listOf(
            CarFloatingDockPosition(10, 240, CarFloatingDockEdge.Left),
            CarFloatingDockPosition(910, 240, CarFloatingDockEdge.Right),
        ).forEach { snapped ->
            val expanded = CarFloatingDockPositioner.forSize(snapped, area, 220, 300)
            val collapsed = CarFloatingDockPositioner.forSize(expanded, area, 100, 100)

            assertEquals(snapped.edge, collapsed.edge)
            assertEquals(expanded.y, collapsed.y)
            assertEquals(
                if (snapped.edge == CarFloatingDockEdge.Left) area.left else area.right - 100,
                collapsed.x,
            )
        }
    }

    @Test
    fun tapAndSubSlopMoveTap_butDragAndCancelNeverTap() {
        val tap = CarDockDragGesture(10f)
        tap.onTouch(CarDockTouchAction.Down, 100f, 100f, 20, 30)
        tap.onTouch(CarDockTouchAction.Move, 105f, 104f, 20, 30)
        assertEquals(CarDockGestureResult.Tap,
            tap.onTouch(CarDockTouchAction.Up, 105f, 104f, 20, 30))

        val drag = CarDockDragGesture(10f)
        drag.onTouch(CarDockTouchAction.Down, 100f, 100f, 20, 30)
        assertEquals(CarDockGestureResult.Drag(40, 30),
            drag.onTouch(CarDockTouchAction.Move, 120f, 100f, 20, 30))
        assertEquals(CarDockGestureResult.EndDrag(50, 30),
            drag.onTouch(CarDockTouchAction.Up, 130f, 100f, 40, 30))

        val cancelled = CarDockDragGesture(10f)
        cancelled.onTouch(CarDockTouchAction.Down, 0f, 0f, 20, 30)
        assertEquals(CarDockGestureResult.None,
            cancelled.onTouch(CarDockTouchAction.Cancel, 2f, 2f, 20, 30))
    }

    @Test
    fun sequentialTapsEachEmitExactlyOneTap() {
        val gesture = CarDockDragGesture(touchSlop = 8f)

        repeat(2) {
            assertEquals(
                CarDockGestureResult.None,
                gesture.onTouch(CarDockTouchAction.Down, 10f, 20f, 0, 0),
            )
            assertEquals(
                CarDockGestureResult.Tap,
                gesture.onTouch(CarDockTouchAction.Up, 10f, 20f, 0, 0),
            )
        }
    }

    @Test
    fun newShowDefaultsLeftAndVerticalCenter() {
        assertEquals(
            CarFloatingDockPosition(10, 270, CarFloatingDockEdge.Left),
            CarFloatingDockPositioner.defaultPosition(area, 100, 100),
        )
    }
}
