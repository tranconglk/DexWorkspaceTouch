package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledApp
import com.trancong.dexworkspacetouch.workspace.apppicker.model.ListInstalledAppCatalog
import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunResult
import com.trancong.dexworkspacetouch.workspace.launcher.WorkspaceLaunchRequestFactory
import com.trancong.dexworkspacetouch.workspace.launcher.model.*
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.*
import com.trancong.dexworkspacetouch.workspace.library.model.WorkspaceLibraryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.*
import org.junit.Test

/** Real admission + request factory + Classic ViewModel; only the Android launch boundary is replaced. */
class ClassicWorkspaceAdmissionTest {
    @Test fun admittedClassicLaunchCompletesWithoutEmbeddedExecutionOrNavigation() {
        val f = Fixture()
        assertTrue(f.routing.openClassic {
            assertNull(f.gate.tryAcquireEmbedded("competing"))
            f.launch()
        })
        assertEquals(listOf(request), f.runtime.requests)
        assertEquals(success, (f.viewModel.state as WorkspaceLaunchUiState.Completed).result)
        assertEquals(ProductRunStatus(), f.gate.status.value)
        assertTrue(f.gate.canEnterEmbedded())
        assertTrue(f.embeddedEntries.isEmpty())
    }

    @Test fun classicLaunchFailureDoesNotFallBackToEmbedded() {
        val failure = WorkspaceLaunchResult.Failure(listOf(AppLaunchFailure(target, AppLaunchFailureReason.UNKNOWN)))
        val f = Fixture(result = failure)
        assertTrue(f.routing.openClassic(f::launch))
        assertEquals(failure, (f.viewModel.state as WorkspaceLaunchUiState.Completed).result)
        assertEquals(listOf(request), f.runtime.requests)
        assertTrue(f.embeddedEntries.isEmpty())
        assertEquals(ProductRunStatus(), f.gate.status.value)
    }

    @Test fun unavailableClassicEnvironmentDoesNotFallBackToEmbedded() {
        val f = Fixture(environment = LaunchEnvironmentCheck.Unavailable(LaunchEnvironmentFailure.HOST_NOT_EXTERNAL))
        assertTrue(f.routing.openClassic(f::launch))
        assertEquals(LaunchEnvironmentFailure.HOST_NOT_EXTERNAL,
            (f.viewModel.state as WorkspaceLaunchUiState.LaunchError).reason)
        assertTrue(f.runtime.requests.isEmpty())
        assertTrue(f.embeddedEntries.isEmpty())
        assertEquals(ProductRunStatus(), f.gate.status.value)
    }

    @Test fun incompleteEmbeddedCleanupRejectsBeforeClassicReadinessOrRuntime() {
        val f = Fixture()
        val token = checkNotNull(f.gate.tryAcquireEmbedded(item.id))
        val operation = checkNotNull(f.gate.startOperation(token))
        assertTrue(f.gate.markInvoked(operation))
        assertTrue(f.gate.acceptResult(operation, ProductExecutionValue.from(
            EmbeddedWorkspaceRunResult.CleanupIncomplete(emptyList(), emptyList()))))
        val before = f.gate.status.value
        assertFalse(f.routing.openClassic(f::launch))
        assertEquals(0, f.readinessCalls)
        assertEquals(0, f.runtime.environmentChecks)
        assertTrue(f.runtime.requests.isEmpty())
        assertEquals(WorkspaceLaunchUiState.Idle, f.viewModel.state)
        assertSame(before, f.gate.status.value)
        assertTrue(f.embeddedEntries.isEmpty())
    }

    @Test fun explicitEmbeddedEntryNavigatesWithoutLaunchingEitherRuntime() {
        val f = Fixture()
        assertTrue(f.embeddedEntries.isEmpty())
        assertTrue(f.routing.openEmbedded(item.id))
        assertEquals(listOf(item.id), f.embeddedEntries)
        assertEquals(0, f.readinessCalls)
        assertEquals(0, f.runtime.environmentChecks)
        assertTrue(f.runtime.requests.isEmpty())
        assertEquals(WorkspaceLaunchUiState.Idle, f.viewModel.state)
        assertEquals(ProductRunStatus(), f.gate.status.value)
    }

    private class Fixture(
        result: WorkspaceLaunchResult = success,
        environment: LaunchEnvironmentCheck = LaunchEnvironmentCheck.Ready,
    ) {
        val gate = EmbeddedProductRunGate()
        val embeddedEntries = mutableListOf<String>()
        val routing = EmbeddedWorkspaceProductRouting(gate) { embeddedEntries += it }
        val runtime = RecordingClassicRuntime(result, environment)
        private val requestFactory = WorkspaceLaunchRequestFactory(ListInstalledAppCatalog(
            listOf(InstalledApp("com.example.app", "com.example.app.Main", "Example", true))))
        var readinessCalls = 0
        val viewModel = WorkspaceLaunchViewModel({ workspace ->
            readinessCalls++
            requestFactory.create(workspace.id, workspace.name, workspace.canvas)
        }, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        fun launch() = viewModel.launchWorkspace(item, runtime, this)
    }

    private class RecordingClassicRuntime(
        private val result: WorkspaceLaunchResult,
        private val environment: LaunchEnvironmentCheck,
    ) : WorkspaceLaunchRuntime {
        val requests = mutableListOf<WorkspaceLaunchRequest>()
        var environmentChecks = 0
        override fun checkEnvironment(): LaunchEnvironmentCheck { environmentChecks++; return environment }
        override suspend fun launch(request: WorkspaceLaunchRequest): WorkspaceLaunchResult {
            requests += request
            return result
        }
    }

    private companion object {
        val identity = AppIdentity("com.example.app", "com.example.app.Main")
        val canvas = WorkspaceCanvas(listOf(WorkspaceCell("cell", NormalizedBounds.FullCanvas,
            AssignedApp(identity.packageName, identity.activityName!!, "Example"))))
        val item = WorkspaceLibraryItem("persisted-workspace", "Workspace", canvas, 1)
        val target = AppLaunchTarget("cell", identity, NormalizedBounds.FullCanvas, 0)
        val request = WorkspaceLaunchRequest(item.id, item.name, listOf(target))
        val success = WorkspaceLaunchResult.Success(listOf(AppLaunchTargetResult(target)))
    }
}
