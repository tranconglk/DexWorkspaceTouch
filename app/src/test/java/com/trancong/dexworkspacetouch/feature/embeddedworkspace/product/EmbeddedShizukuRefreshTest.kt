package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppSessionId
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionPhase
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceCleanupOutcome
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceItemReceipt
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedShizukuRefreshTest {
    @Test fun receivedBinderRefreshesUnavailableRouteWithoutAllocation() = runTest {
        val fixture = Fixture(backgroundScope, EmbeddedCapabilitySnapshot(true, false, false))
        fixture.refresh.attach()
        assertEquals(EmbeddedReadinessResult.ShizukuUnavailable, fixture.refresh.state.value?.readiness)
        fixture.probe.value = EmbeddedCapabilitySnapshot(true, true, false, shizukuLaunchAvailable = true)
        fixture.callbacks.received!!.invoke()
        assertEquals(EmbeddedReadinessResult.ShizukuPermissionMissing, fixture.refresh.state.value?.readiness)
        assertTrue(fixture.refresh.state.value!!.capability.shizukuLaunchAvailable)
        fixture.assertZeroAllocation()
    }

    @Test fun permissionGrantedWhileRouteOpenBecomesReadyWithoutRestartOrStart() = runTest {
        val fixture = Fixture(backgroundScope, EmbeddedCapabilitySnapshot(true, true, false))
        fixture.refresh.attach()
        fixture.probe.value = EmbeddedCapabilitySnapshot(true, true, true)
        fixture.callbacks.permission!!.invoke(41008, true)
        assertEquals(EmbeddedReadinessResult.Ready, fixture.refresh.state.value?.readiness)
        assertFalse(fixture.refresh.state.value!!.permissionDenied)
        fixture.assertZeroAllocation()
    }

    @Test fun deniedPermissionIsObservedWithoutRepeatedAutomaticRequest() = runTest {
        val fixture = Fixture(backgroundScope, EmbeddedCapabilitySnapshot(true, true, false))
        fixture.refresh.attach()
        fixture.callbacks.permission!!.invoke(41008, false)
        assertEquals(EmbeddedReadinessResult.ShizukuPermissionMissing, fixture.refresh.state.value?.readiness)
        assertTrue(fixture.refresh.state.value!!.permissionDenied)
        fixture.refresh.refresh()
        assertTrue(fixture.refresh.state.value!!.permissionDenied)
        fixture.assertZeroAllocation()
    }

    @Test fun permissionRequestRequiresExplicitActionAndCurrentProductPermission() = runTest {
        val fixture = Fixture(backgroundScope, EmbeddedCapabilitySnapshot(true, true, false))
        fixture.refresh.attach()
        fixture.assertZeroAllocation()
        assertTrue(fixture.refresh.requestPermission())
        assertEquals(1, fixture.calls.permissionRequests)
        assertEquals(41008, fixture.callbacks.lastPermissionRequestCode)
        fixture.gate.tryAcquireEmbedded("owned")!!
        assertFalse(fixture.refresh.requestPermission())
        assertEquals(1, fixture.calls.permissionRequests)
        assertEquals(0, fixture.calls.startCalls)
    }

    @Test fun foreignPermissionCallbackCannotInventDenialOrRefresh() = runTest {
        val fixture = Fixture(backgroundScope, EmbeddedCapabilitySnapshot(true, true, false))
        fixture.refresh.attach()
        val reads = fixture.probe.reads
        fixture.callbacks.permission!!.invoke(123, false)
        assertEquals(reads, fixture.probe.reads)
        assertFalse(fixture.refresh.state.value!!.permissionDenied)
        fixture.assertZeroAllocation()
    }

    @Test fun resumeAndExplicitRefreshReadCurrentHostWithoutAllocation() = runTest {
        val fixture = Fixture(backgroundScope, EmbeddedCapabilitySnapshot(true, true, true))
        fixture.hostReady = false
        fixture.refresh.attach()
        assertEquals(EmbeddedReadinessResult.RendererNotReady, fixture.refresh.state.value?.readiness)
        fixture.hostReady = true
        fixture.refresh.onResume()
        assertEquals(EmbeddedReadinessResult.Ready, fixture.refresh.state.value?.readiness)
        fixture.refresh.refresh()
        assertEquals(EmbeddedReadinessResult.Ready, fixture.refresh.state.value?.readiness)
        fixture.assertZeroAllocation()
    }

    @Test fun binderDeathClearsOldDenialAndRemainsGenericUnavailable() = runTest {
        val fixture = Fixture(backgroundScope, EmbeddedCapabilitySnapshot(true, true, false))
        fixture.refresh.attach()
        fixture.callbacks.permission!!.invoke(41008, false)
        fixture.probe.value = EmbeddedCapabilitySnapshot(true, false, false)
        fixture.callbacks.dead!!.invoke()
        assertEquals(EmbeddedReadinessResult.ShizukuUnavailable, fixture.refresh.state.value?.readiness)
        assertFalse(fixture.refresh.state.value!!.permissionDenied)
        fixture.probe.value = EmbeddedCapabilitySnapshot(true, true, false)
        fixture.callbacks.received!!.invoke()
        assertFalse(fixture.refresh.state.value!!.permissionDenied)
        fixture.assertZeroAllocation()
    }

    @Test fun attachIsIdempotentAndDisposeRemovesAllThreeListeners() = runTest {
        val fixture = Fixture(backgroundScope, EmbeddedCapabilitySnapshot(true, true, true))
        fixture.refresh.attach()
        fixture.refresh.attach()
        assertEquals(3, fixture.callbacks.registrations)
        assertEquals(3, fixture.callbacks.activeListeners)
        fixture.refresh.dispose()
        fixture.refresh.dispose()
        assertEquals(3, fixture.callbacks.removals)
        assertEquals(0, fixture.callbacks.activeListeners)
        fixture.assertZeroAllocation()
    }

    @Test fun callbacksAndResumeAfterDisposeCannotChangeSnapshotOrRequestPermission() = runTest {
        val fixture = Fixture(backgroundScope, EmbeddedCapabilitySnapshot(true, true, false))
        fixture.refresh.attach()
        val received = fixture.callbacks.received!!
        val dead = fixture.callbacks.dead!!
        val permission = fixture.callbacks.permission!!
        val before = fixture.refresh.state.value
        val reads = fixture.probe.reads
        fixture.refresh.dispose()
        fixture.probe.value = EmbeddedCapabilitySnapshot(true, true, true)
        received()
        dead()
        permission(41008, true)
        fixture.refresh.onResume()
        fixture.refresh.refresh()
        assertFalse(fixture.refresh.requestPermission())
        assertEquals(before, fixture.refresh.state.value)
        assertEquals(reads, fixture.probe.reads)
        fixture.assertZeroAllocation()
    }

    @Test fun oldSubscriptionCallbackCannotRefreshAReattachedRoute() = runTest {
        val fixture = Fixture(backgroundScope, EmbeddedCapabilitySnapshot(true, true, false))
        fixture.refresh.attach()
        val oldReceived = fixture.callbacks.received!!
        val oldPermission = fixture.callbacks.permission!!
        fixture.refresh.dispose()
        fixture.refresh.attach()
        fixture.probe.value = EmbeddedCapabilitySnapshot(true, true, true)
        val reads = fixture.probe.reads
        oldReceived()
        oldPermission(41008, false)
        assertEquals(reads, fixture.probe.reads)
        assertEquals(EmbeddedReadinessResult.ShizukuPermissionMissing, fixture.refresh.state.value?.readiness)
        fixture.callbacks.received!!.invoke()
        assertEquals(EmbeddedReadinessResult.Ready, fixture.refresh.state.value?.readiness)
        assertFalse(fixture.refresh.state.value!!.permissionDenied)
        fixture.assertZeroAllocation()
    }

    @Test fun lateShizukuCallbackCannotExposeClassicInStartingActiveStoppingOrCleanupBlocked() = runTest {
        for (phase in listOf(ProductRunPhase.STARTING, ProductRunPhase.ACTIVE,
            ProductRunPhase.STOPPING, ProductRunPhase.CLEANUP_BLOCKED)) {
            val fixture = Fixture(backgroundScope, EmbeddedCapabilitySnapshot(true, false, false))
            fixture.refresh.attach()
            val lateReceived = fixture.callbacks.received!!
            val latePermission = fixture.callbacks.permission!!
            val token = fixture.gate.tryAcquireEmbedded("owned")!!
            when (phase) {
                ProductRunPhase.ACTIVE -> fixture.gate.acceptResult(token, EmbeddedWorkspaceRunResult.Started(emptyList()))
                ProductRunPhase.STOPPING -> fixture.gate.markStopping(token)
                ProductRunPhase.CLEANUP_BLOCKED -> fixture.gate.markUncertain(token)
                else -> Unit
            }
            val before = fixture.product.currentRecovery()
            fixture.probe.value = EmbeddedCapabilitySnapshot(true, true, true)
            lateReceived()
            latePermission(41008, true)
            assertEquals(EmbeddedReadinessResult.Ready, fixture.refresh.state.value?.readiness)
            val after = fixture.product.currentRecovery()
            assertEquals(before, after)
            assertFalse("$phase", EmbeddedRecoveryAction.OPEN_CLASSIC in after.permittedActions)
            val visible = EmbeddedWorkspaceReadiness.withRecovery(fixture.refresh.state.value!!.readiness, after)
            assertFalse("$phase / UI", EmbeddedRecoveryAction.OPEN_CLASSIC in visible.permittedActions)
            assertFalse(fixture.gate.tryDispatchClassic { fixture.calls.classicDispatches++ })
            assertFalse(fixture.refresh.requestPermission())
            fixture.assertZeroAllocation()
        }
    }

    @Test fun refreshAtIdleCannotReplaceUncertainTaskOneEvidenceWithAbsent() = runTest {
        val fixture = Fixture(backgroundScope, EmbeddedCapabilitySnapshot(true, true, true), missingStartResult = true)
        fixture.product.start()
        val before = fixture.product.currentRecovery()
        fixture.refresh.attach()
        fixture.callbacks.permission!!.invoke(41008, true)
        assertEquals(before, fixture.product.currentRecovery())
        assertEquals(AllocationEvidence.UNKNOWN, fixture.product.currentRecovery().allocationEvidence)
        assertFalse(EmbeddedRecoveryAction.OPEN_CLASSIC in fixture.product.currentRecovery().permittedActions)
    }

    @Test fun callbackMakesStartAvailableAfterReadinessFailureWithoutChangingEvidence() = runTest {
        val fixture = Fixture(backgroundScope, EmbeddedCapabilitySnapshot(true, true, false))
        assertTrue(fixture.product.start() is ProductStartOutcome.NotReady)
        val before = fixture.product.currentRecovery()
        fixture.refresh.attach()
        fixture.probe.value = EmbeddedCapabilitySnapshot(true, true, true)
        fixture.callbacks.permission!!.invoke(41008, true)
        val visible = EmbeddedWorkspaceReadiness.withRecovery(fixture.refresh.state.value!!.readiness,
            fixture.product.currentRecovery())
        assertEquals(before.allocationEvidence, visible.allocationEvidence)
        assertEquals(before.cleanupEvidence, visible.cleanupEvidence)
        assertTrue(EmbeddedRecoveryAction.START_EMBEDDED in visible.permittedActions)
        fixture.assertZeroAllocation()
        assertTrue(fixture.product.start() is ProductStartOutcome.RunResult)
        assertEquals(1, fixture.calls.startCalls)
        assertTrue(fixture.product.requestExit())
    }

    @Test fun onlyExplicitProductStartReachesAllocationBoundaries() = runTest {
        val fixture = Fixture(backgroundScope, EmbeddedCapabilitySnapshot(true, true, true))
        fixture.refresh.attach()
        fixture.callbacks.received!!.invoke()
        fixture.refresh.onResume()
        fixture.assertZeroAllocation()
        fixture.product.start()
        assertEquals(1, fixture.calls.startCalls)
        assertEquals(1, fixture.calls.leases)
        assertEquals(1, fixture.calls.connects)
        assertEquals(1, fixture.calls.binds)
        assertEquals(1, fixture.calls.sessions)
        assertEquals(1, fixture.calls.vdms)
        assertEquals(0, fixture.calls.permissionRequests)
        assertTrue(fixture.product.requestExit())
    }

    private class Calls {
        var leases = 0
        var connects = 0
        var binds = 0
        var sessions = 0
        var vdms = 0
        var startCalls = 0
        var permissionRequests = 0
        var classicDispatches = 0
    }

    private class Probe(var value: EmbeddedCapabilitySnapshot) : EmbeddedCapabilityProbe {
        var reads = 0
        override fun snapshot(): EmbeddedCapabilitySnapshot { reads++; return value }
    }

    private class Callbacks(private val calls: Calls) : EmbeddedShizukuCallbacks {
        var received: (() -> Unit)? = null
        var dead: (() -> Unit)? = null
        var permission: ((Int, Boolean) -> Unit)? = null
        var registrations = 0
        var removals = 0
        var lastPermissionRequestCode: Int? = null
        val activeListeners get() = listOf(received, dead, permission).count { it != null }

        override fun listenBinderReceived(listener: () -> Unit): AutoCloseable {
            registrations++
            received = listener
            return AutoCloseable { if (received === listener) { received = null; removals++ } }
        }
        override fun listenBinderDead(listener: () -> Unit): AutoCloseable {
            registrations++
            dead = listener
            return AutoCloseable { if (dead === listener) { dead = null; removals++ } }
        }
        override fun listenPermissionResult(listener: (Int, Boolean) -> Unit): AutoCloseable {
            registrations++
            permission = listener
            return AutoCloseable { if (permission === listener) { permission = null; removals++ } }
        }
        override fun requestPermission(requestCode: Int) {
            calls.permissionRequests++
            lastPermissionRequestCode = requestCode
        }
    }

    private class Fixture(scope: CoroutineScope, capability: EmbeddedCapabilitySnapshot, missingStartResult: Boolean = false) {
        val calls = Calls()
        val probe = Probe(capability)
        val callbacks = Callbacks(calls)
        val gate = EmbeddedProductRunGate()
        var hostReady = true
        private val receipt = EmbeddedWorkspaceItemReceipt("cell", EmbeddedAppSessionId("session"),
            "pkg", "pkg.Main", 0, EmbeddedSessionPhase.ACTIVE, 10)
        val product = EmbeddedWorkspaceProductController("ws", gate, probe,
            { ProductHostReadiness(hostReady, hostReady) }, object : EmbeddedProductExecution {
                override suspend fun start(): EmbeddedWorkspaceRunResult? {
                    calls.startCalls++
                    if (missingStartResult) return null
                    calls.leases++
                    calls.connects++
                    calls.binds++
                    calls.sessions++
                    calls.vdms++
                    return EmbeddedWorkspaceRunResult.Started(listOf(receipt))
                }
                override suspend fun close() = EmbeddedWorkspaceRunResult.Stopped(
                    listOf(receipt), listOf(EmbeddedWorkspaceCleanupOutcome.Clean("cell")),
                )
            }, scope)
        val refresh = EmbeddedShizukuRefresh(probe, callbacks,
            { ProductHostReadiness(hostReady, hostReady) }, { product.currentRecovery() })

        fun assertZeroAllocation() {
            assertEquals("lease acquisition", 0, calls.leases)
            assertEquals("connect", 0, calls.connects)
            assertEquals("bind", 0, calls.binds)
            assertEquals("session creation", 0, calls.sessions)
            assertEquals("VDM creation", 0, calls.vdms)
            assertEquals("automatic permission request", 0, calls.permissionRequests)
            assertEquals("automatic Start", 0, calls.startCalls)
            assertEquals("Classic dispatch", 0, calls.classicDispatches)
        }
    }
}
