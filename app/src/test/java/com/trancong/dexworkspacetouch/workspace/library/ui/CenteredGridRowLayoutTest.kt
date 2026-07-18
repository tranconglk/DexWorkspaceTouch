package com.trancong.dexworkspacetouch.workspace.library.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CenteredGridRowLayoutTest {
    @Test fun oneItemInSixColumnsCreatesOnePartialRow() = assertRows(1, 6, listOf(1))
    @Test fun twoItemsInSixColumnsCreatesOnePartialRow() = assertRows(2, 6, listOf(2))
    @Test fun fiveItemsInSixColumnsCreatesOnePartialRow() = assertRows(5, 6, listOf(5))
    @Test fun sixItemsInSixColumnsCreatesOneFullRow() = assertRows(6, 6, listOf(6))
    @Test fun sevenItemsInSixColumnsCentersLastSingleItem() = assertRows(7, 6, listOf(6, 1))
    @Test fun oneItemInThreeColumnsCreatesOnePartialRow() = assertRows(1, 3, listOf(1))
    @Test fun twoItemsInThreeColumnsCreatesOnePartialRow() = assertRows(2, 3, listOf(2))
    @Test fun oneItemInTwoColumnsCreatesOnePartialRow() = assertRows(1, 2, listOf(1))

    @Test fun emptyItemsCreateNoRows() {
        assertTrue(chunkCenteredRows(emptyList<Int>(), 6).isEmpty())
    }

    @Test fun rowChunkingPreservesOrder() {
        val values = listOf("c", "a", "b", "e", "d")
        assertEquals(values, chunkCenteredRows(values, 2).flatten())
    }

    @Test fun resizeRecomputesAdaptiveColumnCountAndRows() {
        val items = (1..7).toList()
        val wideColumns = adaptiveGridColumnCount(1872f, 280f, 16f)
        val narrowColumns = adaptiveGridColumnCount(872f, 280f, 16f)
        assertEquals(6, wideColumns)
        assertEquals(3, narrowColumns)
        assertEquals(listOf(6, 1), chunkCenteredRows(items, wideColumns).map { it.size })
        assertEquals(listOf(3, 3, 1), chunkCenteredRows(items, narrowColumns).map { it.size })
    }

    @Test fun pinnedCardWidthUsesSameGridCellFormulaAsRegularGrid() {
        val available = 1872f
        val spacing = 16f
        val columns = adaptiveGridColumnCount(available, 280f, spacing)
        val pinnedWidth = centeredGridMetrics(available, 280f, spacing, columns).cardWidth
        val regularCellWidth = (available - spacing * (columns - 1)) / columns
        assertEquals(regularCellWidth, pinnedWidth, 0.0001f)
        assertTrue(pinnedWidth >= 280f)
    }

    private fun assertRows(itemCount: Int, columns: Int, expectedSizes: List<Int>) {
        assertEquals(expectedSizes, chunkCenteredRows((0 until itemCount).toList(), columns).map { it.size })
    }
}
