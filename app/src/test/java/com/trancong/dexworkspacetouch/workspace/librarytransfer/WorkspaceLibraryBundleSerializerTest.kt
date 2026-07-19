package com.trancong.dexworkspacetouch.workspace.librarytransfer

import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell
import com.trancong.dexworkspacetouch.workspace.transfer.WorkspaceImportPayload
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WorkspaceLibraryBundleSerializerTest {
    private val serializer = DeterministicWorkspaceLibraryBundleSerializer()

    @Test fun deterministicUnicodeRoundTripAndCanonicalOrder() {
        val export = WorkspaceLibraryExport(listOf(item("Zalo"), item("Đi đường", null)), 1_784_000_000_000L)
        assertArrayEquals(serializer.encode(export), serializer.encode(export))
        val decoded = serializer.decode(serializer.encode(export))
        assertEquals(listOf("Zalo", "Đi đường"), decoded.workspaces.map { it.name })
        assertEquals(export.workspaces.associateBy { it.name }, decoded.workspaces.associateBy { it.name })
    }

    @Test fun oneAndOneHundredWorkspacesRoundTrip() {
        assertEquals(1, roundTrip(listOf(item("One"))).workspaces.size)
        assertEquals(100, roundTrip((1..100).map { item("Workspace $it") }).workspaces.size)
    }

    @Test fun fiveCellsAndNullableActivityRoundTrip() {
        val cells = (0..4).map { i -> WorkspaceCell("c$i", NormalizedBounds(i / 5f, 0f, (i + 1) / 5f, 1f), AssignedApp("pkg", null, "Ứng dụng")) }
        assertEquals(5, roundTrip(listOf(WorkspaceImportPayload("Five", WorkspaceCanvas(cells), 1))).workspaces.single().canvas.cells.size)
    }

    @Test fun malformedWrongVersionUnknownTrailingAndCountMismatchAreRejected() {
        val valid = serializer.encode(WorkspaceLibraryExport(listOf(item("One")), 0)).toString(Charsets.UTF_8)
        invalid("{")
        invalid(valid.replace("dex-workspace-touch-library", "wrong"))
        failure(WorkspaceLibraryTransferFailure.UNSUPPORTED_VERSION, valid.replace("\"formatVersion\":1", "\"formatVersion\":2"))
        invalid(valid.replaceFirst("{", "{\"unknown\":1,"))
        invalid(valid + "x")
        invalid(valid.replace("\"workspaceCount\":1", "\"workspaceCount\":2"))
    }

    @Test fun tooManyWorkspacesAndOversizedInputAreRejected() {
        failure(WorkspaceLibraryTransferFailure.TOO_MANY_WORKSPACES) { serializer.encode(WorkspaceLibraryExport((0..100).map { item("W$it") }, 0)) }
        failure(WorkspaceLibraryTransferFailure.FILE_TOO_LARGE) { serializer.decode(ByteArray(WorkspaceLibraryTransferFormat.MaxBytes + 1)) }
    }

    @Test fun totalCellAndEncodedSizeLimitsRemainEnforcedForSelectedBundles() {
        val sixCells = (0 until 6).map { index ->
            WorkspaceCell(
                "cell-$index",
                NormalizedBounds(index / 6f, 0f, (index + 1) / 6f, 1f),
            )
        }
        failure(WorkspaceLibraryTransferFailure.TOO_MANY_CELLS) {
            serializer.encode(WorkspaceLibraryExport((1..100).map {
                WorkspaceImportPayload("Workspace $it", WorkspaceCanvas(sixCells), 1)
            }, 0))
        }
        val oversizedLabel = "x".repeat(WorkspaceLibraryTransferFormat.MaxBytes)
        failure(WorkspaceLibraryTransferFailure.FILE_TOO_LARGE) {
            serializer.encode(WorkspaceLibraryExport(listOf(
                WorkspaceImportPayload(
                    "Large",
                    WorkspaceCanvas(listOf(WorkspaceCell(
                        "cell",
                        NormalizedBounds.FullCanvas,
                        AssignedApp("pkg", "activity", oversizedLabel),
                    ))),
                    1,
                ),
            ), 0))
        }
    }

    @Test fun localMetadataIsNeverExported() {
        val text = serializer.encode(WorkspaceLibraryExport(listOf(item("One")), 0)).toString(Charsets.UTF_8)
        listOf("isPinned", "modifiedSequence", "createdAt", "updatedAt", "databaseId").forEach { assertFalse(text.contains(it)) }
    }

    private fun roundTrip(items: List<WorkspaceImportPayload>) = serializer.decode(serializer.encode(WorkspaceLibraryExport(items, 42)))
    private fun item(name: String, activity: String? = "Activity") = WorkspaceImportPayload(name, WorkspaceCanvas(listOf(WorkspaceCell("cell", NormalizedBounds.FullCanvas, AssignedApp("pkg", activity, "App")))), 1)
    private fun invalid(text: String) = failure(WorkspaceLibraryTransferFailure.INVALID_FORMAT) { serializer.decode(text.toByteArray()) }
    private fun failure(expected: WorkspaceLibraryTransferFailure, block: () -> Unit) { try { block(); throw AssertionError("Expected $expected") } catch (error: WorkspaceLibraryTransferException) { assertEquals(expected, error.failure) } }
    private fun failure(expected: WorkspaceLibraryTransferFailure, text: String) = failure(expected) { serializer.decode(text.toByteArray()) }
}
