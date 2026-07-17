package com.trancong.dexworkspacetouch.workspace.templates

import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import kotlin.math.roundToInt

fun WorkspaceCanvas.canonicalTemplateSignature(
    tolerance: Float = DEFAULT_SIGNATURE_TOLERANCE,
): String {
    require(tolerance > 0f && tolerance.isFinite()) { "tolerance must be positive and finite" }
    val bounds = cells.map { cell ->
        listOf(cell.bounds.left, cell.bounds.top, cell.bounds.right, cell.bounds.bottom)
            .joinToString(BOUND_SEPARATOR) { value -> (value / tolerance).roundToInt().toString() }
    }.sorted()
    return "${cells.size}${CELL_SEPARATOR}${bounds.joinToString(CELL_SEPARATOR)}"
}

private const val DEFAULT_SIGNATURE_TOLERANCE = 0.0001f
private const val BOUND_SEPARATOR = ","
private const val CELL_SEPARATOR = "|"
