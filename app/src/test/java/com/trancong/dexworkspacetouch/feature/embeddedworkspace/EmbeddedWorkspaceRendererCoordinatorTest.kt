package com.trancong.dexworkspacetouch.feature.embeddedworkspace

import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppGeometry
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppSessionId
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppTarget
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionPhase
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionSnapshot
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedTouchEvent
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlan
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlanItem
import com.trancong.dexworkspacetouch.workspace.execution.embedded.layout.EmbeddedWorkspaceLayoutMapper
import com.trancong.dexworkspacetouch.workspace.execution.embedded.layout.EmbeddedWorkspaceLayoutRejection
import com.trancong.dexworkspacetouch.workspace.execution.embedded.layout.EmbeddedWorkspaceLayoutResult
import com.trancong.dexworkspacetouch.workspace.execution.embedded.layout.EmbeddedWorkspaceViewport
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedGeometryPolicy
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceExecutionSurface
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspacePreflight
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunner
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunnerTimeoutPolicy
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceSessionFactory
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceSessionHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class EmbeddedWorkspaceRendererCoordinatorTest {
    @Test fun geometryPolicyResolvesOnceAndRunnerReceivesFrozenValues() = runTest {
        var calls = 0
        val policy = EmbeddedGeometryPolicy { calls++; EmbeddedAppGeometry(900 + calls, 675, 320) }
        val snapshot = EmbeddedWorkspaceGeometrySnapshot.resolve(plan(), policy) as GeometrySnapshotResult.Ready
        assertEquals(2, calls)
        assertEquals(901, snapshot.snapshot.geometryFor("waze").width)
        assertEquals(901, snapshot.snapshot.asPolicy().geometryFor(snapshot.snapshot.planSnapshot.items[0]).width)
        assertEquals(2, calls)
        val factory = FakeFactory()
        val coordinator = coordinator(snapshot.snapshot, factory)
        coordinator.onViewportChanged(EmbeddedWorkspaceViewport(1100, 700))
        attachBoth(coordinator)
        assertNotNull(coordinator.start())
        assertEquals(listOf(901, 902), factory.targets.map { it.geometry.width })
        assertEquals(2, calls)
        coordinator.close()
    }

    @Test fun staleGenerationAndSurfaceEventsCannotRemoveNewSlot() = runTest {
        val first = coordinator(snapshot(), FakeFactory())
        first.onViewportChanged(EmbeddedWorkspaceViewport(1100, 700))
        val oldGeneration = Any()
        first.onSurfaceAvailable(oldGeneration, "waze", Any(), Any(), FakeSurface())
        assertFalse(first.state.value.canStart)
        val view = Any(); val oldSurface = Any(); val newSurface = Any()
        first.onSurfaceAvailable(first.generationToken, "waze", view, oldSurface, FakeSurface())
        first.onSurfaceAvailable(first.generationToken, "waze", view, oldSurface, FakeSurface())
        first.onSurfaceDestroyed(first.generationToken, "waze", view, newSurface)
        assertTrue(first.state.value.surfaceValidity.getValue("waze"))
        first.onSurfaceDestroyed(oldGeneration, "waze", view, oldSurface)
        assertTrue(first.state.value.surfaceValidity.getValue("waze"))
        first.close()
    }

    @Test fun temporaryZeroRetainsLayoutAndPositiveUnusableStopsOnce() = runTest {
        val factory = FakeFactory()
        val coordinator = coordinator(snapshot(), factory)
        coordinator.onViewportChanged(EmbeddedWorkspaceViewport(1100, 700))
        attachBoth(coordinator)
        coordinator.start()
        val oldLayout = coordinator.state.value.layout
        coordinator.onViewportChanged(EmbeddedWorkspaceViewport(0, 0))
        assertEquals(oldLayout, coordinator.state.value.layout)
        assertFalse(coordinator.state.value.touchEnabled)
        assertEquals(0, factory.handles.sumOf { it.stopCalls })
        coordinator.onViewportChanged(EmbeddedWorkspaceViewport(1400, 850))
        assertTrue(coordinator.state.value.touchEnabled)
        assertEquals(0, factory.handles.sumOf { it.stopCalls })
        coordinator.onViewportChanged(EmbeddedWorkspaceViewport(1, 850))
        coordinator.onViewportChanged(EmbeddedWorkspaceViewport(1, 850))
        assertTrue(coordinator.state.value.failure is EmbeddedWorkspaceLayoutRejection.ZeroPixelPane)
        assertEquals(2, factory.handles.sumOf { it.stopCalls })
    }

    @Test fun realDestroyStopsRunWhileStaleDestroyDoesNot() = runTest {
        val factory = FakeFactory()
        val coordinator = coordinator(snapshot(), factory)
        coordinator.onViewportChanged(EmbeddedWorkspaceViewport(1100, 700))
        val wazeView = Any(); val wazeSurface = Any()
        coordinator.onSurfaceAvailable(coordinator.generationToken, "waze", wazeView, wazeSurface, FakeSurface())
        coordinator.onSurfaceAvailable(coordinator.generationToken, "calc", Any(), Any(), FakeSurface())
        coordinator.start()
        coordinator.onSurfaceDestroyed(Any(), "waze", wazeView, wazeSurface)
        assertEquals(0, factory.handles.sumOf { it.stopCalls })
        coordinator.onSurfaceDestroyed(coordinator.generationToken, "waze", wazeView, wazeSurface)
        assertEquals(2, factory.handles.sumOf { it.stopCalls })
        assertFalse(coordinator.state.value.surfaceValidity.getValue("waze"))
        assertFalse(coordinator.state.value.touchEnabled)
    }

    @Test fun fullPlanValueAndGenerationGuardAgainstStaleLayout() = runTest {
        val items = plan().items.toMutableList()
        val mutablePlan = EmbeddedWorkspacePlan("ws", "Workspace", items)
        val frozen = (EmbeddedWorkspaceGeometrySnapshot.resolve(mutablePlan, EmbeddedGeometryPolicy { EmbeddedAppGeometry(900, 675, 320) }) as GeometrySnapshotResult.Ready).snapshot
        items[0] = items[0].copy(componentName = "com.waze.Changed")
        assertEquals("com.waze.Main", frozen.planSnapshot.items[0].componentName)
        val coordinator = coordinator(frozen, FakeFactory())
        val otherPlan = frozen.planSnapshot.copy(workspaceName = "Changed")
        val otherLayout = EmbeddedWorkspaceLayoutMapper().map(otherPlan, EmbeddedWorkspaceViewport(1100, 700)) as EmbeddedWorkspaceLayoutResult.Mapped
        coordinator.acceptLayout(Any(), otherLayout)
        assertEquals(null, coordinator.state.value.failure)
        coordinator.acceptLayout(coordinator.generationToken, otherLayout)
        assertTrue(coordinator.state.value.failure is EmbeddedWorkspaceLayoutRejection.MissingPlanItemCorrelation)
    }

    @Test fun sameSurfaceBecomingInvalidUsesSurfaceLoss() = runTest {
        val factory = FakeFactory()
        val coordinator = coordinator(snapshot(), factory)
        coordinator.onViewportChanged(EmbeddedWorkspaceViewport(1100, 700))
        val view = Any(); val surface = Any(); val validity = MutableSurface()
        coordinator.onSurfaceAvailable(coordinator.generationToken, "waze", view, surface, validity)
        coordinator.onSurfaceAvailable(coordinator.generationToken, "calc", Any(), Any(), FakeSurface())
        coordinator.start()
        validity.valid = false
        coordinator.onSurfaceAvailable(coordinator.generationToken, "waze", view, surface, validity)
        assertEquals(2, factory.handles.sumOf { it.stopCalls })
        assertFalse(coordinator.state.value.surfaceValidity.getValue("waze"))
    }

    @Test fun paneBridgeUsesFrozenGeometryAndLocalViewSizeForTouch() = runTest {
        val factory = FakeFactory()
        val coordinator = coordinator(snapshot(), factory)
        coordinator.onViewportChanged(EmbeddedWorkspaceViewport(1100, 700))
        val bridge = EmbeddedWorkspacePaneEventBridge(coordinator, "waze", Any())
        bridge.onSurfaceAvailable(Any(), FakeSurface())
        coordinator.onSurfaceAvailable(coordinator.generationToken, "calc", Any(), Any(), FakeSurface())
        coordinator.start()
        assertTrue(bridge.sendLocalTouch(0, 400f, 600f, 400, 600, 255f, 1L))
        assertEquals(899f, factory.handles[0].lastTouch?.x)
        assertEquals(674f, factory.handles[0].lastTouch?.y)
    }

    private fun plan(): EmbeddedWorkspacePlan = EmbeddedWorkspacePlan("ws", "Workspace", listOf(
        EmbeddedWorkspacePlanItem("waze", "com.waze", "com.waze.Main", NormalizedBounds(0f, 0f, .625f, 1f), 0),
        EmbeddedWorkspacePlanItem("calc", "com.calc", "com.calc.Main", NormalizedBounds(.625f, 0f, 1f, 1f), 1),
    ))

    private fun snapshot(): EmbeddedWorkspaceGeometrySnapshot =
        (EmbeddedWorkspaceGeometrySnapshot.resolve(plan(), EmbeddedGeometryPolicy { EmbeddedAppGeometry(900, 675, 320) }) as GeometrySnapshotResult.Ready).snapshot

    private fun kotlinx.coroutines.test.TestScope.coordinator(
        snapshot: EmbeddedWorkspaceGeometrySnapshot,
        factory: FakeFactory,
    ): EmbeddedWorkspaceRendererCoordinator {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val runner = EmbeddedWorkspaceRunner(
            EmbeddedWorkspacePreflight(snapshot.asPolicy()), factory,
            EmbeddedWorkspaceRunnerTimeoutPolicy(10.seconds, 30.seconds, 20.seconds), this, dispatcher,
        )
        return EmbeddedWorkspaceRendererCoordinator(snapshot, EmbeddedWorkspaceLayoutMapper(), EmbeddedWorkspaceRunnerController(snapshot.planSnapshot, runner))
    }

    private suspend fun attachBoth(coordinator: EmbeddedWorkspaceRendererCoordinator) {
        coordinator.onSurfaceAvailable(coordinator.generationToken, "waze", Any(), Any(), FakeSurface())
        coordinator.onSurfaceAvailable(coordinator.generationToken, "calc", Any(), Any(), FakeSurface())
    }

    private class FakeSurface : EmbeddedWorkspaceExecutionSurface { override val isValid = true }
    private class MutableSurface(var valid: Boolean = true) : EmbeddedWorkspaceExecutionSurface {
        override val isValid: Boolean get() = valid
    }
    private class FakeFactory : EmbeddedWorkspaceSessionFactory {
        val targets = mutableListOf<EmbeddedAppTarget>()
        val handles = mutableListOf<FakeHandle>()
        override fun create(target: EmbeddedAppTarget, observer: (EmbeddedSessionSnapshot) -> Unit): EmbeddedWorkspaceSessionHandle {
            targets += target
            return FakeHandle(target.packageName, observer).also(handles::add)
        }
    }
    private class FakeHandle(packageName: String, private val observer: (EmbeddedSessionSnapshot) -> Unit) : EmbeddedWorkspaceSessionHandle {
        override val sessionId = EmbeddedAppSessionId("session-$packageName")
        var stopCalls = 0
        var lastTouch: EmbeddedTouchEvent? = null
        override fun connect() { observer(EmbeddedSessionSnapshot(EmbeddedSessionPhase.READY)) }
        override fun start(surface: EmbeddedWorkspaceExecutionSurface) { observer(EmbeddedSessionSnapshot(EmbeddedSessionPhase.ACTIVE)) }
        override fun sendTouch(event: EmbeddedTouchEvent): Boolean { lastTouch = event; return true }
        override fun stop() { stopCalls++ }
        override fun close() { observer(EmbeddedSessionSnapshot(EmbeddedSessionPhase.STOPPED)) }
    }
}
