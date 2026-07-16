package com.trancong.dexworkspacetouch.workspace.designer.ui

internal data class WorkspaceActionSafeArea(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

internal fun workspaceActionSafeArea(
    cell: ComposePlacement,
    baseInset: Int,
    dividerClearance: Int,
    minimumContentSize: Int,
    dividerOnLeft: Boolean,
    dividerOnTop: Boolean,
    dividerOnRight: Boolean,
    dividerOnBottom: Boolean,
): WorkspaceActionSafeArea {
    require(baseInset >= 0 && dividerClearance >= 0 && minimumContentSize > 0)
    require(cell.width > 0 && cell.height > 0)

    val (left, right) = fittedInsets(
        totalSize = cell.width,
        leading = baseInset + if (dividerOnLeft) dividerClearance else 0,
        trailing = baseInset + if (dividerOnRight) dividerClearance else 0,
        minimumContentSize = minimumContentSize,
    )
    val (top, bottom) = fittedInsets(
        totalSize = cell.height,
        leading = baseInset + if (dividerOnTop) dividerClearance else 0,
        trailing = baseInset + if (dividerOnBottom) dividerClearance else 0,
        minimumContentSize = minimumContentSize,
    )
    return WorkspaceActionSafeArea(
        x = cell.x + left,
        y = cell.y + top,
        width = (cell.width - left - right).coerceAtLeast(1),
        height = (cell.height - top - bottom).coerceAtLeast(1),
    )
}

private fun fittedInsets(
    totalSize: Int,
    leading: Int,
    trailing: Int,
    minimumContentSize: Int,
): Pair<Int, Int> {
    val availableForInsets = (totalSize - minimumContentSize.coerceAtMost(totalSize)).coerceAtLeast(0)
    val desiredTotal = leading + trailing
    if (desiredTotal <= availableForInsets) return leading to trailing
    if (desiredTotal == 0) return 0 to 0

    val fittedLeading = (availableForInsets * (leading.toFloat() / desiredTotal)).toInt()
    return fittedLeading to (availableForInsets - fittedLeading)
}
