package com.trancong.dexworkspacetouch.debugbenchmark

import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.SplitDirection
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvasEditor
import com.trancong.dexworkspacetouch.workspace.designer.model.assignApp
import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace

internal object WorkspaceBenchmarkDataGenerator {
    fun generate(count: Int): List<Workspace> {
        require(count in setOf(100, 250, 500)) { "Benchmark count must be 100, 250, or 500" }
        return List(count) { index ->
            val number = index + 1
            Workspace(
                id = "benchmark-${number.toString().padStart(4, '0')}",
                name = "Workspace $number",
                canvas = canvas(number),
                modifiedSequence = number.toLong(),
                schemaVersion = 1,
                createdAtEpochMillis = 1_700_000_000_000L + number,
                updatedAtEpochMillis = 1_700_000_000_000L + number * 10L,
            )
        }
    }

    private fun canvas(number: Int): WorkspaceCanvas {
        val editor = WorkspaceCanvasEditor()
        val cellCount = (number - 1) % 4 + 1
        var canvas = WorkspaceCanvas.singleCell()
        if (cellCount >= 2) canvas = editor.splitCell(canvas, "cell", SplitDirection.VERTICAL)
        if (cellCount >= 3) canvas = editor.splitCell(canvas, "cell_a", SplitDirection.HORIZONTAL)
        if (cellCount >= 4) canvas = editor.splitCell(canvas, "cell_b", SplitDirection.HORIZONTAL)
        if (number % 2 == 0) {
            canvas = canvas.assignApp(
                canvas.cells.first().id,
                AssignedApp("benchmark.app.${number % 7}", "benchmark.Main${number % 3}", "App ${number % 7}"),
            )
        }
        return canvas
    }
}
