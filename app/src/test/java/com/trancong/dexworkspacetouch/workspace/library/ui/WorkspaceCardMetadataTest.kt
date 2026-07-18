package com.trancong.dexworkspacetouch.workspace.library.ui

import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceCardMetadataTest {
    @Test fun countsCellsAndAssignmentsIncludingDuplicateApps() {
        val app = AssignedApp("pkg", "Activity", "App")
        val canvas = WorkspaceCanvas(listOf(
            WorkspaceCell("a", NormalizedBounds(0f, 0f, .5f, 1f), app),
            WorkspaceCell("b", NormalizedBounds(.5f, 0f, 1f, 1f), app),
        ))
        assertEquals("2 ô • 2 ứng dụng", canvas.cardMetadata().compactText)
    }

    @Test fun singleEmptyCellMetadataIsStable() {
        assertEquals("1 ô • 0 ứng dụng", WorkspaceCanvas.singleCell().cardMetadata().compactText)
    }

    @Test fun relativeTimeCoversBoundariesAndFuture() {
        val now = 1_800_000_000_000L
        assertEquals("Vừa cập nhật", WorkspaceRelativeTimeFormatter.format(now, now))
        assertEquals("Vừa cập nhật", WorkspaceRelativeTimeFormatter.format(now + 1_000, now))
        assertEquals("5 phút trước", WorkspaceRelativeTimeFormatter.format(now - 5 * 60_000, now))
        assertEquals("2 giờ trước", WorkspaceRelativeTimeFormatter.format(now - 2 * 3_600_000, now))
        assertEquals("Hôm qua", WorkspaceRelativeTimeFormatter.format(now - 25 * 3_600_000, now))
        assertEquals("3 ngày trước", WorkspaceRelativeTimeFormatter.format(now - 3 * 86_400_000, now))
        assertEquals("03/01/1970", WorkspaceRelativeTimeFormatter.format(2 * 86_400_000, now))
    }
}
