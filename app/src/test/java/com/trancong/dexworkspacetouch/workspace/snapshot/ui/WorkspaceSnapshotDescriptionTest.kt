package com.trancong.dexworkspacetouch.workspace.snapshot.ui

import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceSnapshotDescriptionTest {
    @Test
    fun oneEmptyCell() {
        assertEquals("Bố cục gồm 1 ô: một ô trống.", WorkspaceCanvas.singleCell().accessibilitySummary())
    }

    @Test
    fun twoApps() {
        assertEquals("Bố cục gồm 2 ô: Maps và Waze.", canvas("Maps", "Waze").accessibilitySummary())
    }

    @Test
    fun duplicateAppLabelsArePreserved() {
        assertEquals("Bố cục gồm 2 ô: Maps và Maps.", canvas("Maps", "Maps").accessibilitySummary())
    }

    @Test
    fun appsAndEmptyCellsAreSummarized() {
        assertEquals(
            "Bố cục gồm 3 ô: Maps, một ô trống và Waze.",
            canvas("Maps", null, "Waze").accessibilitySummary(),
        )
    }

    @Test
    fun descriptionUsesCellOrder() {
        assertEquals(
            "Bố cục gồm 3 ô: Waze, Maps và một ô trống.",
            canvas("Waze", "Maps", null).accessibilitySummary(),
        )
    }

    private fun canvas(vararg labels: String?): WorkspaceCanvas = WorkspaceCanvas(
        labels.mapIndexed { index, label ->
            WorkspaceCell(
                id = "cell-$index",
                bounds = NormalizedBounds(0f, index / labels.size.toFloat(), 1f, (index + 1) / labels.size.toFloat()),
                app = label?.let { AssignedApp("demo.$index", "demo.MainActivity", it) },
            )
        },
    )
}
