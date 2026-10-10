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
        var value = PhysicalButtonConfiguration(true, workspace.id)
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
    @Test fun disabled_doesNotLoadOrLaunch() = runBlocking {
        preferences.setEnabled(false)
        assertEquals(PhysicalButtonActionResult.NOT_CONFIGURED, dispatcher().dispatch())
        assertTrue(repository.requestedIds.isEmpty())
        assertTrue(launched.isEmpty())
    }
    @Test fun unconfigured_doesNotLoadOrLaunch() = runBlocking {
        preferences.setWorkspace(null)
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
        repository.beforeRead = { preferences.setWorkspace("another-id") }
        assertEquals(PhysicalButtonActionResult.BLOCKED, dispatcher().dispatch())
        assertTrue(launched.isEmpty())
    }
    @Test fun repositoryFailure_doesNotCrashOrLaunch() = runBlocking {
        repository.beforeRead = { error("read failure") }
        assertEquals(PhysicalButtonActionResult.FAILED, dispatcher().dispatch())
        assertTrue(launched.isEmpty())
    }
    @Test fun dispatchRejectedByClassicGate_isBlocked() = runBlocking {
        val dispatcher = PhysicalButtonActionDispatcher(preferences, repository, { true }, { false })
        assertEquals(PhysicalButtonActionResult.BLOCKED, dispatcher.dispatch())
        assertTrue(launched.isEmpty())
    }
    @Test fun cancellation_propagates() {
        repository.beforeRead = { throw CancellationException("service stopped") }
        assertThrows(CancellationException::class.java) { runBlocking { dispatcher().dispatch() } }
    }
    private fun dispatcher(canLaunch: suspend () -> Boolean = { true }) =
        PhysicalButtonActionDispatcher(preferences, repository, canLaunch) {
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
