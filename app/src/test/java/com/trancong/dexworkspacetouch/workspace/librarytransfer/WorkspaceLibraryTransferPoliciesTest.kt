package com.trancong.dexworkspacetouch.workspace.librarytransfer

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceLibraryTransferPoliciesTest {
    @Test fun filenameUsesBundleExtension() {
        val name = libraryBackupFileName(0)
        assertTrue(name.startsWith("DexWorkspaceTouch-backup-"))
        assertTrue(name.endsWith(".dwtbundle"))
        assertEquals("application/vnd.dexworkspacetouch.library+json", WorkspaceLibraryTransferFormat.MimeType)
    }

    @Test fun boundedReaderAcceptsLimitAndRejectsOneByteMore() {
        assertEquals(WorkspaceLibraryTransferFormat.MaxBytes, readWorkspaceLibraryBytes(ByteArrayInputStream(ByteArray(WorkspaceLibraryTransferFormat.MaxBytes))).size)
        try {
            readWorkspaceLibraryBytes(ByteArrayInputStream(ByteArray(WorkspaceLibraryTransferFormat.MaxBytes + 1)))
            throw AssertionError("Expected FILE_TOO_LARGE")
        } catch (error: WorkspaceLibraryTransferException) {
            assertEquals(WorkspaceLibraryTransferFailure.FILE_TOO_LARGE, error.failure)
        }
    }
}
