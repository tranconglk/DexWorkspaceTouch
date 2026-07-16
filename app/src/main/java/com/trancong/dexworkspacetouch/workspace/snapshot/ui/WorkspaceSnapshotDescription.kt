package com.trancong.dexworkspacetouch.workspace.snapshot.ui

import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas

internal fun WorkspaceCanvas.accessibilitySummary(): String {
    val entries = cells.map { cell -> cell.app?.label ?: "một ô trống" }
    return "Bố cục gồm ${cells.size} ô: ${entries.joinNaturally()}."
}

private fun List<String>.joinNaturally(): String = when (size) {
    0 -> ""
    1 -> first()
    2 -> "${first()} và ${last()}"
    else -> dropLast(1).joinToString(", ") + " và " + last()
}
