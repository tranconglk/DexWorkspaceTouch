package com.trancong.dexworkspacetouch.workspace.externaltransfer

import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.librarytransfer.DeterministicWorkspaceLibraryBundleSerializer
import com.trancong.dexworkspacetouch.workspace.librarytransfer.WorkspaceLibraryExport
import com.trancong.dexworkspacetouch.workspace.librarytransfer.WorkspaceLibraryTransferFormat
import com.trancong.dexworkspacetouch.workspace.transfer.DeterministicWorkspaceTransferSerializer
import com.trancong.dexworkspacetouch.workspace.transfer.WorkspaceExport
import com.trancong.dexworkspacetouch.workspace.transfer.WorkspaceImportPayload
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalTransferDetectorTest {
    @Test fun detectsSingleAndBundleByContentOnly() {
        assertEquals(ExternalTransferDetection.SingleWorkspace, ExternalTransferDetector.detect(single()))
        assertEquals(ExternalTransferDetection.LibraryBundle, ExternalTransferDetector.detect(bundle()))
    }

    @Test fun wrongMimeOrExtensionCannotChangeContentDetection() {
        // Detector intentionally has no MIME/extension input: those values are diagnostics-only hints.
        assertEquals(ExternalTransferDetection.SingleWorkspace, ExternalTransferDetector.detect(single()))
        assertEquals(ExternalTransferDetection.Invalid, ExternalTransferDetector.detect("not a .dwt".toByteArray()))
    }

    @Test fun unsupportedVersionsAreTyped() {
        assertEquals(ExternalTransferDetection.UnsupportedVersion, ExternalTransferDetector.detect(single().toString(Charsets.UTF_8).replace("\"formatVersion\":1", "\"formatVersion\":2").toByteArray()))
        assertEquals(ExternalTransferDetection.UnsupportedVersion, ExternalTransferDetector.detect(bundle().toString(Charsets.UTF_8).replace("\"formatVersion\":1", "\"formatVersion\":2").toByteArray()))
    }

    @Test fun boundedReaderRejectsOversizeAndCallerUseClosesStream() {
        val stream = TrackingInputStream(ByteArray(WorkspaceLibraryTransferFormat.MaxBytes + 1))
        try { stream.use(::readExternalTransferBytes); throw AssertionError("Expected oversize") }
        catch (_: ExternalTransferTooLargeException) { }
        assertTrue(stream.closed)
    }

    @Test fun duplicateConsumedAndDesignerPendingPoliciesAreStable() {
        val inbox = ExternalTransferViewModel()
        assertTrue(inbox.begin("view|content://one"))
        assertFalse(inbox.begin("view|content://one"))
        inbox.accept("view|content://one", single(), ExternalTransferDetection.SingleWorkspace)
        assertFalse(shouldDispatchExternalTransfer(isLibraryVisible = false))
        assertTrue(inbox.state is ExternalTransferInboxState.Pending)
        inbox.consume("view|content://one")
        assertEquals(ExternalTransferInboxState.Idle, inbox.state)
        assertFalse(inbox.begin("view|content://one"))
        assertTrue(inbox.begin("view|content://one", allowConsumedReplay = true))
        assertFalse(inbox.begin("view|content://one", allowConsumedReplay = true))
    }

    private fun single() = DeterministicWorkspaceTransferSerializer().encode(WorkspaceExport("One", WorkspaceCanvas.singleCell(), 1))
    private fun bundle() = DeterministicWorkspaceLibraryBundleSerializer().encode(WorkspaceLibraryExport(listOf(WorkspaceImportPayload("One", WorkspaceCanvas.singleCell(), 1)), 1))

    private class TrackingInputStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
        override fun close() { closed = true; super.close() }
    }
}
