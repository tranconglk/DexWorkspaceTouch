package com.trancong.dexworkspacetouch.workspace.library.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceLibraryInteractionPolicyTest {
    @Test
    fun `active write blocks create edit management and export`() {
        val policy = workspaceLibraryInteractionPolicy(
            libraryIsLoading = false,
            libraryWriteInProgress = true,
            transferOperationActive = false,
        )

        assertFalse(policy.createEnabled)
        assertFalse(policy.editEnabled)
        assertFalse(policy.managementEnabled)
        assertFalse(policy.exportEnabled)
    }

    @Test
    fun `stable library enables actions while transfer blocks only export`() {
        val policy = workspaceLibraryInteractionPolicy(
            libraryIsLoading = false,
            libraryWriteInProgress = false,
            transferOperationActive = true,
        )

        assertTrue(policy.createEnabled)
        assertTrue(policy.editEnabled)
        assertTrue(policy.managementEnabled)
        assertFalse(policy.exportEnabled)
    }
}
