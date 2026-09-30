package com.trancong.dexworkspacetouch.feature.embeddedworkspace

import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppSessionId
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppTarget
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionPhase
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionSnapshot
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedTouchEvent
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlanner
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceExecutionSurface
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspacePreflight
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunner
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunnerTimeoutPolicy
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceSessionFactory
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceSessionHandle
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceTouchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class EmbeddedWorkspaceRunnerControllerTest {
    @Test
    fun `start requires exactly both expected valid source slots and is consumed once`() = runTest {
        val factory = FakeFactory()
        val controller = controller(factory)
        val waze = FakeSurface(true)
        val calculator = FakeSurface(true)

        assertFalse(controller.state.value.canStart)
        controller.updateHostSlot(EmbeddedWorkspaceProofPlan.WAZE_SOURCE_ID, waze)
        assertFalse(controller.state.value.canStart)
        controller.updateHostSlot(EmbeddedWorkspaceProofPlan.CALCULATOR_SOURCE_ID, FakeSurface(false))
        assertFalse(controller.state.value.canStart)
        controller.updateHostSlot(EmbeddedWorkspaceProofPlan.CALCULATOR_SOURCE_ID, calculator)
        assertTrue(controller.state.value.canStart)

        controller.start()
        controller.start()

        assertEquals(2, factory.createdTargets.size)
        assertFalse(controller.state.value.canStart)
    }

    @Test
    fun `stop surface loss and touch stay source keyed through runner`() = runTest {
        val factory = FakeFactory()
        val controller = controller(factory)
        controller.updateHostSlot(EmbeddedWorkspaceProofPlan.WAZE_SOURCE_ID, FakeSurface(true))
        controller.updateHostSlot(EmbeddedWorkspaceProofPlan.CALCULATOR_SOURCE_ID, FakeSurface(true))
        controller.start()
        val touch = EmbeddedTouchEvent(0, 10f, 20f, 255f, 30L)

        assertEquals(
            EmbeddedWorkspaceTouchResult.Accepted,
            controller.sendTouch(EmbeddedWorkspaceProofPlan.WAZE_SOURCE_ID, touch),
        )
        assertEquals(1, factory.handleForPackage("com.waze").touchCalls)
        assertEquals(0, factory.handleForPackage("com.sec.android.app.popupcalculator").touchCalls)

        controller.surfaceLost(EmbeddedWorkspaceProofPlan.CALCULATOR_SOURCE_ID)

        assertEquals(1, factory.handleForPackage("com.waze").stopCalls)
        assertEquals(1, factory.handleForPackage("com.sec.android.app.popupcalculator").stopCalls)
        assertFalse(controller.state.value.canStart)
    }

    @Test
    fun `explicit stop and disposal converge on one runner cleanup`() = runTest {
        val factory = FakeFactory()
        val controller = controller(factory)
        controller.updateHostSlot(EmbeddedWorkspaceProofPlan.WAZE_SOURCE_ID, FakeSurface(true))
        controller.updateHostSlot(EmbeddedWorkspaceProofPlan.CALCULATOR_SOURCE_ID, FakeSurface(true))
        controller.start()

        controller.stop()
        controller.close()
        controller.close()

        assertEquals(1, factory.handleForPackage("com.waze").stopCalls)
        assertEquals(1, factory.handleForPackage("com.waze").closeCalls)
        assertEquals(1, factory.handleForPackage("com.sec.android.app.popupcalculator").stopCalls)
        assertEquals(1, factory.handleForPackage("com.sec.android.app.popupcalculator").closeCalls)
    }

    private fun kotlinx.coroutines.test.TestScope.controller(factory: FakeFactory): EmbeddedWorkspaceRunnerController {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val runner = EmbeddedWorkspaceRunner(
            preflight = EmbeddedWorkspacePreflight(EmbeddedWorkspaceProofPlan.geometryPolicy),
            sessionFactory = factory,
            timeoutPolicy = EmbeddedWorkspaceRunnerTimeoutPolicy(
                readyTimeout = 10.seconds,
                activeTimeout = 30.seconds,
                cleanupTimeout = 20.seconds,
            ),
            scope = this,
            dispatcher = dispatcher,
        )
        return EmbeddedWorkspaceRunnerController(
            plan = EmbeddedWorkspacePlanner().plan(EmbeddedWorkspaceProofPlan.createRequest()),
            runner = runner,
        )
    }

    private class FakeSurface(var valid: Boolean) : EmbeddedWorkspaceExecutionSurface {
        override val isValid: Boolean get() = valid
    }

    private class FakeFactory : EmbeddedWorkspaceSessionFactory {
        val createdTargets = mutableListOf<EmbeddedAppTarget>()
        private val handles = mutableMapOf<String, FakeHandle>()

        override fun create(
            target: EmbeddedAppTarget,
            observer: (EmbeddedSessionSnapshot) -> Unit,
        ): EmbeddedWorkspaceSessionHandle {
            createdTargets += target
            return FakeHandle(target.packageName, observer).also { handles[target.packageName] = it }
        }

        fun handleForPackage(packageName: String): FakeHandle = handles.getValue(packageName)
    }

    private class FakeHandle(
        packageName: String,
        private val observer: (EmbeddedSessionSnapshot) -> Unit,
    ) : EmbeddedWorkspaceSessionHandle {
        override val sessionId = EmbeddedAppSessionId("session-$packageName")
        var touchCalls = 0
        var stopCalls = 0
        var closeCalls = 0

        override fun connect() {
            observer(EmbeddedSessionSnapshot(EmbeddedSessionPhase.READY))
        }

        override fun start(surface: EmbeddedWorkspaceExecutionSurface) {
            observer(EmbeddedSessionSnapshot(EmbeddedSessionPhase.ACTIVE, displayId = sessionId.value.hashCode()))
        }

        override fun sendTouch(event: EmbeddedTouchEvent): Boolean {
            touchCalls++
            return true
        }

        override fun stop() {
            stopCalls++
        }

        override fun close() {
            closeCalls++
            observer(EmbeddedSessionSnapshot(EmbeddedSessionPhase.STOPPED))
        }
    }
}
