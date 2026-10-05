package com.trancong.dexworkspacetouch.qualification

import android.app.Activity
import android.app.ActivityOptions
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import com.trancong.dexworkspacetouch.DexWorkspaceTouchApplication
import com.trancong.dexworkspacetouch.feature.embeddedapp.*
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.product.*
import com.trancong.dexworkspacetouch.workspace.execution.embedded.layout.EmbeddedWorkspaceViewport
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Category B: actual Android Activity/Surface, controlled synthetic session results. No skips. */
class EmbeddedHostLifecycleDeviceTest {
    @Test fun recreateJoinsCleanupOldCallbackCannotPopNewHostAndFreshStartIsExplicit() = withHost { old, next ->
        val fixture = HarnessFixture()
        val owned = main { old.explicitStart(fixture)!! }
        await { old.gate.status.value.phase == ProductRunPhase.ACTIVE }
        val original = main { old.gate.status.value }
        main {
            assertTrue(old.observeSurface().holder.surface.isValid)
            old.requestBackFromUserAction()
            old.recreate()
        }
        val fresh = next()
        await { old.destroyed && fixture.cleanupWaiting.isCompleted }
        main {
            assertNotEquals(old.hostId, fresh.hostId)
            assertEquals(old.pid, fresh.pid)
            assertEquals(ProductRunPhase.STOPPING, fresh.gate.status.value.phase)
            assertEquals(0, fresh.allocations)
            assertNull(fresh.route)
            assertFalse(fresh.hasSurface())
            assertNull(fresh.explicitStart(HarnessFixture()))
            assertEquals(1, fixture.closeCalls)
            assertSame(original.token, fresh.gate.status.value.token)
            assertEquals(original.generation, fresh.gate.status.value.generation)
        }
        val cleanupId = main { fresh.gate.status.value.cleanupOperationId!! }
        main { fixture.cleanupPermit.complete(Unit) }
        await { fresh.gate.status.value.phase == ProductRunPhase.IDLE }
        main {
            assertEquals(0, old.navigationCallbacks)
            assertEquals(0, fresh.navigationCallbacks)
            assertEquals(0, fresh.allocations)
            assertNull(fresh.route)
            assertFalse(fresh.hasSurface())
            assertEquals(cleanupId, fresh.gate.status.value.cleanupOperationId)
            assertEquals(CleanupEvidence.CLEAN_CONFIRMED, fresh.gate.status.value.cleanupEvidence)
            assertEquals(listOf("calc"), fresh.gate.status.value.items.map { it.sourceCellId })
            assertEquals(listOf("calc"), fresh.gate.status.value.cleanupOutcomes.map { it.sourceCellId })
            owned.sessions.lateCallback!!(EmbeddedSessionSnapshot(EmbeddedSessionPhase.ACTIVE, 9999))
        }
        val secondFixture = HarnessFixture()
        val second = main { fresh.explicitStart(secondFixture)!! }
        await { fresh.gate.status.value.phase == ProductRunPhase.ACTIVE }
        main {
            assertNotSame(original.token, fresh.gate.status.value.token)
            assertTrue(fresh.gate.status.value.generation > original.generation)
            assertEquals(1, secondFixture.startCalls)
        }
        val newer = main { fresh.gate.status.value }
        val newerSurface = main { fresh.observeSurface().holder.surface }
        runBlocking { assertFalse(owned.product.observeResult(owned.product.startOperation!!,
            EmbeddedWorkspaceRunResult.Stopped(emptyList(), emptyList()))) }
        main {
            runBlocking { second.renderer.onSurfaceDestroyed(owned.renderer.generationToken, "calc", Any(), Any()) }
            owned.sessions.lateCallback!!(EmbeddedSessionSnapshot(EmbeddedSessionPhase.STOPPED))
            assertEquals(newer, fresh.gate.status.value)
            assertTrue(newerSurface.isValid)
            secondFixture.cleanupPermit.complete(Unit)
            fresh.requestBackFromUserAction()
        }
        await { fresh.gate.status.value.phase == ProductRunPhase.IDLE }
        save("recreation-clean", old, fresh)
    }

    @Test fun syntheticIncompleteSurvivesRecreationNavigationAndReadinessRefresh() = blocked(HarnessCleanupMode.INCOMPLETE)
    @Test fun syntheticUncertainSurvivesRecreationNavigationAndReadinessRefresh() = blocked(HarnessCleanupMode.UNCERTAIN)

    private fun blocked(mode: HarnessCleanupMode) = withHost { old, next ->
        val fixture = HarnessFixture(mode)
        val owned = main { old.explicitStart(fixture)!! }
        await { old.gate.status.value.phase == ProductRunPhase.ACTIVE }
        main { old.observeSurface(); old.recreate() }
        val fresh = next()
        await { fixture.cleanupWaiting.isCompleted }
        main { fixture.cleanupPermit.complete(Unit) }
        await { fresh.gate.status.value.phase == ProductRunPhase.CLEANUP_BLOCKED }
        val blocked = main { fresh.gate.status.value }
        main {
            assertNotEquals(old.hostId, fresh.hostId)
            assertEquals(old.pid, fresh.pid)
            assertEquals(0, fresh.allocations)
            assertNull(fresh.route)
            assertFalse(fresh.hasSurface())
            assertNotNull(blocked.cleanupBlockedUi())
            if (mode == HarnessCleanupMode.INCOMPLETE) {
                assertEquals(ProductResultKind.INCOMPLETE, blocked.resultKind)
                assertEquals(EmbeddedCleanupBlockedCause.INCOMPLETE, blocked.cleanupBlockedUi()!!.cause)
                assertEquals(listOf("CONTROLLED_010"), blocked.cleanupOutcomes.map { it.failureCode })
            } else {
                assertEquals(ProductResultKind.UNCERTAIN, blocked.resultKind)
                assertEquals(CleanupEvidence.UNCERTAIN, blocked.cleanupEvidence)
            }
            val policy = EmbeddedProductRecoveryMapper.snapshot(blocked.phase, fresh.gate.canEnterEmbedded(),
                blocked.issue, blocked.allocationEvidence, blocked.cleanupEvidence)
            assertEquals(policy, EmbeddedWorkspaceReadiness.withRecovery(EmbeddedReadinessResult.Ready, policy))
            assertFalse(EmbeddedRecoveryAction.OPEN_CLASSIC in policy.permittedActions)
            assertFalse(EmbeddedRecoveryAction.START_EMBEDDED in policy.permittedActions)
            assertFalse(fresh.gate.tryDispatchClassic { fail("Classic unlocked") })
            assertNull(fresh.explicitStart(HarnessFixture()))
            owned.sessions.lateCallback!!(EmbeddedSessionSnapshot(EmbeddedSessionPhase.STOPPED))
            fresh.recreate()
        }
        val navigated = next()
        main {
            assertEquals(old.pid, navigated.pid)
            assertEquals(blocked, navigated.gate.status.value)
            assertNull(navigated.explicitStart(HarnessFixture()))
            assertEquals(0, navigated.allocations)
            assertFalse(navigated.hasSurface())
        }
        save("blocked-$mode", old, fresh, navigated)
    }

    @Test fun actualSurfaceLossAndViewportChangesDoNotResurrectRun() = withHost { host, _ ->
        val fixture = HarnessFixture()
        val owned = main { host.explicitStart(fixture)!! }
        await { host.gate.status.value.phase == ProductRunPhase.ACTIVE }
        val surfaceView = main { host.observeSurface() }
        main {
            runBlocking { owned.renderer.onViewportChanged(EmbeddedWorkspaceViewport(0, 0)) }
            assertFalse(owned.renderer.state.value.touchEnabled)
            assertEquals(0, fixture.sessionStops)
            runBlocking { owned.renderer.onViewportChanged(EmbeddedWorkspaceViewport(1200, 900)) }
            assertTrue(owned.renderer.state.value.touchEnabled)
            surfaceView.visibility = View.GONE
        }
        await { host.events.any { "event=surface_destroyed" in it } }
        await { owned.renderer.controller.state.value.latestResult is EmbeddedWorkspaceRunResult.StartFailed }
        main {
            val terminal = owned.renderer.controller.state.value.latestResult as EmbeddedWorkspaceRunResult.StartFailed
            assertEquals("SURFACE_LOST", terminal.failure.code)
            assertTrue(terminal.allOwnedSessionsClean)
            host.record("surface_terminal receipts=${terminal.receipts} partial=${terminal.partialReceipt} outcomes=${terminal.rollbackOutcomes}")
        }
        main { surfaceView.visibility = View.VISIBLE }
        await { host.events.any { "event=surface_created" in it } }
        main {
            assertTrue(surfaceView.holder.surface.isValid)
            assertFalse(owned.renderer.state.value.canStart)
            assertEquals(1, fixture.startCalls)
            assertEquals(1, fixture.sessionStops)
            assertEquals(1, fixture.sessionCloses)
            fixture.cleanupPermit.complete(Unit)
            host.requestBackFromUserAction()
        }
        await { fixture.cleanupWaiting.isCompleted }
        instrumentation.waitForIdleSync()
        main {
            assertEquals(ProductRunPhase.IDLE, host.gate.status.value.phase)
            assertEquals(1, fixture.startCalls)
            assertEquals(1, fixture.closeCalls)
            assertEquals(listOf("calc"), host.gate.status.value.cleanupOutcomes.map { it.sourceCellId })
        }
        save("surface-loss", host)
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun <T> main(block: () -> T): T {
        var value: T? = null
        var failure: Throwable? = null
        instrumentation.runOnMainSync { try { value = block() } catch (t: Throwable) { failure = t } }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST") return value as T
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25)
        while (!main(condition)) {
            check(System.nanoTime() < deadline) { "Harness observation deadline; never cleanup evidence" }
            Thread.sleep(25)
        }
    }
    private fun withHost(test: (EmbeddedLifecycleHarnessActivity, () -> EmbeddedLifecycleHarnessActivity) -> Unit) {
        val context = instrumentation.targetContext
        assertEquals("Do not instrument production package", VDM010_HARNESS_PACKAGE, context.packageName)
        val application = context.applicationContext as DexWorkspaceTouchApplication
        // Independent test gate in isolated harness process only; no recovery/unlock of a live run.
        val created = LinkedBlockingQueue<EmbeddedLifecycleHarnessActivity>()
        val hosts = mutableListOf<EmbeddedLifecycleHarnessActivity>()
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) {
                if (activity is EmbeddedLifecycleHarnessActivity) { hosts += activity; created.offer(activity) }
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        }
        // CLEANUP_BLOCKED is intentionally permanent. Run each blocked case in a fresh
        // instrumentation invocation; never reset Application gate to make a test pass.
        assertTrue("Fresh isolated harness invocation required", main { application.embeddedProductRunGate.canEnterEmbedded() })
        main { application.registerActivityLifecycleCallbacks(callbacks) }
        fun next(): EmbeddedLifecycleHarnessActivity = created.poll(25, TimeUnit.SECONDS)
            ?: error("Activity recreation not observed")
        try {
            val displayId = InstrumentationRegistry.getArguments().getString("displayId", "0").toInt()
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
            instrumentation.startActivitySync(Intent(context, EmbeddedLifecycleHarnessActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options.toBundle())
            val first = next()
            main { assertEquals(displayId, first.windowManager.defaultDisplay.displayId) }
            test(first, ::next)
        } finally {
            main {
                hosts.forEach { it.route?.fixture?.cleanupPermit?.complete(Unit) }
                hosts.filterNot { it.destroyed }.forEach { it.finish() }
                application.unregisterActivityLifecycleCallbacks(callbacks)
            }
            instrumentation.waitForIdleSync()
        }
    }
    private fun save(name: String, vararg hosts: EmbeddedLifecycleHarnessActivity) {
        val context = instrumentation.targetContext
        val data = main {
            hosts.flatMap { it.events } + hosts.last().gate.status.value.let { status ->
                "category=B result=$name phase=${status.phase} generation=${status.generation} cleanup=${status.cleanupOperationId} owned=${status.items.map { it.sourceCellId }} outcomes=${status.cleanupOutcomes}"
            }
        }
        context.getExternalFilesDir("dwt-vdm-010")!!.resolve("$name.txt").writeText(data.joinToString("\n"))
    }
}
