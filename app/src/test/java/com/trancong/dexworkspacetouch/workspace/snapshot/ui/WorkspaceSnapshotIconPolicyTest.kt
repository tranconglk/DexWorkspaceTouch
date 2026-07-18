package com.trancong.dexworkspacetouch.workspace.snapshot.ui

import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceSnapshotIconPolicyTest {
    @Test fun iconSizeAdaptsToCellSize() {
        assertEquals(SnapshotIconSizeClass.LARGE, snapshotIconSizeClass(120f, 80f))
        assertEquals(SnapshotIconSizeClass.MEDIUM, snapshotIconSizeClass(60f, 40f))
        assertEquals(SnapshotIconSizeClass.SMALL, snapshotIconSizeClass(30f, 24f))
    }

    @Test fun onlyAssignedVisibleCellWithLoaderRequestsIcon() {
        val empty = WorkspaceCell("empty", NormalizedBounds.FullCanvas)
        val assigned = empty.copy(app = AssignedApp("pkg", null, "App"))
        assertFalse(empty.shouldLoadSnapshotIcon(true))
        assertFalse(assigned.shouldLoadSnapshotIcon(false))
        assertTrue(assigned.shouldLoadSnapshotIcon(true))
    }
}
