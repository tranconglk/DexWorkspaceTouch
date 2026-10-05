package com.trancong.dexworkspacetouch.qualification

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.*
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.trancong.dexworkspacetouch.DexWorkspaceTouchApplication
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.product.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class EmbeddedRealProductLifecycleDeviceTest {
    @Test fun realCalculatorRecreationFreshStartAndSurfaceLoss() = qualify("calculator")
    @Test fun realCalculatorWazeRecreationFreshStartAndSurfaceLoss() = qualify("calculator-waze")
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val displayId get() = InstrumentationRegistry.getArguments().getString("displayId", "0").toInt()
    private fun qualify(scenario: String) = withHost(scenario) { old, next ->
        val probe = main { AndroidEmbeddedCapabilityProbe(old.applicationContext) }
        await("Shizuku binder") { probe.snapshot().shizukuBinderAvailable }
        if (!main { probe.snapshot().shizukuPermissionGranted }) {
            main { probe.requestPermission(EMBEDDED_SHIZUKU_PERMISSION_REQUEST_CODE) }
            val allow = setOf("Allow all the time", "Allow", "Lu\u00f4n cho ph\u00e9p", "Cho ph\u00e9p", "Cho ph\u00e9p m\u1ecdi l\u00fac")
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            while (!main { probe.snapshot().shizukuPermissionGranted }) {
                check(System.nanoTime() < deadline) { "Shizuku harness permission required; no remote Start invoked" }
                allow.firstOrNull { clickText(it, "moe.shizuku.privileged.api", optional = true) }
                Thread.sleep(100)
            }
        }
        QualificationPreStartCapture.capture(old, "before-refresh", "Ki\u1ec3m tra l\u1ea1i")
        clickText("Ki\u1ec3m tra l\u1ea1i", captureHost = old)
        clickText("B\u1eaft \u0111\u1ea7u Embedded", captureHost = old)
        awaitActiveOrFail("first Start", old)
        val owned = main { old.gate.status.value }
        val expected = if (scenario == "calculator") setOf("calc") else setOf("calc", "waze")
        main {
            assertEquals(expected, owned.items.map { it.sourceCellId }.toSet())
            assertEquals(expected.size, owned.items.size)
            assertEquals(expected.size, owned.items.map { it.sessionId }.toSet().size)
            assertTrue(owned.items.all { it.displayId > 0 })
            val surfaces = old.observeSurfaces()
            assertEquals(expected.size, surfaces.size)
            assertTrue(surfaces.all { it.holder.surface.isValid })
            old.record("forced_recreate"); old.recreate()
        }
        val fresh = next()
        await("recreation cleanup terminal") { fresh.gate.status.value.phase in setOf(ProductRunPhase.IDLE, ProductRunPhase.CLEANUP_BLOCKED) }
        main {
            assertNotEquals(old.hostId, fresh.hostId); assertEquals(old.pid, fresh.pid)
            assertEquals(displayId, fresh.windowManager.defaultDisplay.displayId)
            val terminal = fresh.gate.status.value
            assertEquals(ProductRunPhase.IDLE, terminal.phase)
            assertEquals(CleanupEvidence.CLEAN_CONFIRMED, terminal.cleanupEvidence)
            assertEquals(owned.generation, terminal.generation)
            assertEquals(owned.startOperationId!! + 1L, terminal.cleanupOperationId)
            assertEquals(owned.items.associate { it.sourceCellId to it.sessionId }, terminal.items.associate { it.sourceCellId to it.sessionId })
            assertEquals(expected, terminal.cleanupOutcomes.map { it.sourceCellId }.toSet())
            assertEquals(expected.size, terminal.cleanupOutcomes.size)
            assertTrue(terminal.cleanupOutcomes.all { it.evidence == CleanupEvidence.CLEAN_CONFIRMED })
            assertEquals(0, old.navigationCallbacks); assertEquals(0, fresh.navigationCallbacks)
            assertTrue(fresh.surfaces().isEmpty())
        }
        clickText("Ki\u1ec3m tra l\u1ea1i")
        main { assertEquals(owned.generation, fresh.gate.status.value.generation); assertTrue(fresh.surfaces().isEmpty()) }
        clickText("B\u1eaft \u0111\u1ea7u Embedded")
        awaitActiveOrFail("explicit fresh Start", fresh)
        val freshOwned = main { fresh.gate.status.value }
        main {
            assertNotSame(owned.token, freshOwned.token)
            assertEquals(owned.generation + 1, fresh.gate.status.value.generation)
            assertEquals(expected, freshOwned.items.map { it.sourceCellId }.toSet())
            assertEquals(expected.size, freshOwned.items.size)
            val surfaces = fresh.observeSurfaces()
            assertEquals(expected.size, surfaces.size)
            assertTrue(surfaces.all { it.holder.surface.isValid })
            val view = surfaces.first()
            view.visibility = View.GONE
        }
        await("real Surface cleanup terminal") { fresh.gate.status.value.phase in setOf(ProductRunPhase.IDLE, ProductRunPhase.CLEANUP_BLOCKED) }
        main { fresh.record("surface_terminal") }
        save(scenario, old, fresh)
        main {
            assertEquals("Authoritative exact clean Surface result must release the run", ProductRunPhase.IDLE, fresh.gate.status.value.phase)
            assertEquals(CleanupEvidence.CLEAN_CONFIRMED, fresh.gate.status.value.cleanupEvidence)
            val terminal = fresh.gate.status.value
            assertEquals(freshOwned.generation, terminal.generation)
            // A Surface terminal observed before an exit intent belongs to the exact Start operation.
            assertEquals(freshOwned.startOperationId, terminal.startOperationId)
            assertEquals(freshOwned.cleanupOperationId, terminal.cleanupOperationId)
            assertEquals(expected.size, terminal.items.size)
            assertEquals(freshOwned.items.associate { it.sourceCellId to it.sessionId }, terminal.items.associate { it.sourceCellId to it.sessionId })
            assertEquals(expected, terminal.cleanupOutcomes.map { it.sourceCellId }.toSet())
            assertEquals(expected.size, terminal.cleanupOutcomes.size)
            assertTrue(terminal.cleanupOutcomes.all { it.evidence == CleanupEvidence.CLEAN_CONFIRMED })
        }
    }
    private fun awaitActiveOrFail(label: String, host: EmbeddedRealProductHarnessActivity) {
        await(label) {
            val phase = host.gate.status.value.phase
            if (phase == ProductRunPhase.CLEANUP_BLOCKED) {
                host.record("FIRST_FAILURE_STOP $label")
                throw AssertionError("$label failed; raw diagnostics saved; no further Start authorized")
            }
            phase == ProductRunPhase.ACTIVE
        }
    }
    private fun <T> main(block: () -> T): T {
        var value: T? = null; var failure: Throwable? = null
        instrumentation.runOnMainSync { try { value = block() } catch (t: Throwable) { failure = t } }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST") return value as T
    }
    private fun await(label: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45)
        while (!main(condition)) { check(System.nanoTime() < deadline) { "$label observation deadline; timeout is not clean evidence" }; Thread.sleep(50) }
    }
    private fun clickText(text: String, packageName: String = VDM010_HARNESS_PACKAGE, optional: Boolean = false, captureHost: EmbeddedRealProductHarnessActivity? = null): Boolean {
        fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.packageName?.toString() == packageName && node.text?.toString() == text) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { find(it)?.let { found -> return found } }
            return null
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(if (optional) 0 else 15)
        do {
            val allDisplays = instrumentation.uiAutomation.windowsOnAllDisplays
            val windows = if (packageName == "moe.shizuku.privileged.api") {
                (0 until allDisplays.size()).flatMap { allDisplays.valueAt(it) }
            } else allDisplays.get(displayId).orEmpty()
            for (window in windows) window.root?.let { find(it) }?.let { node ->
                var clickable: AccessibilityNodeInfo? = node
                while (clickable != null && !clickable.isClickable) clickable = clickable.parent
                if (clickable?.isEnabled == true) {
                    captureHost?.let { QualificationPreStartCapture.capture(it, "before-action", text, dispatchTarget = clickable) }
                    val clicked = clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    captureHost?.let { QualificationPreStartCapture.capture(it, "action-result", text, clicked, clickable) }
                    if (clicked) return true
                    if (packageName == VDM010_HARNESS_PACKAGE) error("Product ACTION_CLICK returned false: $text; STOP without retry")
                }
            }
            if (optional) return false
            Thread.sleep(50)
        } while (System.nanoTime() < deadline)
        captureHost?.let { QualificationPreStartCapture.capture(it, "action-unavailable", text) }
        error("Product action not available: $text; pre-Start snapshot preserved; no Start")
    }
    private fun withHost(scenario: String, test: (EmbeddedRealProductHarnessActivity, () -> EmbeddedRealProductHarnessActivity) -> Unit) {
        val context = instrumentation.targetContext
        assertEquals(VDM010_HARNESS_PACKAGE, context.packageName)
        val app = context.applicationContext as DexWorkspaceTouchApplication
        val independentRunId = requireNotNull(InstrumentationRegistry.getArguments().getString("independentRunId"))
        require(independentRunId.startsWith("vdm010-independent-"))
        assertTrue("Diagnostic overlay must exist BEFORE device Start", QualificationDiagnostics.overlayAvailable)
        val created = LinkedBlockingQueue<EmbeddedRealProductHarnessActivity>(); val hosts = mutableListOf<EmbeddedRealProductHarnessActivity>()
        val info = instrumentation.uiAutomation.serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        instrumentation.uiAutomation.serviceInfo = info
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) { if (activity is EmbeddedRealProductHarnessActivity) { hosts += activity; created.offer(activity) } }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        }
        main {
            val fresh = app.embeddedProductRunGate.status.value
            assertTrue(app.embeddedProductRunGate.canEnterEmbedded())
            assertEquals(ProductRunPhase.IDLE, fresh.phase); assertEquals(0L, fresh.generation)
            assertNull(fresh.token); assertNull(fresh.startOperationId); assertNull(fresh.cleanupOperationId)
            app.registerActivityLifecycleCallbacks(callbacks)
        }
        fun next(): EmbeddedRealProductHarnessActivity = created.poll(25, TimeUnit.SECONDS) ?: error("New real product host not observed")
        var primaryFailure: Throwable? = null
        try {
            instrumentation.startActivitySync(Intent(context, EmbeddedRealProductHarnessActivity::class.java)
                .putExtra("scenario", scenario).putExtra("independentRunId", independentRunId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle())
            test(next(), ::next)
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            try {
                main { hosts.filterNot { it.destroyed }.forEach { it.finish() } }
                instrumentation.waitForIdleSync()
                await("qualification disposal terminal") {
                    hosts.all { it.destroyed } && app.embeddedProductRunGate.status.value.phase in
                        setOf(ProductRunPhase.IDLE, ProductRunPhase.CLEANUP_BLOCKED)
                }
                main { hosts.lastOrNull()?.record("qualification_disposal_terminal") }
            } catch (cleanupFailure: Throwable) {
                main { hosts.lastOrNull()?.record("qualification_disposal_unresolved") }
                if (primaryFailure != null) primaryFailure.addSuppressed(cleanupFailure) else throw cleanupFailure
            } finally {
                save(scenario, *main { hosts.toTypedArray() })
                main { app.unregisterActivityLifecycleCallbacks(callbacks) }
            }
        }
    }
    private fun save(scenario: String, vararg hosts: EmbeddedRealProductHarnessActivity) {
        val data = main { hosts.flatMap { it.events } }
        val directory = instrumentation.targetContext.getExternalFilesDir("dwt-vdm-010-real")!!
            .resolve(requireNotNull(InstrumentationRegistry.getArguments().getString("independentRunId")))
        directory.mkdirs()
        directory.resolve("$scenario.txt").writeText(data.joinToString("\n"))
        directory.resolve("$scenario-diagnostics.jsonl").writeText(QualificationDiagnostics.snapshotRows().joinToString("\n"))
        directory.resolve("$scenario-independent-run.txt").writeText(
            "NEW_INDEPENDENT_RUN=" + InstrumentationRegistry.getArguments().getString("independentRunId") +
                "\nHISTORICAL_SESSION=" + QualificationDiagnostics.HISTORICAL_SID +
                "\nHISTORICAL_AUTHORITATIVE_RELEASE=NOT_VERIFIED\n")
    }
}
