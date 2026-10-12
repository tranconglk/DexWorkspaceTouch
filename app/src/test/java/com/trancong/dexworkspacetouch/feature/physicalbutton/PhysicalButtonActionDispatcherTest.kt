package com.trancong.dexworkspacetouch.feature.physicalbutton

import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import com.trancong.dexworkspacetouch.workspace.persistence.repository.WorkspaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalButtonActionDispatcherTest {
    private val workspace = Workspace("chosen-id", "Chosen", WorkspaceCanvas(emptyList()), 0, 1, 0, 0)
    private val storage = object : PhysicalButtonStorage {
        var value = PhysicalButtonConfiguration(true, workspace.id, null, "remote-descriptor")
        override fun read() = value
        override fun write(configuration: PhysicalButtonConfiguration) { value = configuration }
    }
    private val preferences = PhysicalButtonPreferences(storage)
    private val launched = mutableListOf<Workspace>()
    private val repository = FakeRepository(workspace)

    @Test fun configuredWorkspace_dispatchesExactStoredWorkspace() = runBlocking {
        assertEquals(PhysicalButtonActionResult.DISPATCHED, dispatcher().dispatch())
        assertEquals(listOf("chosen-id"), repository.requestedIds)
        assertEquals(listOf(workspace), launched)
    }
    @Test fun dualMapping_dispatchesExactWorkspaceForEachKey() = runBlocking {
        preferences.setVolumeDownWorkspace("down-id")
        val downWorkspace = workspace.copy(id = "down-id", name = "Down")
        assertEquals(PhysicalButtonActionResult.DISPATCHED, dispatcher().dispatch(keyCode = 24))
        repository.stored = downWorkspace
        assertEquals(PhysicalButtonActionResult.DISPATCHED, dispatcher().dispatch(keyCode = 25))
        assertEquals(listOf("chosen-id", "down-id"), repository.requestedIds)
        assertEquals(listOf(workspace, downWorkspace), launched)
    }

    @Test fun longPressForEitherKey_closesMappedEmptyWorkspaceWithoutCallingLaunchGateOrLauncher() = runBlocking {
        preferences.setVolumeDownWorkspace("down-id")
        var gateCalls = 0
        val actionDispatcher = dispatcher { gateCalls++; true }
        for (keyCode in listOf(24, 25)) {
            val id = preferences.configuration.value.workspaceForKey(keyCode)!!
            repository.stored = workspace.copy(id = id)
            assertEquals("CLOSE_NO_OP",
                actionDispatcher.dispatch(keyCode = keyCode, gesture = PhysicalButtonGesture.LONG_PRESS).name)
        }
        assertEquals(listOf("chosen-id", "down-id"), repository.requestedIds)
        assertEquals(0, gateCalls)
        assertTrue(launched.isEmpty())
    }

    @Test fun configChangedBeforeQueuedGesture_blocksBothShortAndLong() = runBlocking {
        val snapshot = preferences.configuration.value
        preferences.setVolumeDownWorkspace("another-id")
        for (gesture in PhysicalButtonGesture.entries) {
            assertEquals(PhysicalButtonActionResult.BLOCKED,
                dispatcher().dispatch(gesture = gesture, expectedConfiguration = snapshot))
        }
        assertTrue(repository.requestedIds.isEmpty())
        assertTrue(launched.isEmpty())
    }

    @Test fun longPressRevalidatesConfigurationAfterWorkspaceLookup() = runBlocking {
        repository.beforeRead = { preferences.setEnabled(false) }
        assertEquals(PhysicalButtonActionResult.BLOCKED,
            dispatcher().dispatch(gesture = PhysicalButtonGesture.LONG_PRESS))
        assertTrue(launched.isEmpty())
    }

    @Test fun longPressWithMissingWorkspace_doesNotRecordOrLaunch() = runBlocking {
        repository.stored = null
        assertEquals(PhysicalButtonActionResult.WORKSPACE_UNAVAILABLE,
            dispatcher().dispatch(gesture = PhysicalButtonGesture.LONG_PRESS))
        assertTrue(launched.isEmpty())
    }

    @Test fun longPressRespectsFeatureBindingAndMappingGates() = runBlocking {
        preferences.setEnabled(false)
        assertEquals(PhysicalButtonActionResult.NOT_CONFIGURED,
            dispatcher().dispatch(gesture = PhysicalButtonGesture.LONG_PRESS))
        preferences.setEnabled(true)
        assertEquals(PhysicalButtonActionResult.NOT_CONFIGURED,
            dispatcher().dispatch(keyCode = 25, gesture = PhysicalButtonGesture.LONG_PRESS))
        preferences.beginBinding()
        assertEquals(PhysicalButtonActionResult.NOT_CONFIGURED,
            dispatcher().dispatch(gesture = PhysicalButtonGesture.LONG_PRESS))
        preferences.cancelBinding()
        assertEquals(PhysicalButtonActionResult.BLOCKED,
            dispatcher().dispatch("other-remote", gesture = PhysicalButtonGesture.LONG_PRESS))
        assertTrue(repository.requestedIds.isEmpty())
        assertTrue(launched.isEmpty())
    }
    @Test fun unconfiguredDown_neverFallsBackToConfiguredUp() = runBlocking {
        assertEquals(PhysicalButtonActionResult.NOT_CONFIGURED, dispatcher().dispatch(keyCode = 25))
        assertTrue(repository.requestedIds.isEmpty())
        assertTrue(launched.isEmpty())
    }
    @Test fun deletedDown_neverFallsBackToAvailableUp() = runBlocking {
        preferences.setVolumeDownWorkspace("deleted-id")
        assertEquals(PhysicalButtonActionResult.WORKSPACE_UNAVAILABLE, dispatcher().dispatch(keyCode = 25))
        assertEquals(listOf("deleted-id"), repository.requestedIds)
        assertTrue(launched.isEmpty())
    }
    @Test fun downOnly_dispatchesDownAndLeavesUpUnconfigured() = runBlocking {
        preferences.setVolumeDownWorkspace(workspace.id)
        preferences.setVolumeUpWorkspace(null)
        assertEquals(PhysicalButtonActionResult.NOT_CONFIGURED, dispatcher().dispatch(keyCode = 24))
        assertEquals(PhysicalButtonActionResult.DISPATCHED, dispatcher().dispatch(keyCode = 25))
        assertEquals(listOf(workspace), launched)
    }
    @Test fun unknownKey_doesNotLoadOrLaunch() = runBlocking {
        assertEquals(PhysicalButtonActionResult.NOT_CONFIGURED, dispatcher().dispatch(keyCode = 85))
        assertTrue(repository.requestedIds.isEmpty())
        assertTrue(launched.isEmpty())
    }
    @Test fun mappingChangedBeforeQueuedDispatch_blocksInsteadOfOpeningNewSelection() = runBlocking {
        preferences.setVolumeDownWorkspace("down-id")
        preferences.setVolumeDownWorkspace(workspace.id)
        assertEquals(PhysicalButtonActionResult.BLOCKED,
            dispatcher().dispatch(keyCode = 25, expectedWorkspaceId = "down-id"))
        assertTrue(repository.requestedIds.isEmpty())
        assertTrue(launched.isEmpty())
    }
    @Test fun disabled_doesNotLoadOrLaunch() = runBlocking {
        preferences.setEnabled(false)
        assertEquals(PhysicalButtonActionResult.NOT_CONFIGURED, dispatcher().dispatch())
        assertTrue(repository.requestedIds.isEmpty())
        assertTrue(launched.isEmpty())
    }
    @Test fun unconfigured_doesNotLoadOrLaunch() = runBlocking {
        preferences.setVolumeUpWorkspace(null)
        assertEquals(PhysicalButtonActionResult.NOT_CONFIGURED, dispatcher().dispatch())
        assertTrue(repository.requestedIds.isEmpty())
        assertTrue(launched.isEmpty())
    }
    @Test fun deletedWorkspace_doesNotFallbackToAnotherWorkspace() = runBlocking {
        repository.stored = null
        assertEquals(PhysicalButtonActionResult.WORKSPACE_UNAVAILABLE, dispatcher().dispatch())
        assertEquals(listOf("chosen-id"), repository.requestedIds)
        assertTrue(launched.isEmpty())
    }
    @Test fun deniedExistingGate_doesNotLaunch() = runBlocking {
        assertEquals(PhysicalButtonActionResult.BLOCKED, dispatcher { false }.dispatch())
        assertTrue(launched.isEmpty())
    }
    @Test fun disabledWhileCheckingGate_doesNotLaunchQueuedAction() = runBlocking {
        assertEquals(PhysicalButtonActionResult.BLOCKED, dispatcher {
            preferences.setEnabled(false)
            true
        }.dispatch())
        assertTrue(launched.isEmpty())
    }
    @Test fun selectionChangedWhileLoading_doesNotLaunchOldWorkspace() = runBlocking {
        repository.beforeRead = { preferences.setVolumeUpWorkspace("another-id") }
        assertEquals(PhysicalButtonActionResult.BLOCKED, dispatcher().dispatch())
        assertTrue(launched.isEmpty())
    }
    @Test fun repositoryFailure_doesNotCrashOrLaunch() = runBlocking {
        repository.beforeRead = { error("read failure") }
        assertEquals(PhysicalButtonActionResult.FAILED, dispatcher().dispatch())
        assertTrue(launched.isEmpty())
    }
    @Test fun dispatchRejectedByClassicGate_isBlocked() = runBlocking {
        val dispatcher = PhysicalButtonActionDispatcher(preferences, repository, RecordingPackageCloseBackend(), { true }, { false })
        assertEquals(PhysicalButtonActionResult.BLOCKED, dispatcher.dispatch())
        assertTrue(launched.isEmpty())
    }
    @Test fun cancellation_propagates() {
        repository.beforeRead = { throw CancellationException("service stopped") }
        assertThrows(CancellationException::class.java) { runBlocking { dispatcher().dispatch() } }
    }

    @Test fun missingBinding_doesNotLoadOrLaunch() = runBlocking {
        storage.value = PhysicalButtonConfiguration(true, workspace.id)
        assertEquals(PhysicalButtonActionResult.NOT_CONFIGURED,
            dispatcher(PhysicalButtonPreferences(storage)).dispatch())
        assertTrue(repository.requestedIds.isEmpty())
        assertTrue(launched.isEmpty())
    }

    @Test fun bindingMode_doesNotLoadOrLaunch() = runBlocking {
        preferences.beginBinding()
        assertEquals(PhysicalButtonActionResult.NOT_CONFIGURED, dispatcher().dispatch())
        assertTrue(repository.requestedIds.isEmpty())
        assertTrue(launched.isEmpty())
    }

    @Test fun bindingStartedWhileCheckingGate_blocksQueuedAction() = runBlocking {
        assertEquals(PhysicalButtonActionResult.BLOCKED, dispatcher {
            preferences.beginBinding()
            true
        }.dispatch())
        assertTrue(launched.isEmpty())
    }

    @Test fun bindingChangedWhileCheckingGate_blocksOldRemoteAction() = runBlocking {
        assertEquals(PhysicalButtonActionResult.BLOCKED, dispatcher {
            preferences.beginBinding()
            preferences.captureRemote("other-remote", "Another remote")
            true
        }.dispatch())
        assertTrue(launched.isEmpty())
    }

    @Test fun rebindBeforeQueuedActionStarts_blocksOriginalRemoteAction() = runBlocking {
        val triggeringDescriptor = preferences.configuration.value.deviceDescriptor
        preferences.beginBinding()
        preferences.captureRemote("other-remote", "Another remote")
        assertEquals(PhysicalButtonActionResult.BLOCKED, dispatcher().dispatch(triggeringDescriptor))
        assertTrue(repository.requestedIds.isEmpty())
        assertTrue(launched.isEmpty())
    }

    private fun dispatcher(preferences: PhysicalButtonPreferences = this.preferences, canLaunch: suspend () -> Boolean = { true }) =
        PhysicalButtonActionDispatcher(preferences, repository, RecordingPackageCloseBackend(), canLaunch) {
            launched += it
            true
        }
    private class FakeRepository(var stored: Workspace?) : WorkspaceRepository {
        val requestedIds = mutableListOf<String>()
        var beforeRead: () -> Unit = {}
        override fun observeAll() = flowOf(listOfNotNull(stored))
        override suspend fun getById(id: String): Workspace? {
            requestedIds += id
            beforeRead()
            return stored?.takeIf { it.id == id }
        }
        override suspend fun insert(workspace: Workspace) = Unit
        override suspend fun update(workspace: Workspace) = Unit
        override suspend fun deleteById(id: String) = Unit
        override suspend fun exists(id: String) = stored?.id == id
        override suspend fun count() = if (stored == null) 0 else 1
    }
}
