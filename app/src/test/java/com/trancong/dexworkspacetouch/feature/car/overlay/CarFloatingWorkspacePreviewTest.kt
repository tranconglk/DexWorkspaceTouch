package com.trancong.dexworkspacetouch.feature.car.overlay

import com.trancong.dexworkspacetouch.feature.car.CarWorkspaceShortcutSlot
import com.trancong.dexworkspacetouch.feature.car.CarWorkspaceShortcuts
import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell
import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarFloatingWorkspacePreviewTest {
    @Test fun fullHalfThirdTopBottomAndArbitraryGeometryRemainNormalizedAndOrdered() {
        val cells = listOf(
            cell("full", 0f, 0f, 1f, 1f),
            cell("left", 0f, 0f, .5f, 1f),
            cell("third", 0f, 0f, 1f / 3f, 1f),
            cell("top", 0f, 0f, 1f, .5f),
            cell("bottom", 0f, .5f, 1f, 1f),
            cell("arbitrary", .17f, .23f, .81f, .92f),
        )

        val preview = workspace("geometry", "Geometry", cells).toCarFloatingPreview()

        assertEquals(
            cells.map { listOf(it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom) },
            preview.cells.map { listOf(it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom) },
        )
        assertEquals(cells.map { it.app?.packageName }, preview.cells.map { it.appIdentity?.packageName })
    }

    @Test fun boundsClampOverflowAndRejectInvalidOrNonFiniteInput() {
        assertEquals(
            CarFloatingNormalizedBounds.normalized(0f, 0f, 1f, 1f),
            CarFloatingNormalizedBounds.normalized(-2f, -1f, 3f, 4f),
        )
        assertNull(CarFloatingNormalizedBounds.normalized(.8f, 0f, .2f, 1f))
        assertNull(CarFloatingNormalizedBounds.normalized(0f, 1f, 1f, 1f))
        assertNull(CarFloatingNormalizedBounds.normalized(Float.NaN, 0f, 1f, 1f))
    }

    @Test fun previewCopiesCellsAndKeepsExactAppIdentity() {
        val source = mutableListOf(
            cell("maps", 0f, 0f, 1f, 1f, app("pkg.maps", "MapsActivity")),
        )
        val preview = workspace("one", "One", source).toCarFloatingPreview()
        source.clear()

        assertEquals(1, preview.cells.size)
        assertEquals("pkg.maps", preview.cells.single().appIdentity?.packageName)
        assertEquals("MapsActivity", preview.cells.single().appIdentity?.activityName)
    }

    @Test fun renameLayoutAndRebindFlowThroughWithoutChangingSlotSemantics() {
        val shortcuts = CarWorkspaceShortcuts.from {
            if (it == CarWorkspaceShortcutSlot.Slot1) "one" else null
        }
        val original = resolveFloatingWorkspaceShortcuts(
            shortcuts,
            mapOf("one" to workspace("one", "Original", listOf(cell("full", 0f, 0f, 1f, 1f)))),
            6,
        ).first()
        val changed = resolveFloatingWorkspaceShortcuts(
            shortcuts,
            mapOf("one" to workspace("one", "Renamed", listOf(
                cell("left", 0f, 0f, .5f, 1f),
                cell("right", .5f, 0f, 1f, 1f),
            ))),
            6,
        ).first()

        assertEquals(original.slot, changed.slot)
        assertEquals("Slot 1 — Renamed", changed.accessibilityLabel)
        assertEquals(1, original.preview?.cells?.size)
        assertEquals(2, changed.preview?.cells?.size)
        assertEquals("one", (changed.state as CarFloatingWorkspaceShortcutState.Configured).workspaceId)
    }

    private fun workspace(id: String, name: String, cells: List<WorkspaceCell>) = Workspace(
        id = id,
        name = name,
        canvas = WorkspaceCanvas(cells.toList()),
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
        app: AssignedApp? = app("pkg.$id", "$id.Activity"),
    ) = WorkspaceCell(id, NormalizedBounds(left, top, right, bottom), app)

    private fun app(packageName: String, activityName: String) = AssignedApp(
        packageName,
        activityName,
        packageName,
    )
}
