package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import org.junit.Assert.assertEquals
import org.junit.Test

class EmbeddedWorkspaceReadinessTest {
    @Test fun absentShizukuBlocksEmbeddedOnlyBeforeAllocation() {
        val probe = FakeProbe(EmbeddedCapabilitySnapshot(true, false, false))
        val result = EmbeddedWorkspaceReadiness.evaluate(probe.snapshot(), true, true, true)
        assertEquals(EmbeddedReadinessResult.ShizukuUnavailable, result)
        assertEquals(1, probe.reads)
    }

    @Test fun permissionAndRendererAreIndependentReasons() {
        assertEquals(EmbeddedReadinessResult.ShizukuPermissionMissing,
            EmbeddedWorkspaceReadiness.evaluate(EmbeddedCapabilitySnapshot(true, true, false), true, true, true))
        assertEquals(EmbeddedReadinessResult.RendererNotReady,
            EmbeddedWorkspaceReadiness.evaluate(EmbeddedCapabilitySnapshot(true, true, true), true, false, true))
    }

    @Test fun retryCanBecomeReadyWithoutRestart() {
        val probe = FakeProbe(EmbeddedCapabilitySnapshot(true, false, false))
        assertEquals(EmbeddedReadinessResult.ShizukuUnavailable,
            EmbeddedWorkspaceReadiness.evaluate(probe.snapshot(), true, true, true))
        probe.value = EmbeddedCapabilitySnapshot(true, true, true)
        assertEquals(EmbeddedReadinessResult.Ready,
            EmbeddedWorkspaceReadiness.evaluate(probe.snapshot(), true, true, true))
    }

    private class FakeProbe(var value: EmbeddedCapabilitySnapshot) : EmbeddedCapabilityProbe {
        var reads = 0
        override fun snapshot(): EmbeddedCapabilitySnapshot { reads++; return value }
    }
}
