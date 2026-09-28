package com.trancong.dexworkspacetouch.feature.embeddedapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedAppSessionStateTest {
    @Test fun generatedSessionIdsAreOpaqueAndUnique() {
        val first = newEmbeddedAppSessionId()
        val second = newEmbeddedAppSessionId()
        assertTrue(first.value.isNotBlank())
        assertNotEquals(first, second)
        assertFalse(first.value.contains("com.waze"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun serviceStateRejectsNegativeCounts() {
        EmbeddedAppServiceState(-1, 0, 0, 0)
    }

    @Test fun onlyAllZeroRemoteCountsProveEmpty() {
        assertTrue(EmbeddedAppServiceState(0, 0, 0, 0).provesEmpty)
        assertFalse(EmbeddedAppServiceState(1, 0, 0, 0).provesEmpty)
        assertFalse(EmbeddedAppServiceState(0, 1, 0, 0).provesEmpty)
        assertFalse(EmbeddedAppServiceState(0, 0, 1, 0).provesEmpty)
        assertFalse(EmbeddedAppServiceState(0, 0, 0, 1).provesEmpty)
    }

    @Test fun finalRemovalRequiresEveryLocalAndRemoteGate() {
        val empty = EmbeddedAppServiceState(0, 0, 0, 0)
        assertTrue(canRemoveEmbeddedAppService(0, false, true, empty))
        assertFalse(canRemoveEmbeddedAppService(1, false, true, empty))
        assertFalse(canRemoveEmbeddedAppService(0, true, true, empty))
        assertFalse(canRemoveEmbeddedAppService(0, false, false, empty))
        assertFalse(canRemoveEmbeddedAppService(0, false, true, null))
        assertFalse(canRemoveEmbeddedAppService(0, false, true,
            EmbeddedAppServiceState(0, 0, 0, 1)))
    }
}
