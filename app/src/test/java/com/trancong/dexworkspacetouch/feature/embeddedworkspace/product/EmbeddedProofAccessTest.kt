package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import com.trancong.dexworkspacetouch.navigation.EmbeddedProofRoute
import com.trancong.dexworkspacetouch.navigation.enterEmbeddedProofRoute
import com.trancong.dexworkspacetouch.ui.screens.HomeEmbeddedProofControl
import com.trancong.dexworkspacetouch.ui.screens.HomeEmbeddedProofMenu
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunResult
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Các counter đặt ngay sau admission, độc lập với enabled/visibility của nút. */
@RunWith(Parameterized::class)
internal class EmbeddedProofAccessTest(private val route: EmbeddedProofRoute, private val control: HomeEmbeddedProofControl,
    private val exactPattern: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{2}") fun routes() = listOf(
            arrayOf(EmbeddedProofRoute.WAZE, HomeEmbeddedProofControl.WAZE, "embedded-waze"),
            arrayOf(EmbeddedProofRoute.CALCULATOR, HomeEmbeddedProofControl.CALCULATOR, "embedded-calculator"),
            arrayOf(EmbeddedProofRoute.DUAL_APP, HomeEmbeddedProofControl.DUAL_APP, "embedded-dual-app"),
            arrayOf(EmbeddedProofRoute.RUNNER, HomeEmbeddedProofControl.RUNNER, "embedded-workspace-runner"),
            arrayOf(EmbeddedProofRoute.WORKSPACE_LAYOUT, HomeEmbeddedProofControl.WORKSPACE_LAYOUT,
                "embedded-workspace-layout/{workspaceId}"),
        )
    }

    @Test fun ordinaryHomeDoesNotExposeProofControl() {
        assertFalse(HomeEmbeddedProofMenu().controls.contains(control))
    }

    @Test fun deliberateDeveloperEntryExposesProofControlAndCloseHidesIt() {
        val ordinary = HomeEmbeddedProofMenu()
        val developer = ordinary.open()
        assertTrue(developer.isOpen)
        assertTrue(developer.controls.contains(control))
        assertFalse(developer.close().controls.contains(control))
    }

    @Test fun deliberateDeveloperEntryLabelsProofAsExperimentalAndFrozen() {
        val visible = HomeEmbeddedProofMenu().open().controls.single { it == control }
        assertTrue("Experimental label missing: ${visible.label}", visible.label.contains("Experimental"))
        assertTrue("Frozen label missing: ${visible.label}", visible.label.contains("Frozen"))
    }

    @Test fun exactDirectRouteRemainsDefinedAndIdleAdmitsExistingBehavior() {
        assertEquals(exactPattern, route.pattern)
        val gate = EmbeddedProductRunGate()
        val counters = Allocations()
        assertTrue(enterEmbeddedProofRoute(gate, route) { counters.constructProof() })
        assertEquals(listOf(0, 1, 1, 1, 1, 1, 1, 1, 1), counters.values())
        assertEquals(ProductRunStatus(), gate.status.value)
    }

    @Test fun startingDirectEntryRejectsBeforeEveryAllocation() = blockedEntry(ProductRunPhase.STARTING)
    @Test fun activeDirectEntryRejectsBeforeEveryAllocation() = blockedEntry(ProductRunPhase.ACTIVE)
    @Test fun stoppingDirectEntryRejectsBeforeEveryAllocation() = blockedEntry(ProductRunPhase.STOPPING)
    @Test fun cleanupBlockedDirectEntryRejectsBeforeEveryAllocation() = blockedEntry(ProductRunPhase.CLEANUP_BLOCKED)

    @Test fun staleDeveloperClickRechecksEveryNonIdlePhaseBeforeDispatch() {
        for (phase in nonIdle) {
            val gate = EmbeddedProductRunGate()
            val counters = Allocations()
            val menu = HomeEmbeddedProofMenu().open()
            assertTrue(menu.controls.contains(control))
            assertTrue(gate.canEnterEmbedded()) // Trạng thái lúc render, không phải authority của click.
            val click = { enterEmbeddedProofRoute(gate, route) { counters.dispatch++ } }
            moveTo(gate, phase)
            val before = gate.status.value
            assertFalse("stale dispatch $phase", click())
            counters.assertZero()
            assertSame(before, gate.status.value)
        }
    }

    @Test fun ownershipAcquiredBetweenNavigationAndDestinationRejectsAllocation() {
        for (phase in nonIdle) {
            val gate = EmbeddedProductRunGate()
            var dispatched = 0
            assertTrue(enterEmbeddedProofRoute(gate, route) { dispatched++ })
            moveTo(gate, phase)
            val counters = Allocations()
            assertFalse(enterEmbeddedProofRoute(gate, route) { counters.constructProof() })
            assertEquals(1, dispatched)
            counters.assertZero()
        }
    }

    @Test fun directRestoredDestinationUsesCurrentGateWithoutMenuOrOldRuntime() {
        val gate = EmbeddedProductRunGate()
        moveTo(gate, ProductRunPhase.CLEANUP_BLOCKED)
        val counters = Allocations()
        repeat(3) { assertFalse(enterEmbeddedProofRoute(gate, route) { counters.constructProof() }) }
        counters.assertZero()
        assertEquals(ProductRunPhase.CLEANUP_BLOCKED, gate.status.value.phase)
    }

    @Test fun blockedProofActionsPreserveEvidenceAndDiagnosticCopyRemainsUsable() {
        val gate = EmbeddedProductRunGate()
        moveTo(gate, ProductRunPhase.CLEANUP_BLOCKED)
        val before = gate.status.value
        val counters = Allocations()
        val menu = HomeEmbeddedProofMenu().open()
        assertTrue(menu.controls.contains(control))
        repeat(2) { assertFalse(enterEmbeddedProofRoute(gate, route) { counters.constructProof() }) }
        val ui = checkNotNull(gate.status.value.cleanupBlockedUi())
        assertEquals(setOf(EmbeddedCleanupBlockedAction.BACK, EmbeddedCleanupBlockedAction.VIEW_STATUS), ui.permittedActions)
        val text = EmbeddedProductDiagnosticFormatter.format(
            EmbeddedProductDiagnostics.from(gate.status.value, Instant.parse("2026-10-02T03:04:05Z")),
        )
        var copied: String? = null
        val copy = EmbeddedProductDiagnosticCopyAction(text) { copied = it }
        assertNull(copied)
        copy.onUserTap()
        assertEquals(text, copied)
        assertTrue(text.contains("phase=CLEANUP_BLOCKED"))
        counters.assertZero()
        assertSame(before, gate.status.value)
        assertFalse(gate.canEnterEmbedded())
        assertFalse(gate.tryDispatchClassic { fail("Classic must stay blocked") })
    }

    @Test fun openingDeveloperSectionAloneAllocatesNothingAndHasOnlyValueState() {
        for (phase in ProductRunPhase.entries) {
            val gate = EmbeddedProductRunGate()
            if (phase != ProductRunPhase.IDLE) moveTo(gate, phase)
            val before = gate.status.value
            val counters = Allocations()
            val menu = HomeEmbeddedProofMenu().open()
            assertEquals(5, menu.controls.size)
            assertTrue(menu.controls.contains(control))
            counters.assertZero()
            assertSame(before, gate.status.value)
            // Menu chỉ có Boolean; danh sách enum không capture callback/runtime.
            assertTrue(menu.javaClass.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
                .all { it.type == Boolean::class.javaPrimitiveType })
        }
    }

    @Test fun classicDispatchInProgressAlsoRejectsDirectProof() {
        val gate = EmbeddedProductRunGate()
        val counters = Allocations()
        assertTrue(gate.tryDispatchClassic {
            assertFalse(enterEmbeddedProofRoute(gate, route) { counters.constructProof() })
        })
        counters.assertZero()
    }

    private fun blockedEntry(phase: ProductRunPhase) {
        val gate = EmbeddedProductRunGate()
        moveTo(gate, phase)
        val before = gate.status.value
        val counters = Allocations()
        assertFalse(enterEmbeddedProofRoute(gate, route) { counters.constructProof() })
        counters.assertZero()
        assertSame(before, gate.status.value)
    }

    private fun moveTo(gate: EmbeddedProductRunGate, phase: ProductRunPhase) {
        val token = checkNotNull(gate.tryAcquireEmbedded("product-workspace"))
        val start = checkNotNull(gate.startOperation(token))
        check(gate.markInvoked(start))
        if (phase != ProductRunPhase.STARTING) {
            check(gate.acceptResult(start, ProductExecutionValue.from(EmbeddedWorkspaceRunResult.Started(emptyList()))))
            if (phase != ProductRunPhase.ACTIVE) {
                val cleanup = checkNotNull(gate.markStopping(token))
                if (phase == ProductRunPhase.CLEANUP_BLOCKED) {
                    check(gate.acceptResult(cleanup, ProductExecutionValue.from(
                        EmbeddedWorkspaceRunResult.CleanupIncomplete(emptyList(), emptyList()))))
                }
            }
        }
        assertEquals(phase, gate.status.value.phase)
    }

    private val nonIdle = listOf(ProductRunPhase.STARTING, ProductRunPhase.ACTIVE, ProductRunPhase.STOPPING,
        ProductRunPhase.CLEANUP_BLOCKED)

    private class Allocations {
        var dispatch = 0
        var controllers = 0
        var executions = 0
        var renderers = 0
        var surfaces = 0
        var sessions = 0
        var starts = 0
        var tokens = 0
        var shizukuConnections = 0
        fun constructProof() {
            controllers++; executions++; renderers++; surfaces++; sessions++; starts++; tokens++; shizukuConnections++
        }
        fun values() = listOf(dispatch, controllers, executions, renderers, surfaces, sessions, starts, tokens, shizukuConnections)
        fun assertZero() = assertEquals(List(9) { 0 }, values())
    }
}
