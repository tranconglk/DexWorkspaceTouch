package com.trancong.dexworkspacetouch.workspace.launcher.presentation

import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchFailure
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchFailureReason
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTarget
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTargetResult
import com.trancong.dexworkspacetouch.workspace.launcher.model.LaunchApplicationIssue
import com.trancong.dexworkspacetouch.workspace.launcher.model.LaunchReadiness
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchRequest
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchResult
import com.trancong.dexworkspacetouch.workspace.library.model.WorkspaceLibraryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceLaunchViewModelTest {
    @Test fun `ready invokes launcher for exact selected workspace`() {
        val runtime = FakeRuntime(result = success)
        val viewModel = viewModel { ready }

        viewModel.launchWorkspace(item, runtime, hostToken)

        assertEquals(listOf(request), runtime.requests)
        assertEquals(item.id, (viewModel.state as WorkspaceLaunchUiState.Completed).workspaceId)
    }

    @Test fun `empty cells does not invoke launcher`() = assertReadinessStops(
        LaunchReadiness.EmptyCells(listOf("cell")),
    )

    @Test fun `missing applications does not invoke launcher`() = assertReadinessStops(
        LaunchReadiness.MissingApplications(listOf(issue)),
    )

    @Test fun `non launchable applications does not invoke launcher`() = assertReadinessStops(
        LaunchReadiness.NonLaunchableApplications(listOf(issue)),
    )

    @Test fun `invalid canvas does not invoke launcher`() = assertReadinessStops(
        LaunchReadiness.InvalidCanvas(
            listOf(com.trancong.dexworkspacetouch.workspace.designer.model.CanvasValidationIssue.EmptyCanvas),
        ),
    )

    @Test fun `success maps to completed state`() {
        val viewModel = launchWith(success)
        assertTrue((viewModel.state as WorkspaceLaunchUiState.Completed).result is WorkspaceLaunchResult.Success)
    }

    @Test fun `partial success maps to completed state`() {
        val result = WorkspaceLaunchResult.PartialSuccess(
            listOf(AppLaunchTargetResult(target)),
            listOf(failure),
        )
        val viewModel = launchWith(result)
        assertEquals(result, (viewModel.state as WorkspaceLaunchUiState.Completed).result)
    }

    @Test fun `failure maps to completed state`() {
        val result = WorkspaceLaunchResult.Failure(listOf(failure))
        val viewModel = launchWith(result)
        assertEquals(result, (viewModel.state as WorkspaceLaunchUiState.Completed).result)
    }

    @Test fun `double tap does not start second sequence`() {
        val runtime = FakeRuntime(block = true)
        val viewModel = viewModel { ready }

        viewModel.launchWorkspace(item, runtime, hostToken)
        viewModel.launchWorkspace(item.copy(id = "other"), runtime, hostToken)

        assertEquals(1, runtime.requests.size)
        viewModel.cancelLaunch()
    }

    @Test fun `cancel moves to explicit cancelled state`() {
        val runtime = FakeRuntime(block = true)
        val viewModel = viewModel { ready }
        viewModel.launchWorkspace(item, runtime, hostToken)

        viewModel.cancelLaunch()

        assertTrue(viewModel.state is WorkspaceLaunchUiState.Cancelled)
    }

    @Test fun `legacy unavailable selects legacy guidance`() {
        val viewModel = launchWithEnvironment(LaunchEnvironmentFailure.LEGACY_WORK_AREA_UNAVAILABLE)
        assertEquals(
            LaunchEnvironmentFailure.LEGACY_WORK_AREA_UNAVAILABLE,
            (viewModel.state as WorkspaceLaunchUiState.LaunchError).reason,
        )
    }

    @Test fun `modern unavailable does not select legacy guidance`() {
        val viewModel = launchWithEnvironment(LaunchEnvironmentFailure.WORK_AREA_UNAVAILABLE)
        assertEquals(
            LaunchEnvironmentFailure.WORK_AREA_UNAVAILABLE,
            (viewModel.state as WorkspaceLaunchUiState.LaunchError).reason,
        )
    }

    @Test fun `dismiss completed result returns idle`() {
        val viewModel = launchWith(success)
        viewModel.dismissResult()
        assertEquals(WorkspaceLaunchUiState.Idle, viewModel.state)
    }

    @Test fun `host disposal cancels active launcher without retaining dead host`() {
        val runtime = FakeRuntime(block = true)
        val viewModel = viewModel { ready }
        viewModel.launchWorkspace(item, runtime, hostToken)

        viewModel.onHostDisposed(hostToken)

        assertTrue(viewModel.state is WorkspaceLaunchUiState.LaunchError)
    }

    private fun assertReadinessStops(readiness: LaunchReadiness) {
        val runtime = FakeRuntime()
        val viewModel = viewModel { readiness }
        viewModel.launchWorkspace(item, runtime, hostToken)
        assertTrue(runtime.requests.isEmpty())
        assertEquals(readiness, (viewModel.state as WorkspaceLaunchUiState.ReadinessError).readiness)
    }

    private fun launchWith(result: WorkspaceLaunchResult): WorkspaceLaunchViewModel {
        val viewModel = viewModel { ready }
        viewModel.launchWorkspace(item, FakeRuntime(result = result), hostToken)
        return viewModel
    }

    private fun launchWithEnvironment(reason: LaunchEnvironmentFailure): WorkspaceLaunchViewModel {
        val viewModel = viewModel { ready }
        viewModel.launchWorkspace(item, FakeRuntime(environmentFailure = reason), hostToken)
        return viewModel
    }

    private fun viewModel(readiness: (WorkspaceLibraryItem) -> LaunchReadiness) =
        WorkspaceLaunchViewModel(
            createReadiness = readiness,
            suppliedScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        )

    private class FakeRuntime(
        private val result: WorkspaceLaunchResult = success,
        private val environmentFailure: LaunchEnvironmentFailure? = null,
        private val block: Boolean = false,
    ) : WorkspaceLaunchRuntime {
        val requests = mutableListOf<WorkspaceLaunchRequest>()
        override fun checkEnvironment(): LaunchEnvironmentCheck = environmentFailure?.let {
            LaunchEnvironmentCheck.Unavailable(it)
        } ?: LaunchEnvironmentCheck.Ready

        override suspend fun launch(request: WorkspaceLaunchRequest): WorkspaceLaunchResult {
            requests += request
            if (block) suspendCancellableCoroutine<Nothing> { }
            return result
        }
    }

    private companion object {
        val hostToken = Any()
        val identity = AppIdentity("com.example.app", "com.example.app.Main")
        val assignedApp = AssignedApp(identity.packageName, identity.activityName!!, "Example")
        val canvas = WorkspaceCanvas(listOf(WorkspaceCell("cell", NormalizedBounds.FullCanvas, assignedApp)))
        val item = WorkspaceLibraryItem("workspace", "Workspace", canvas, 1)
        val target = AppLaunchTarget(identity, NormalizedBounds.FullCanvas, 0)
        val request = WorkspaceLaunchRequest(item.id, item.name, listOf(target))
        val ready = LaunchReadiness.Ready(request)
        val success = WorkspaceLaunchResult.Success(listOf(AppLaunchTargetResult(target)))
        val failure = AppLaunchFailure(target, AppLaunchFailureReason.UNKNOWN)
        val issue = LaunchApplicationIssue("cell", identity)
    }
}
