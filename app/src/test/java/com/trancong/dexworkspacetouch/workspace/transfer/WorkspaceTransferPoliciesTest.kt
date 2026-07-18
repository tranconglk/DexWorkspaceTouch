package com.trancong.dexworkspacetouch.workspace.transfer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class WorkspaceTransferPoliciesTest {
    @Test fun importNamesHandleUniqueDuplicatesGapsCaseSuffixAndUnicode() {
        assertEquals("Đi đường", WorkspaceImportNamePolicy.nextName(" Đi đường ", emptyList()))
        assertEquals("Đi đường (Đã nhập)", WorkspaceImportNamePolicy.nextName("Đi đường", listOf("đi ĐƯỜNG")))
        assertEquals("Đi đường (Đã nhập 2)", WorkspaceImportNamePolicy.nextName("Đi đường", listOf("Đi đường", "Đi đường (Đã nhập)")))
        assertEquals("Đi đường (Đã nhập 2)", WorkspaceImportNamePolicy.nextName("Đi đường", listOf("Đi đường", "Đi đường (Đã nhập)", "Đi đường (Đã nhập 3)")))
        assertEquals("Đi đường (Đã nhập)", WorkspaceImportNamePolicy.nextName("Đi đường (Đã nhập 9)", listOf("Đi đường")))
    }

    @Test fun filenameSanitizerRemovesTraversalAndFallsBack() {
        assertEquals("Đi đường.dwt", sanitizeWorkspaceFileName("Đi/đường"))
        assertEquals("workspace.dwt", sanitizeWorkspaceFileName("../.."))
    }

    @Test fun byteReaderEnforcesLimitAndConstantsAreStable() {
        assertEquals("dwt", WorkspaceTransferFormat.Extension)
        assertEquals(3, readWorkspaceBytes(ByteArrayInputStream(byteArrayOf(1, 2, 3))).size)
        try { readWorkspaceBytes(ByteArrayInputStream(ByteArray(WorkspaceTransferFormat.MaxBytes + 1))); throw AssertionError() }
        catch (e: WorkspaceTransferException) { assertEquals(WorkspaceTransferFailure.FILE_TOO_LARGE, e.failure) }
    }
}
