package com.trancong.dexworkspacetouch.feature.car

import com.trancong.dexworkspacetouch.feature.car.overlay.toCarFloatingPreview
import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell
import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CarWorkspaceDashboardTest {
    @Test fun configuredPresentationUsesSharedPreviewNameAndNoLegacyCardLabels() {
        val workspace = workspace(
            name = "Map Music",
            cells = listOf(
                cell("left", 0f, 0f, .5f, 1f),
                cell("right", .5f, 0f, 1f, 1f),
            ),
        )
        val row = resolveRows(workspace).first()

        assertEquals(CarWorkspaceShortcutStatus.Configured, row.status)
        assertEquals("Map Music", row.dashboardTitle)
        assertEquals("Mở Workspace Map Music", row.dashboardAccessibilityLabel)
        assertFalse(row.dashboardTitle.contains("Slot"))
        assertFalse(row.dashboardTitle.contains("apps"))
        assertEquals(
            listOf(listOf(0f, 0f, .5f, 1f), listOf(.5f, 0f, 1f, 1f)),
            row.preview?.cells?.map {
                listOf(it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom)
            },
        )
    }

    @Test fun unconfiguredAndUnavailableHaveStablePlaceholderAndWarningPresentation() {
        val shortcuts = CarWorkspaceShortcuts.from { slot ->
            if (slot == CarWorkspaceShortcutSlot.Slot2) "deleted" else null
        }
        val rows = resolveCarWorkspaceShortcutRows(shortcuts, emptyList(), 3)

        assertEquals(CarWorkspaceShortcutStatus.Unconfigured, rows[0].status)
        assertEquals("Chưa cấu hình", rows[0].dashboardTitle)
        assertEquals("Slot 1, chưa cấu hình", rows[0].dashboardAccessibilityLabel)
        assertNull(rows[0].preview)
        assertEquals(CarWorkspaceShortcutStatus.Unavailable, rows[1].status)
        assertEquals("Workspace không khả dụng", rows[1].dashboardTitle)
        assertEquals("Slot 2, Workspace không khả dụng", rows[1].dashboardAccessibilityLabel)
        assertNull(rows[1].preview)
        assertEquals("deleted", rows[1].workspaceId)
    }

    @Test fun visibleCountsThreeThroughEightAlwaysUseLeadingStableSlots() {
        (3..8).forEach { count ->
            val rows = resolveCarWorkspaceShortcutRows(
                CarWorkspaceShortcuts.defaults(),
                emptyList(),
                count,
            )
            assertEquals(count, rows.size)
            assertEquals(CarWorkspaceShortcutSlot.entries.take(count), rows.map { it.slot })
        }
    }

    @Test fun dashboardAndFloatingDockUseTheSameNormalizedPreviewData() {
        val workspace = workspace(cells = listOf(cell("center", .2f, .1f, .8f, .9f)))

        assertEquals(workspace.toCarFloatingPreview(), workspace.toCarWorkspacePreview())
    }

    @Test fun renameAndLayoutChangesProduceFreshPresentationFromWorkspaceSource() {
        val original = resolveRows(workspace(name = "Original")).first()
        val changed = resolveRows(
            workspace(
                name = "Renamed",
                cells = listOf(
                    cell("top", 0f, 0f, 1f, .5f),
                    cell("bottom", 0f, .5f, 1f, 1f),
                ),
            ),
        ).first()

        assertEquals("Original", original.dashboardTitle)
        assertEquals("Renamed", changed.dashboardTitle)
        assertEquals(1, original.preview?.cells?.size)
        assertEquals(2, changed.preview?.cells?.size)
    }

    @Test fun missingIconUsesStableFallbackLabelWithoutThrowing() {
        assertEquals("C", carWorkspaceFallbackIconLabel(AppIdentity("com.maps", null)))
    }

    private fun resolveRows(workspace: Workspace): List<CarWorkspaceShortcutRow> =
        resolveCarWorkspaceShortcutRows(
            shortcuts = CarWorkspaceShortcuts.from { slot ->
                if (slot == CarWorkspaceShortcutSlot.Slot1) workspace.id else null
            },
            workspaces = listOf(
                CarWorkspaceOption(
                    id = workspace.id,
                    name = workspace.name,
                    appCount = workspace.canvas.cells.count { it.app != null },
                    preview = workspace.toCarWorkspacePreview(),
                ),
            ),
            visibleSlotCount = 3,
        )

    private fun workspace(
        name: String = "Workspace",
        cells: List<WorkspaceCell> = listOf(cell("full", 0f, 0f, 1f, 1f)),
    ) = Workspace(
        id = "workspace-id",
        name = name,
        canvas = WorkspaceCanvas(cells),
        modifiedSequence = 0,
        schemaVersion = 1,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
    )

    private fun cell(
        id: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) = WorkspaceCell(
        id = id,
        bounds = NormalizedBounds(left, top, right, bottom),
        app = AssignedApp("pkg.$id", "$id.Activity", id),
    )
}
