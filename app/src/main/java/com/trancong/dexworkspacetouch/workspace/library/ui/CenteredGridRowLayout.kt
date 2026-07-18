package com.trancong.dexworkspacetouch.workspace.library.ui

data class CenteredGridMetrics(
    val columnCount: Int,
    val cardWidth: Float,
)

fun adaptiveGridColumnCount(
    availableWidth: Float,
    cardMinWidth: Float,
    spacing: Float,
): Int {
    require(availableWidth >= 0f) { "availableWidth must not be negative" }
    require(cardMinWidth > 0f) { "cardMinWidth must be positive" }
    require(spacing >= 0f) { "spacing must not be negative" }
    return ((availableWidth + spacing) / (cardMinWidth + spacing)).toInt().coerceAtLeast(1)
}

fun centeredGridMetrics(
    availableWidth: Float,
    cardMinWidth: Float,
    spacing: Float,
    columnCount: Int,
): CenteredGridMetrics {
    require(availableWidth >= 0f) { "availableWidth must not be negative" }
    require(cardMinWidth > 0f) { "cardMinWidth must be positive" }
    require(spacing >= 0f) { "spacing must not be negative" }
    require(columnCount > 0) { "columnCount must be positive" }
    val totalSpacing = spacing * (columnCount - 1)
    return CenteredGridMetrics(columnCount, ((availableWidth - totalSpacing) / columnCount).coerceAtLeast(0f))
}

fun <T> chunkCenteredRows(items: List<T>, columnCount: Int): List<List<T>> {
    require(columnCount > 0) { "columnCount must be positive" }
    return items.chunked(columnCount)
}
