package com.trancong.dexworkspacetouch.feature.embeddedapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedAppSessionPolicyTest {
    @Test fun repeatedStopAndCloseConvergeOnOneRemoteCleanup() {
        val gate = SessionStopGate()
        assertTrue(gate.request())
        assertFalse(gate.request())
        assertFalse(gate.request())
    }

    @Test fun aNewSuccessfulSessionCanResetTheStopGate() {
        val gate = SessionStopGate()
        assertTrue(gate.request())
        gate.reset()
        assertTrue(gate.request())
    }
}
