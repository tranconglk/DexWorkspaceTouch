package com.trancong.dexworkspacetouch.platform.launch.shizuku

import org.junit.Assert.*
import org.junit.Test

class WorkspaceCommandDeadlineTest {
    @Test fun binderQueueTimeIsDeductedFromRemainingBudget() {
        assertEquals(750L, workspaceDeadlineRemaining(13000, 12250))
    }
    @Test fun expiredAndEqualDeadlinesCannotDispatch() {
        listOf(12999L, 13000L).forEach { deadline -> failure(CommandTransportFailure.TIMEOUT) { workspaceDeadlineRemaining(deadline, 13000) } }
    }
    @Test fun overlongAndOverflowingDeadlineIsRejected() {
        failure(CommandTransportFailure.INVALID_COMMAND) { workspaceDeadlineRemaining(21001, 13000) }
        failure(CommandTransportFailure.INVALID_COMMAND) { workspaceDeadlineRemaining(Long.MAX_VALUE, 13000) }
    }
    private fun failure(reason: CommandTransportFailure, action: () -> Unit) {
        try { action(); fail("Expected $reason") } catch(e: CommandTransportException) { assertEquals(reason, e.failure) }
    }
}
