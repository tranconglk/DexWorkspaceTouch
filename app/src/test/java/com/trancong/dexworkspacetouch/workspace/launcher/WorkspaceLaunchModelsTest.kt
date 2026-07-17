package com.trancong.dexworkspacetouch.workspace.launcher

import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchFailure
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchFailureReason
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTarget
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTargetResult
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchRequest
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WorkspaceLaunchModelsTest {
    private val target = AppLaunchTarget(
        AppIdentity("com.example", "MainActivity"),
        NormalizedBounds.FullCanvas,
        order = 0,
    )

    @Test(expected = IllegalArgumentException::class)
    fun `blank workspace id is rejected`() {
        WorkspaceLaunchRequest(" ", "Name", listOf(target))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank workspace name is rejected`() {
        WorkspaceLaunchRequest("id", " ", listOf(target))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty targets are rejected`() {
        WorkspaceLaunchRequest("id", "Name", emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative target order is rejected`() {
        target.copy(order = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `duplicate target order is rejected`() {
        WorkspaceLaunchRequest("id", "Name", listOf(target, target.copy()))
    }

    @Test
    fun `success partial and failure retain their data`() {
        val launched = AppLaunchTargetResult(target)
        val failure = AppLaunchFailure(
            target,
            AppLaunchFailureReason.LAUNCH_REJECTED,
            technicalMessage = "rejected by platform",
        )

        val success = WorkspaceLaunchResult.Success(listOf(launched))
        val partial = WorkspaceLaunchResult.PartialSuccess(listOf(launched), listOf(failure))
        val failed = WorkspaceLaunchResult.Failure(listOf(failure))

        assertSame(target, success.launchedTargets.single().target)
        assertEquals(AppLaunchFailureReason.LAUNCH_REJECTED, partial.failedTargets.single().reason)
        assertEquals("rejected by platform", failed.failures.single().technicalMessage)
    }
}
