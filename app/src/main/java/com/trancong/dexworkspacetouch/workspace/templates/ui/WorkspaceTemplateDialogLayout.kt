package com.trancong.dexworkspacetouch.workspace.templates.ui

data class WorkspaceTemplateDialogSize(
    val width: Float,
    val height: Float,
)

fun calculateWorkspaceTemplateDialogSize(
    availableWidth: Float,
    availableHeight: Float,
    widthFraction: Float,
    maximumWidth: Float,
    heightFraction: Float,
): WorkspaceTemplateDialogSize {
    require(availableWidth > 0f && availableHeight > 0f) { "available size must be positive" }
    require(widthFraction in 0f..1f && widthFraction > 0f) { "widthFraction must be in (0, 1]" }
    require(heightFraction in 0f..1f && heightFraction > 0f) { "heightFraction must be in (0, 1]" }
    require(maximumWidth > 0f) { "maximumWidth must be positive" }
    return WorkspaceTemplateDialogSize(
        width = (availableWidth * widthFraction).coerceAtMost(maximumWidth).coerceAtMost(availableWidth),
        height = (availableHeight * heightFraction).coerceAtMost(availableHeight),
    )
}

fun workspaceTemplateColumnCount(
    contentWidth: Float,
    mediumBreakpoint: Float,
    largeBreakpoint: Float,
): Int {
    require(contentWidth >= 0f) { "contentWidth must not be negative" }
    require(mediumBreakpoint > 0f && largeBreakpoint > mediumBreakpoint) {
        "breakpoints must be positive and ordered"
    }
    return when {
        contentWidth >= largeBreakpoint -> 3
        contentWidth >= mediumBreakpoint -> 2
        else -> 1
    }
}

fun shouldUsePlatformDefaultTemplateDialogWidth(
    hostWidth: Float,
    largeBreakpoint: Float,
): Boolean {
    require(hostWidth > 0f && largeBreakpoint > 0f) { "widths must be positive" }
    return hostWidth < largeBreakpoint
}
