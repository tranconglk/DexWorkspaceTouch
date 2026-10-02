package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test fun resolvableShizukuLaunchDoesNotProveBinderAvailability() {
        val capability = EmbeddedCapabilitySnapshot(true, false, false, shizukuLaunchAvailable = true)
        assertEquals(EmbeddedReadinessResult.ShizukuUnavailable,
            EmbeddedWorkspaceReadiness.evaluate(capability, true, true, true))
    }

    @Test fun unavailableWithoutLaunchEvidenceKeepsGenericUnavailable() {
        val capability = EmbeddedCapabilitySnapshot(true, false, false, shizukuLaunchAvailable = false)
        assertEquals(EmbeddedReadinessResult.ShizukuUnavailable,
            EmbeddedWorkspaceReadiness.evaluate(capability, true, true, true))
    }

    @Test fun denialEvidenceAppliesOnlyToMissingPermissionWithLiveBinder() {
        val denied = EmbeddedCapabilitySnapshot(true, true, false, shizukuPermissionDenied = true)
        assertTrue(EmbeddedReadinessSnapshot(denied,
            EmbeddedWorkspaceReadiness.evaluate(denied, true, true, true)).permissionDenied)
        val unavailable = denied.copy(shizukuBinderAvailable = false)
        assertFalse(EmbeddedReadinessSnapshot(unavailable,
            EmbeddedWorkspaceReadiness.evaluate(unavailable, true, true, true)).permissionDenied)
    }

    @Test fun staleDenialNeverOverridesCurrentPermissionGrant() {
        val capability = EmbeddedCapabilitySnapshot(true, true, true, shizukuPermissionDenied = true)
        val readiness = EmbeddedWorkspaceReadiness.evaluate(capability, true, true, true)
        assertEquals(EmbeddedReadinessResult.Ready, readiness)
        assertFalse(EmbeddedReadinessSnapshot(capability, readiness).permissionDenied)
    }

    @Test fun shizukuReadyStillRequiresPlatformGeometryAndCurrentHost() {
        val capability = EmbeddedCapabilitySnapshot(true, true, true)
        assertEquals(EmbeddedReadinessResult.UnsupportedPlatform,
            EmbeddedWorkspaceReadiness.evaluate(capability.copy(platformSupported = false), true, true, true))
        assertEquals(EmbeddedReadinessResult.GeometryUnavailable,
            EmbeddedWorkspaceReadiness.evaluate(capability, false, true, true))
        assertEquals(EmbeddedReadinessResult.RendererNotReady,
            EmbeddedWorkspaceReadiness.evaluate(capability, true, true, false))
    }

    @Test fun refreshedReadinessPreservesEvidenceAndCannotAddClassicToUnresolvedPolicy() {
        val absent = EmbeddedProductRecoveryMapper.readiness(EmbeddedReadinessResult.ShizukuPermissionMissing,
            ProductRunPhase.IDLE, true)
        val ready = EmbeddedWorkspaceReadiness.withRecovery(EmbeddedReadinessResult.Ready, absent)
        assertEquals(absent.allocationEvidence, ready.allocationEvidence)
        assertEquals(absent.cleanupEvidence, ready.cleanupEvidence)
        assertTrue(EmbeddedRecoveryAction.START_EMBEDDED in ready.permittedActions)
        assertEquals(EmbeddedRecoveryAction.OPEN_CLASSIC in absent.permittedActions,
            EmbeddedRecoveryAction.OPEN_CLASSIC in ready.permittedActions)
        val unknown = EmbeddedProductRecoveryMapper.snapshot(ProductRunPhase.IDLE, true,
            EmbeddedProductIssue.ShizukuPermissionMissing, AllocationEvidence.UNKNOWN, CleanupEvidence.UNCERTAIN)
        assertEquals(unknown, EmbeddedWorkspaceReadiness.withRecovery(EmbeddedReadinessResult.Ready, unknown))
        val runtime = EmbeddedProductRecoveryMapper.snapshot(ProductRunPhase.IDLE, true,
            EmbeddedProductIssue.SurfaceLost("cell"), AllocationEvidence.POSSIBLE_OR_OWNED, CleanupEvidence.CLEAN_CONFIRMED)
        assertEquals(runtime, EmbeddedWorkspaceReadiness.withRecovery(EmbeddedReadinessResult.Ready, runtime))
    }

    private class FakeProbe(var value: EmbeddedCapabilitySnapshot) : EmbeddedCapabilityProbe {
        var reads = 0
        override fun snapshot(): EmbeddedCapabilitySnapshot { reads++; return value }
    }
}
