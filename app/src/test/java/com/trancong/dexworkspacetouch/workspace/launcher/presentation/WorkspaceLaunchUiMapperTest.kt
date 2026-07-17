package com.trancong.dexworkspacetouch.workspace.launcher.presentation

import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchFailure
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchFailureReason
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTarget
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WorkspaceLaunchUiMapperTest {
    @Test fun `all failure reasons have friendly Vietnamese messages`() {
        AppLaunchFailureReason.entries.forEach { reason ->
            val message = reason.userMessage()
            assertFalse(message.isBlank())
            assertFalse(message.contains("Exception"))
        }
    }

    @Test fun `failure display order is deterministic and ignores technical messages`() {
        val second = failure(2, "java.lang.IllegalStateException: secret")
        val first = failure(1, "raw stack trace")
        val state = WorkspaceLaunchUiState.Completed(
            "id", "Name", 2, WorkspaceLaunchResult.Failure(listOf(second, first)),
        )

        assertEquals(listOf(1, 2), state.failures().map { it.target.order })
        val visible = state.failures().map { it.reason.userMessage() }.joinToString()
        assertFalse(visible.contains("secret"))
        assertFalse(visible.contains("stack trace"))
    }

    private fun failure(order: Int, technical: String) = AppLaunchFailure(
        AppLaunchTarget(
            AppIdentity("com.example.$order", "Activity$order"),
            NormalizedBounds.FullCanvas,
            order,
        ),
        AppLaunchFailureReason.UNKNOWN,
        technical,
    )
}
