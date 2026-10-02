package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.*
import com.trancong.dexworkspacetouch.feature.embeddedapp.*
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlan
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlanItem
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmbeddedProductLifecycleTest {
    @Test fun disposedIdleHostClosesLocalExecutionAndDropsReadinessWithoutToken() = runTest {
        val gate = EmbeddedProductRunGate()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        var closes = 0
        val controller = EmbeddedWorkspaceProductController("ws", gate,
            EmbeddedCapabilityProbe { EmbeddedCapabilitySnapshot(true, true, true) },
            { ProductHostReadiness(true, true) }, object : EmbeddedProductExecution {
                override suspend fun start() = EmbeddedWorkspaceRunResult.Started(emptyList())
                override suspend fun close(): EmbeddedWorkspaceRunResult { closes++; return clean() }
            }, scope)
        controller.onHostDisposed().join()
        assertTrue(scope.coroutineContext[Job]!!.isCompleted)
        assertEquals(1, closes)
        assertNull("Disposed host must drop its execution even before Start", field(controller, "execution"))
        assertNull(field(controller, "hostReadiness"))
        assertEquals(ProductRunPhase.IDLE, gate.status.value.phase)
    }

    @Test fun recreateWhileStartingDoesNotReplaceExecution() = runTest { recreation(ProductRunPhase.STARTING) }
    @Test fun recreateWhileActiveDoesNotReplaceExecution() = runTest { recreation(ProductRunPhase.ACTIVE) }
    @Test fun recreateWhileStoppingDoesNotReplaceExecution() = runTest { recreation(ProductRunPhase.STOPPING) }

    private suspend fun TestScope.recreation(phase: ProductRunPhase) {
        val f = Fixture(this)
        try {
            val old = f.host()
            val runtime = old.start(pending = phase == ProductRunPhase.STARTING)!!
            runCurrent()
            if (phase == ProductRunPhase.STOPPING) runtime.product.requestBack { old.pops++ }
            runCurrent()
            assertEquals(phase, f.gate.status.value.phase)
            val new = f.host()
            assertNull(new.start())
            new.assertNoRuntime()
            val dispose = runtime.product.onHostDisposed()
            assertSame(dispose, runtime.product.onHostDisposed())
            runCurrent()
            assertEquals(1, runtime.closes)
            val cleanup = f.gate.status.value.cleanupOperationId
            assertNotNull(cleanup)
            runtime.product.onHostDisposed()
            assertEquals(cleanup, f.gate.status.value.cleanupOperationId)
            runtime.cleaned.complete(incomplete())
            dispose.join()
            runCurrent()
            assertEquals(ProductRunPhase.CLEANUP_BLOCKED, new.status.phase)
            assertEquals(1, f.tokens)
            assertEquals(0, old.pops)
            assertEquals(0, new.pops)
            runtime.assertDetached()
            new.assertNoRuntime()
        } finally { f.close() }
    }

    @Test fun recreateAfterBlockedSurvivesDroppingOldRuntime() = runTest {
        val f = Fixture(this)
        try {
            suspend fun oldRoute() {
                val old = f.host()
                val runtime = old.start()!!
                runCurrent()
                val disposed = runtime.product.onHostDisposed()
                runtime.cleaned.complete(incomplete())
                disposed.join()
                runtime.assertDetached()
            }
            oldRoute()
            val new = f.host()
            assertEquals(ProductRunPhase.CLEANUP_BLOCKED, new.status.phase)
            assertEquals("RuntimeCleanupIncomplete", new.status.issue?.code)
            assertValueGraph(new.status)
            assertNull(new.start())
            new.assertNoRuntime()
            assertEquals(1, f.tokens)
            assertFalse(f.gate.tryDispatchClassic { error("Classic must stay blocked") })
        } finally { f.close() }
    }

    @Test fun cleanTerminalAllowsOneFreshRunOnlyAfterUserStart() = runTest {
        val f = Fixture(this)
        try {
            val old = f.host()
            val runtime = old.start()!!
            runCurrent()
            val disposed = runtime.product.onHostDisposed()
            val new = f.host()
            runtime.cleaned.complete(clean())
            disposed.join()
            runCurrent()
            assertEquals(ProductRunPhase.IDLE, new.status.phase)
            runtime.assertDetached()
            new.assertNoRuntime()
            assertEquals(1, f.tokens)
            val fresh = new.start()!!
            assertSame(fresh, new.start())
            runCurrent()
            assertEquals(1, new.controllers)
            assertEquals(1, new.executions)
            assertEquals(1, new.surfaces)
            assertEquals(1, fresh.starts)
            assertEquals(2, f.tokens)
            assertNotSame(runtime.product.startOperation!!.token, fresh.product.startOperation!!.token)
            assertEquals(0, old.pops)
        } finally { f.close() }
    }

    @Test fun pendingStartBackAndRecreationJoinOneCleanupWithoutOldNavigation() = runTest {
        val f = Fixture(this)
        try {
            val old = f.host()
            val runtime = old.start(pending = true)!!
            runCurrent()
            runtime.product.requestBack { old.pops++ }
            runCurrent()
            val cleanup = f.gate.status.value.cleanupOperationId
            val dispose = runtime.product.onHostDisposed()
            val new = f.host()
            assertNull(new.start())
            runtime.started.complete(EmbeddedWorkspaceRunResult.Started(emptyList()))
            runCurrent()
            assertEquals(ProductRunPhase.STOPPING, new.status.phase)
            assertEquals(cleanup, new.status.cleanupOperationId)
            runtime.cleaned.complete(clean())
            dispose.join()
            runCurrent()
            assertEquals(1, runtime.starts)
            assertEquals(1, runtime.closes)
            assertEquals(0, old.pops)
            assertEquals(0, new.pops)
            runtime.assertDetached()
            new.assertNoRuntime()
        } finally { f.close() }
    }

    @Test fun doubleBackPopsOnceAfterCleanWhileHostIsAlive() = runTest {
        val f = Fixture(this)
        try {
            val host = f.host()
            val runtime = host.start()!!
            runCurrent()
            val first = runtime.product.requestBack { host.pops++ }
            val second = runtime.product.requestBack { host.pops++ }
            assertSame(first, second)
            assertEquals(0, host.pops)
            runtime.cleaned.complete(clean())
            first.join(); second.join()
            assertEquals(1, host.pops)
            assertEquals(1, runtime.closes)
        } finally { f.close() }
    }

    @Test fun doubleBackAndRecreationDoNotPopNewHost() = runTest {
        val f = Fixture(this)
        try {
            val old = f.host()
            val runtime = old.start()!!
            runCurrent()
            runtime.product.requestBack { old.pops++ }
            runtime.product.requestBack { old.pops++ }
            val dispose = runtime.product.onHostDisposed()
            val new = f.host()
            assertNull(new.start())
            runtime.cleaned.complete(clean())
            dispose.join(); runCurrent()
            assertEquals(1, runtime.closes)
            assertEquals(0, old.pops)
            assertEquals(0, new.pops)
            new.assertNoRuntime()
        } finally { f.close() }
    }

    @Test fun disposeBeforeCleanupTerminalKeepsObserverValueOnly() = runTest { recreation(ProductRunPhase.ACTIVE) }

    @Test fun oldTerminalCleanAfterRecreationUpdatesOnlyExactGate() = runTest { oldTerminal(clean(), ProductRunPhase.IDLE) }
    @Test fun oldTerminalIncompleteAfterRecreationUpdatesOnlyExactGate() = runTest { oldTerminal(incomplete(), ProductRunPhase.CLEANUP_BLOCKED) }
    @Test fun oldTerminalUncertainAfterRecreationKeepsGateBlocked() = runTest { oldTerminal(null, ProductRunPhase.CLEANUP_BLOCKED) }

    private suspend fun TestScope.oldTerminal(result: EmbeddedWorkspaceRunResult?, phase: ProductRunPhase) {
        val f = Fixture(this)
        try {
            val old = f.host()
            val runtime = old.start()!!
            runCurrent()
            runtime.product.requestBack { old.pops++ }
            val disposed = runtime.product.onHostDisposed()
            val new = f.host()
            runtime.callbacks++
            runtime.cleaned.complete(result)
            disposed.join(); runCurrent()
            assertEquals(phase, new.status.phase)
            assertEquals(1, runtime.callbacks)
            assertTrue(new.values > 0)
            assertEquals(0, old.pops)
            assertEquals(0, new.pops)
            runtime.assertDetached()
            new.assertNoRuntime()
        } finally { f.close() }
    }

    @Test fun oldLateStartedAfterNewHostCannotActivateOrRelease() = runTest {
        val f = Fixture(this)
        try {
            val old = f.host()
            val runtime = old.start(pending = true)!!
            runCurrent()
            val operation = runtime.product.startOperation!!
            val disposed = runtime.product.onHostDisposed()
            val new = f.host()
            val stopping = new.status
            runtime.callbacks++
            assertFalse(runtime.product.observeResult(operation, EmbeddedWorkspaceRunResult.Started(emptyList())))
            assertEquals(stopping, new.status)
            runtime.cleaned.complete(incomplete())
            disposed.join(); runCurrent()
            val blocked = new.status
            runtime.started.complete(EmbeddedWorkspaceRunResult.Started(emptyList()))
            assertFalse(runtime.product.observeResult(operation, clean()))
            runCurrent()
            assertEquals(blocked, new.status)
            assertEquals(0, old.pops)
            new.assertNoRuntime()
        } finally { f.close() }
    }

    @Test fun staleCleanupCallbackCannotReleaseNewerRun() = runTest {
        val f = Fixture(this)
        try {
            val runtime = f.host().start()!!
            runCurrent()
            val disposed = runtime.product.onHostDisposed()
            val cleanup = f.gate.markStopping(runtime.product.startOperation!!)!!
            val new = f.host()
            runtime.cleaned.complete(clean())
            disposed.join(); runCurrent()
            new.start()!!
            runCurrent()
            val active = new.status
            runtime.callbacks++
            assertFalse(f.gate.acceptResult(cleanup, ProductExecutionValue.from(clean())))
            assertFalse(f.gate.acceptResult(cleanup.copy(generation = active.generation), ProductExecutionValue.from(incomplete())))
            assertEquals(active, new.status)
            assertEquals(2, f.tokens)
            assertEquals(0, new.pops)
        } finally { f.close() }
    }

    @Test fun everyNonIdlePhaseRejectsFactoryBeforeAnyAllocationOrToken() = runTest {
        for (phase in listOf(ProductRunPhase.STARTING, ProductRunPhase.ACTIVE, ProductRunPhase.STOPPING, ProductRunPhase.CLEANUP_BLOCKED)) {
            val f = Fixture(this)
            try {
                val runtime = f.host().start(pending = phase == ProductRunPhase.STARTING)!!
                runCurrent()
                if (phase == ProductRunPhase.STOPPING || phase == ProductRunPhase.CLEANUP_BLOCKED) runtime.product.requestBack { }
                if (phase == ProductRunPhase.CLEANUP_BLOCKED) { runtime.cleaned.complete(incomplete()); runCurrent() }
                assertEquals(phase, f.gate.status.value.phase)
                val before = f.gate.status.value
                val new = f.host()
                assertNull("Factory must not run during $phase", new.start())
                new.assertNoRuntime()
                assertEquals(before, f.gate.status.value)
                assertEquals(1, f.tokens)
                assertFalse(f.gate.tryDispatchClassic { error("conflicting Classic") })
            } finally { f.close() }
        }
    }

    @Test fun readinessAndOldShizukuCallbacksCannotChangeOwnershipAfterRecreation() = runTest {
        val f = Fixture(this)
        try {
            val runtime = f.host().start()!!
            runCurrent()
            val oldCallbacks = Callbacks()
            var oldReads = 0
            val oldRefresh = EmbeddedShizukuRefresh(EmbeddedCapabilityProbe { oldReads++; EmbeddedCapabilitySnapshot(true, false, false) },
                oldCallbacks, { ProductHostReadiness(true, true) }, { runtime.product.currentRecovery() })
            oldRefresh.attach()
            val stale = oldCallbacks.received!!
            oldRefresh.dispose()
            val readsAtDispose = oldReads
            val disposed = runtime.product.onHostDisposed()
            val new = f.host()
            val before = new.status
            stale()
            assertEquals(readsAtDispose, oldReads)
            val newCallbacks = Callbacks()
            var ready = false
            val newRefresh = EmbeddedShizukuRefresh(EmbeddedCapabilityProbe { EmbeddedCapabilitySnapshot(true, ready, ready) },
                newCallbacks, { ProductHostReadiness(false, false) }, { new.recovery() })
            newRefresh.attach()
            ready = true
            newCallbacks.received!!()
            assertTrue(newRefresh.state.value!!.capability.shizukuPermissionGranted)
            assertEquals(before, new.status)
            assertFalse(EmbeddedRecoveryAction.OPEN_CLASSIC in new.recovery().permittedActions)
            new.assertNoRuntime()
            newRefresh.dispose()
            runtime.cleaned.complete(incomplete())
            disposed.join()
        } finally { f.close() }
    }

    @Test fun hungStartDisposeRecreatesBlockedUiAfterBoundedLocalTerminalWithoutHandle() = runTest {
        val f = Fixture(this)
        var consumer: ((EmbeddedSessionSnapshot) -> Unit)? = null
        var remoteStarts = 0
        var remoteCloses = 0
        var detached = 0
        val runner = EmbeddedWorkspaceRunner(EmbeddedWorkspacePreflight { EmbeddedAppGeometry(900, 675, 320) },
            object : EmbeddedWorkspaceSessionFactory {
                override fun create(target: EmbeddedAppTarget, observer: (EmbeddedSessionSnapshot) -> Unit): EmbeddedWorkspaceSessionHandle {
                    consumer = observer
                    return object : EmbeddedWorkspaceSessionHandle {
                        override val sessionId = EmbeddedAppSessionId("hung")
                        override fun connect() { consumer?.invoke(EmbeddedSessionSnapshot(EmbeddedSessionPhase.READY)) }
                        override fun start(surface: EmbeddedWorkspaceExecutionSurface) { remoteStarts++ }
                        override fun sendTouch(event: EmbeddedTouchEvent) = false
                        override fun stop() = Unit
                        override fun close() { remoteCloses++ }
                        override fun detachNotifications() { detached++; consumer = null }
                    }
                }
            }, PROOF_EMBEDDED_WORKSPACE_TIMEOUTS, this, StandardTestDispatcher(testScheduler))
        val root = (field(runner, "runnerScope") as CoroutineScope).coroutineContext[Job]!!
        val surface = object : EmbeddedWorkspaceExecutionSurface { override val isValid = true }
        val request = EmbeddedWorkspaceExecutionRequest(EmbeddedWorkspacePlan("ws", "Workspace", listOf(
            EmbeddedWorkspacePlanItem("cell", "pkg", "pkg.Main", NormalizedBounds.FullCanvas, 0))),
            listOf(EmbeddedWorkspaceHostSlot("cell", surface)))
        try {
            val old = f.host()
            val runtime = old.start(backing = object : EmbeddedProductExecution {
                override suspend fun start() = runner.start(request)
                override suspend fun close() = runner.stop()
            })!!
            runCurrent()
            val late = consumer!!
            runtime.product.requestBack { old.pops++ }
            val disposed = runtime.product.onHostDisposed()
            val new = f.host()
            assertNull(new.start())
            advanceTimeBy(20_000); runCurrent()
            disposed.join()
            assertTrue(root.isCompleted && root.children.none())
            assertNull(field(runner, "runnerScope"))
            assertNull(field(runner, "sessionFactory"))
            assertNull(consumer)
            assertEquals(1, detached)
            assertEquals(1, remoteStarts)
            assertEquals(1, remoteCloses)
            assertEquals(1, runtime.starts)
            assertEquals(1, runtime.closes)
            runtime.assertDetached()
            val blocked = new.status
            assertEquals(ProductRunPhase.CLEANUP_BLOCKED, blocked.phase)
            assertEquals("CLEANUP_TIMEOUT", blocked.cleanupOutcomes.single().failureCode)
            val cached = runner.stop()
            late(EmbeddedSessionSnapshot(EmbeddedSessionPhase.ACTIVE, 42))
            assertFalse(runtime.product.observeResult(runtime.product.startOperation!!, EmbeddedWorkspaceRunResult.Started(emptyList())))
            runCurrent()
            assertSame(cached, runner.stop())
            assertEquals(blocked, new.status)
            assertValueGraph(blocked)
            assertNull(new.start())
            new.assertNoRuntime()
            assertEquals(1, f.tokens)
            assertEquals(0, old.pops)
            assertFalse(EmbeddedRecoveryAction.OPEN_CLASSIC in new.recovery().permittedActions)
            assertFalse(EmbeddedRecoveryAction.START_EMBEDDED in new.recovery().permittedActions)
        } finally { f.close() }
    }

    @Test fun backAfterDisposeCannotRetainOrInvokeOldNavigation() = runTest {
        val f = Fixture(this)
        try {
            val old = f.host()
            val runtime = old.start()!!
            runCurrent()
            val disposed = runtime.product.onHostDisposed()
            runtime.product.requestBack { old.pops++ }
            runtime.cleaned.complete(clean())
            disposed.join(); runCurrent()
            assertEquals(0, old.pops)
            runtime.assertDetached()
            assertEquals(1, runtime.closes)
        } finally { f.close() }
    }

    @Test fun preRunnerRejectionAlsoClosesPreparedRunnerWithoutAnotherGateOperation() = runTest { preparedRunnerExit(rejectAtInvocation = true) }
    @Test fun backBeforeStartJoinsDisposeAndTerminatesPreparedRunnerWithoutToken() = runTest { preparedRunnerExit(rejectAtInvocation = false) }

    private suspend fun TestScope.preparedRunnerExit(rejectAtInvocation: Boolean) {
        val gate = EmbeddedProductRunGate()
        val idle = gate.status.value
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val runner = EmbeddedWorkspaceRunner(EmbeddedWorkspacePreflight { EmbeddedAppGeometry(900, 675, 320) },
            object : EmbeddedWorkspaceSessionFactory {
                override fun create(target: EmbeddedAppTarget, observer: (EmbeddedSessionSnapshot) -> Unit): EmbeddedWorkspaceSessionHandle =
                    error("No session may be allocated before Start")
            }, PROOF_EMBEDDED_WORKSPACE_TIMEOUTS, this, StandardTestDispatcher(testScheduler))
        val root = (field(runner, "runnerScope") as CoroutineScope).coroutineContext[Job]!!
        var closes = 0
        var pops = 0
        var checks = 0
        val controller = EmbeddedWorkspaceProductController("ws", gate,
            EmbeddedCapabilityProbe { EmbeddedCapabilitySnapshot(true, true, true) },
            { checks++; ProductHostReadiness(rejectAtInvocation && checks == 1, true) }, object : EmbeddedProductExecution {
                override suspend fun start(): EmbeddedWorkspaceRunResult = error("must not Start")
                override suspend fun close(): EmbeddedWorkspaceRunResult { closes++; return runner.stop() }
            }, scope)
        try {
            if (rejectAtInvocation) assertEquals(ProductStartOutcome.RendererChanged, controller.start())
            controller.requestBack { pops++ }
            val disposed = controller.onHostDisposed()
            runCurrent(); disposed.join()
            assertEquals(1, closes)
            assertTrue(root.isCompleted && root.children.none())
            assertNull(field(runner, "runnerScope"))
            if (rejectAtInvocation) {
                assertEquals(ProductInvocationCategory.PRE_RUNNER_REJECTED, gate.status.value.invocationCategory)
                assertEquals(ProductRunPhase.IDLE, gate.status.value.phase)
                assertNull(gate.status.value.token)
                assertNull(gate.status.value.cleanupOperationId)
            } else assertEquals(idle, gate.status.value)
            assertEquals(0, pops)
            if (!rejectAtInvocation) assertNull(controller.startOperation)
            assertNull(field(controller, "execution"))
        } finally { runner.stop(); scope.cancel() }
    }

    @Test fun classicDispatchCannotConstructEmbeddedHostEvenAtIdle() {
        val gate = EmbeddedProductRunGate()
        var allocations = 0
        assertTrue(gate.tryDispatchClassic {
            assertNull(gate.createExecutionIfIdle { allocations++; Any() })
        })
        assertEquals(0, allocations)
        assertNotNull(gate.createExecutionIfIdle { allocations++; Any() })
        assertEquals(1, allocations)
    }

    private class Fixture(val test: TestScope, val gate: EmbeddedProductRunGate = EmbeddedProductRunGate()) {
        private val scopes = mutableListOf<CoroutineScope>()
        var tokens = 0
        private var generation = 0L
        init {
            test.backgroundScope.launch(UnconfinedTestDispatcher(test.testScheduler)) {
                gate.status.collect {
                    if (it.token != null && it.generation != generation) { generation = it.generation; tokens++ }
                }
            }
        }
        fun host() = Host(this)
        fun scope() = CoroutineScope(SupervisorJob() + StandardTestDispatcher(test.testScheduler)).also { scopes += it }
        fun close() = scopes.forEach { it.cancel() }
    }

    // Host mới chỉ nhận status giá trị; không nhận handle runtime của host cũ.
    private class Host(private val fixture: Fixture) {
        private val gate = fixture.gate
        private var runtime: Runtime? = null
        var controllers = 0
        var executions = 0
        var surfaces = 0
        var pops = 0
        var values = 0
        val status: ProductRunStatus get() = gate.status.value
        init { fixture.test.backgroundScope.launch(UnconfinedTestDispatcher(fixture.test.testScheduler)) { gate.status.collect { values++ } } }
        fun recovery() = EmbeddedProductRecoveryMapper.snapshot(status.phase, gate.canEnterEmbedded(),
            status.issue, status.allocationEvidence, status.cleanupEvidence)
        fun start(pending: Boolean = false, backing: EmbeddedProductExecution? = null): Runtime? {
            runtime?.let { return it }
            return gate.createExecutionIfIdle {
                executions++
                surfaces++
                val scope = fixture.scope()
                val execution = Runtime(scope, pending, backing)
                controllers++
                execution.product = EmbeddedWorkspaceProductController("ws", gate,
                    EmbeddedCapabilityProbe { EmbeddedCapabilitySnapshot(true, true, true) },
                    { ProductHostReadiness(true, true) }, execution, scope)
                execution.also { scope.async { it.product.start() }; runtime = it }
            }
        }
        fun assertNoRuntime() {
            assertEquals("controller constructions", 0, controllers)
            assertEquals("execution constructions", 0, executions)
            assertEquals("Surface/host allocations", 0, surfaces)
            assertNull(runtime)
        }
    }

    private class Runtime(val scope: CoroutineScope, private val pending: Boolean,
        private val backing: EmbeddedProductExecution?) : EmbeddedProductExecution {
        lateinit var product: EmbeddedWorkspaceProductController
        val started = CompletableDeferred<EmbeddedWorkspaceRunResult?>()
        val cleaned = CompletableDeferred<EmbeddedWorkspaceRunResult?>()
        var starts = 0
        var closes = 0
        var callbacks = 0
        override suspend fun start(): EmbeddedWorkspaceRunResult? {
            starts++
            return if (backing != null) backing.start() else if (pending) started.await() else EmbeddedWorkspaceRunResult.Started(emptyList())
        }
        override suspend fun close(): EmbeddedWorkspaceRunResult? { closes++; return if (backing != null) backing.close() else cleaned.await() }
        fun assertDetached() {
            assertTrue(scope.coroutineContext[Job]!!.isCompleted)
            assertTrue(scope.coroutineContext[Job]!!.children.none())
            assertNull(fieldValue(product, "execution"))
            assertNull(fieldValue(product, "hostReadiness"))
            assertNull(fieldValue(product, "backCallback"))
        }
    }

    private class Callbacks : EmbeddedShizukuCallbacks {
        var received: (() -> Unit)? = null
        override fun listenBinderReceived(listener: () -> Unit): AutoCloseable { received = listener; return AutoCloseable { received = null } }
        override fun listenBinderDead(listener: () -> Unit) = AutoCloseable { }
        override fun listenPermissionResult(listener: (Int, Boolean) -> Unit) = AutoCloseable { }
        override fun requestPermission(requestCode: Int) = Unit
    }

    private fun field(target: Any, name: String): Any? =
        target.javaClass.getDeclaredField(name).also { it.isAccessible = true }.get(target)

    private fun clean() = EmbeddedWorkspaceRunResult.Stopped(emptyList(), emptyList())
    private fun incomplete() = EmbeddedWorkspaceRunResult.CleanupIncomplete(emptyList(), emptyList())

    companion object {
        private fun fieldValue(target: Any, name: String): Any? =
            target.javaClass.getDeclaredField(name).also { it.isAccessible = true }.get(target)
    }
}
