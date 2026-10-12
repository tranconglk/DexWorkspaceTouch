package com.trancong.dexworkspacetouch.feature.physicalbutton

import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import com.trancong.dexworkspacetouch.workspace.persistence.repository.WorkspaceRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhysicalButtonClearBindingTest {
    private val storage = MemoryStorage()
    private val preferences = PhysicalButtonPreferences(storage)
    private val workspace = Workspace("chosen-id", "Chosen", WorkspaceCanvas(emptyList()), 0, 1, 0, 0)
    private val repository = FakeRepository(workspace)
    private val launched = mutableListOf<Workspace>()

    @Test fun clearBinding_clearsDescriptorImmediately() {
        preferences.clearRemoteBinding()
        assertNull(preferences.configuration.value.deviceDescriptor)
    }

    @Test fun clearBinding_clearsDisplayNameImmediately() {
        preferences.clearRemoteBinding()
        assertNull(preferences.configuration.value.deviceName)
    }

    @Test fun clearBinding_keepsEnabledAndWorkspace() {
        for (enabled in listOf(true, false)) {
            val localPreferences = PhysicalButtonPreferences(MemoryStorage(enabled))
            localPreferences.clearRemoteBinding()
            assertEquals(enabled, localPreferences.configuration.value.enabled)
            assertEquals("chosen-id", localPreferences.configuration.value.volumeUpWorkspaceId)
            assertNull(localPreferences.configuration.value.deviceDescriptor)
        }
    }

    @Test fun clearBinding_persistsBothFieldsInOneWriteAndReloadsUnbound() {
        preferences.clearRemoteBinding()
        assertEquals(listOf(PhysicalButtonConfiguration(true, "chosen-id")), storage.writes)
        val reloaded = PhysicalButtonPreferences(storage)
        assertEquals(PhysicalButtonConfiguration(true, "chosen-id"), reloaded.configuration.value)
        assertFalse(reloaded.bindingRequested.value)
    }

    @Test fun clearBinding_doesNotStartBindingOrAcceptUnrequestedCapture() {
        preferences.clearRemoteBinding()
        assertFalse(preferences.bindingRequested.value)
        assertFalse(preferences.captureRemote("another-remote", "Another remote"))
        assertNull(preferences.configuration.value.deviceDescriptor)
    }

    @Test fun clearBinding_cancelsPendingRebindAndRejectsLateCapture() {
        preferences.beginBinding()
        preferences.clearRemoteBinding()
        assertFalse(preferences.bindingRequested.value)
        assertFalse(preferences.captureRemote("old-remote", "AB Shutter3"))
        assertEquals(PhysicalButtonConfiguration(true, "chosen-id"), preferences.configuration.value)
    }

    @Test fun clearBinding_oldAndOtherExternalVolumeUpPassThroughEntirePress() {
        preferences.clearRemoteBinding()
        val handler = PhysicalButtonKeyHandler()
        for (descriptor in listOf("old-remote", "other-remote", null, "  ")) {
            val down = PhysicalButtonKeyEvent(24, 0, 0, 7, 100L, true, descriptor)
            for (event in listOf(down, down.copy(repeatCount = 1), down.copy(action = 1))) {
                assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handle(handler, event))
            }
        }
    }

    @Test fun clearAfterCompletedBoundPress_oldRemoteNextPressPassesThrough() {
        val handler = PhysicalButtonKeyHandler()
        val down = PhysicalButtonKeyEvent(24, 0, 0, 7, 100L, true, "old-remote")
        assertEquals(PhysicalButtonKeyDecision.TRACK_PRESS, handle(handler, down))
        assertEquals(PhysicalButtonKeyDecision.SHORT_PRESS, handle(handler, down.copy(action = 1)))
        preferences.clearRemoteBinding()
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handle(handler, down.copy(downTime = 200L)))
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, handle(handler, down.copy(downTime = 200L, action = 1)))
    }

    @Test fun clearBeforeQueuedActionStarts_doesNotLoadOrLaunchOldBinding() = runTest {
        val triggeringDescriptor = preferences.configuration.value.deviceDescriptor
        val action = async { dispatcher().dispatch(triggeringDescriptor) }
        preferences.clearRemoteBinding()
        runCurrent()
        assertEquals(PhysicalButtonActionResult.NOT_CONFIGURED, action.await())
        assertTrue(repository.requestedIds.isEmpty())
        assertTrue(launched.isEmpty())
    }

    @Test fun clearWhileActionWaitsAtGate_blocksOldBinding() = runTest {
        val gate = CompletableDeferred<Boolean>()
        val action = async { dispatcher { gate.await() }.dispatch("old-remote") }
        runCurrent()
        assertEquals(listOf("chosen-id"), repository.requestedIds)
        preferences.clearRemoteBinding()
        gate.complete(true)
        runCurrent()
        assertEquals(PhysicalButtonActionResult.BLOCKED, action.await())
        assertTrue(launched.isEmpty())
    }

    private fun handle(handler: PhysicalButtonKeyHandler, event: PhysicalButtonKeyEvent) = handler.handle(
        event, preferences.configuration.value.enabled, preferences.configuration.value.deviceDescriptor,
        preferences.bindingRequested.value,
    )

    private fun dispatcher(canLaunch: suspend () -> Boolean = { true }) =
        PhysicalButtonActionDispatcher(preferences, repository, RecordingPackageCloseBackend(), canLaunch) {
            launched += it
            true
        }

    private class MemoryStorage(enabled: Boolean = true) : PhysicalButtonStorage {
        private var value = PhysicalButtonConfiguration(enabled, "chosen-id", null, "old-remote", "AB Shutter3")
        val writes = mutableListOf<PhysicalButtonConfiguration>()
        override fun read() = value
        override fun write(configuration: PhysicalButtonConfiguration) {
            writes += configuration
            value = configuration
        }
    }

    private class FakeRepository(private val workspace: Workspace) : WorkspaceRepository {
        val requestedIds = mutableListOf<String>()
        override fun observeAll() = flowOf(listOf(workspace))
        override suspend fun getById(id: String): Workspace? {
            requestedIds += id
            return workspace.takeIf { it.id == id }
        }
        override suspend fun insert(workspace: Workspace) = Unit
        override suspend fun update(workspace: Workspace) = Unit
        override suspend fun deleteById(id: String) = Unit
        override suspend fun exists(id: String) = workspace.id == id
        override suspend fun count() = 1
    }
}
