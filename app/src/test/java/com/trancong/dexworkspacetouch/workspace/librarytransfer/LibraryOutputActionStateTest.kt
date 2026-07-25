package com.trancong.dexworkspacetouch.workspace.librarytransfer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryOutputActionStateTest {
    @Test
    fun `rapid second output action is rejected until first finishes`() {
        val first = beginLibraryOutputAction(LibraryOutputActionState())
        val second = beginLibraryOutputAction(first.state)

        assertTrue(first.shouldLaunch)
        assertTrue(first.state.inProgress)
        assertFalse(second.shouldLaunch)
        assertTrue(second.state.inProgress)
    }

    @Test
    fun `finished output action allows retry`() {
        val retry = beginLibraryOutputAction(finishLibraryOutputAction())

        assertTrue(retry.shouldLaunch)
        assertTrue(retry.state.inProgress)
    }
}
