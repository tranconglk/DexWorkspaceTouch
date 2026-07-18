package com.trancong.dexworkspacetouch.workspace.transfer

import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceTransferSerializerTest {
    private val serializer = DeterministicWorkspaceTransferSerializer()

    @Test fun deterministicRoundTripWithUnicodeAndNullableActivity() {
        val export = WorkspaceExport("Đi đường", canvas(null), 42)
        assertArrayEquals(serializer.encode(export), serializer.encode(export))
        val decoded = serializer.decode(serializer.encode(export))
        assertEquals(export.name, decoded.name)
        assertEquals(export.canvas, decoded.canvas)
    }

    @Test fun roundTripAcceptsRealisticEpochMillisBeyondIntRange() {
        val export = WorkspaceExport("Workspace 1", canvas("Activity"), 1_784_370_315_715L)

        val decoded = serializer.decode(serializer.encode(export))

        assertEquals(export.name, decoded.name)
        assertEquals(export.canvas, decoded.canvas)
    }

    @Test fun oneAndFiveCellsRoundTrip() {
        assertEquals(1, serializer.decode(serializer.encode(WorkspaceExport("Một", WorkspaceCanvas.singleCell(), 0))).canvas.cells.size)
        val five = WorkspaceCanvas((0..4).map { i -> WorkspaceCell("c$i", NormalizedBounds(i / 5f, 0f, (i + 1) / 5f, 1f)) })
        assertEquals(5, serializer.decode(serializer.encode(WorkspaceExport("Năm", five, 0))).canvas.cells.size)
    }

    @Test fun malformedWrongFormatDuplicateUnknownAndTrailingAreRejected() {
        listOf("{", "{}", canonical().replaceFirst("\"format\"", "\"format\":\"x\",\"format\""),
            canonical().replaceFirst("{", "{\"unknown\":1,"), canonical() + "x").forEach { invalid(it) }
    }

    @Test fun unsupportedTransferAndWorkspaceVersionsAreRejected() {
        assertFailure(WorkspaceTransferFailure.UNSUPPORTED_VERSION, canonical().replace("\"formatVersion\":1", "\"formatVersion\":2"))
        assertFailure(WorkspaceTransferFailure.UNSUPPORTED_VERSION, canonical().replace("\"workspaceSchemaVersion\":1", "\"workspaceSchemaVersion\":2"))
    }

    @Test fun oversizedPayloadIsRejected() {
        try { serializer.decode(ByteArray(WorkspaceTransferFormat.MaxBytes + 1)); throw AssertionError() }
        catch (e: WorkspaceTransferException) { assertEquals(WorkspaceTransferFailure.FILE_TOO_LARGE, e.failure) }
    }

    @Test fun invalidCanvasAndNonFiniteNumbersAreRejected() {
        assertFailure(WorkspaceTransferFailure.INVALID_WORKSPACE, canonical().replace("\"cells\":[", "\"cells\":[{\"id\":\"bad\",\"bounds\":{\"left\":NaN,\"top\":0.0,\"right\":1.0,\"bottom\":1.0},\"app\":null},"))
    }

    @Test fun envelopeDoesNotContainLocalMetadata() {
        val text = canonical()
        listOf("isPinned", "modifiedSequence", "createdAt", "updatedAt", "\"id\":\"database").forEach { assertFalse(text.contains(it)) }
        assertTrue(text.startsWith("{\"format\":\"dex-workspace-touch\""))
    }

    private fun canonical() = serializer.encode(WorkspaceExport("Name", canvas("Activity"), 1)).toString(Charsets.UTF_8)
    private fun canvas(activity: String?) = WorkspaceCanvas(listOf(WorkspaceCell("cell", NormalizedBounds.FullCanvas, AssignedApp("pkg", activity, "Ứng dụng"))))
    private fun invalid(json: String) = assertFailure(WorkspaceTransferFailure.INVALID_FORMAT, json)
    private fun assertFailure(expected: WorkspaceTransferFailure, json: String) {
        try { serializer.decode(json.toByteArray()); throw AssertionError("Expected $expected") }
        catch (e: WorkspaceTransferException) { assertEquals(expected, e.failure) }
    }
}
