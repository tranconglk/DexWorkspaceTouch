package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedWorkspaceProductRoutingTest {
    @Test fun classicDispatchKeepsExistingCallbackAndEmbeddedKeepsExactId() {
        val gate = EmbeddedProductRunGate()
        val calls = mutableListOf<String>()
        val router = EmbeddedWorkspaceProductRouting(gate) { calls += "embedded:$it" }
        assertTrue(router.openClassic { calls += "classic:persisted-id" })
        assertTrue(router.openEmbedded("persisted-id"))
        assertEquals(listOf("classic:persisted-id", "embedded:persisted-id"), calls)
    }

    @Test fun shizukuStateDoesNotGateEntryButBlockedCleanupDoes() {
        val gate = EmbeddedProductRunGate()
        val calls = mutableListOf<String>()
        val router = EmbeddedWorkspaceProductRouting(gate) { calls += "embedded" }
        assertTrue(router.openEmbedded("ws"))
        assertEquals(listOf("embedded"), calls)
        val token = gate.tryAcquireEmbedded("ws")!!
        gate.acceptResult(token, EmbeddedWorkspaceRunResult.CleanupIncomplete(emptyList(), emptyList()))
        assertFalse(router.openClassic { calls += "classic" })
        assertFalse(router.openEmbedded("ws"))
        assertEquals(listOf("embedded"), calls)
    }

    @Test fun shizukuUnavailableOpensProductEntryWithoutAllocationOrClassicFallback() = runTest {
        val gate = EmbeddedProductRunGate()
        var productEntries = 0
        var embeddedStarts = 0
        val router = EmbeddedWorkspaceProductRouting(gate) { productEntries++ }
        assertTrue(router.openEmbedded("ws"))
        val controller = EmbeddedWorkspaceProductController("ws", gate,
            EmbeddedCapabilityProbe { EmbeddedCapabilitySnapshot(true, false, false) },
            { ProductHostReadiness(true, true) },
            object : EmbeddedProductExecution {
                override suspend fun start(): EmbeddedWorkspaceRunResult? { embeddedStarts++; return null }
                override suspend fun close(): EmbeddedWorkspaceRunResult? = null
            }, backgroundScope)
        assertEquals(ProductStartOutcome.NotReady(EmbeddedReadinessResult.ShizukuUnavailable), controller.start())
        assertEquals(1, productEntries)
        assertEquals(0, embeddedStarts)
        assertEquals(ProductRunPhase.IDLE, gate.state.value)
    }
}
