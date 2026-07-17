package com.trancong.dexworkspacetouch.workspace.library.state

import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell
import com.trancong.dexworkspacetouch.workspace.designer.model.assignApp
import com.trancong.dexworkspacetouch.workspace.designer.state.WorkspaceDesignerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceLibraryViewModelTest {
    @Test fun renameWorkspaceTrimsNameAndKeepsIdentityCanvasAndSelection() {
        val state = savedState()
        val original = state.workspaces.single()
        state.selectWorkspace(original.id)

        val renamed = state.renameWorkspace(original.id, "  Renamed  ")

        assertEquals("Renamed", renamed.name)
        assertEquals(original.id, renamed.id)
        assertSame(original.canvas, renamed.canvas)
        assertEquals(original.modifiedSequence + 1, renamed.modifiedSequence)
        assertEquals(original.id, state.selectedWorkspaceId)
        assertEquals(1, state.workspaces.size)
    }

    @Test fun renameRejectsBlankName() {
        val state = savedState()
        assertThrows(IllegalArgumentException::class.java) {
            state.renameWorkspace(state.workspaces.single().id, "   ")
        }
    }

    @Test fun renameRejectsMissingWorkspace() {
        assertThrows(IllegalArgumentException::class.java) {
            viewModel().renameWorkspace("missing", "Name")
        }
    }

    @Test fun deleteWorkspaceRemovesOnlyTargetAndClearsItsSelection() {
        val state = viewModel()
        state.createWorkspace()
        val first = state.saveWorkspace(canvas, "First")
        state.finishEditing()
        state.createWorkspace()
        val second = state.saveWorkspace(canvas.assignApp("cell", app), "Second")
        state.finishEditing()
        state.selectWorkspace(first.id)

        state.deleteWorkspace(first.id)

        assertEquals(listOf(second), state.workspaces)
        assertNull(state.selectedWorkspaceId)
        assertEquals(app, state.workspaces.single().canvas.cells.single().app)
    }

    @Test fun deleteDoesNotClearDifferentSelection() {
        val state = viewModel()
        state.createWorkspace()
        val first = state.saveWorkspace(canvas, "First")
        state.finishEditing()
        state.createWorkspace()
        val second = state.saveWorkspace(canvas, "Second")
        state.finishEditing()
        state.selectWorkspace(second.id)

        state.deleteWorkspace(first.id)

        assertEquals(second.id, state.selectedWorkspaceId)
    }

    @Test fun deleteRejectsMissingOrCurrentlyEditedWorkspace() {
        val state = savedState()
        val editedId = state.workspaces.single().id
        assertThrows(IllegalStateException::class.java) { state.deleteWorkspace(editedId) }
        assertThrows(IllegalArgumentException::class.java) { state.deleteWorkspace("missing") }
    }

    @Test fun deletedIdsAndDefaultNamesAreNeverReused() {
        val state = viewModel()
        state.createWorkspace()
        val first = state.saveWorkspace(canvas)
        state.finishEditing()
        state.deleteWorkspace(first.id)

        state.createWorkspace()
        val second = state.saveWorkspace(canvas)

        assertEquals("workspace-2", second.id)
        assertEquals("Workspace 2", second.name)
    }

    @Test fun libraryStartsEmpty() {
        assertTrue(viewModel().workspaces.isEmpty())
    }

    @Test fun createWorkspaceReturnsIndependentUnsavedCanvases() {
        val state = viewModel()
        val first = state.createWorkspace()
        val second = state.createWorkspace()

        assertNotSame(first, second)
        assertEquals(WorkspaceCanvas.singleCell(), first)
        assertEquals(WorkspaceCanvas.singleCell(), second)
        assertTrue(state.workspaces.isEmpty())
    }

    @Test fun blankFirstNameUsesSequentialDefault() {
        val state = viewModel()
        state.createWorkspace()
        assertEquals("Workspace 1", state.saveWorkspace(canvas, "  ").name)
        state.createWorkspace()
        assertEquals("Workspace 2", state.saveWorkspace(canvas, "").name)
    }

    @Test fun explicitNameIsTrimmed() {
        val state = viewModel()
        state.createWorkspace()
        assertEquals("Gaming", state.saveWorkspace(canvas, "  Gaming  ").name)
    }

    @Test fun selectionCanBeSetAndClearedWithoutStartingEdit() {
        val state = savedState()
        val item = state.workspaces.single()
        state.createWorkspace()
        state.selectWorkspace(item.id)
        assertEquals(item.id, state.selectedWorkspaceId)
        assertNull(state.editingWorkspaceId)

        state.clearSelection()
        assertNull(state.selectedWorkspaceId)
    }

    @Test fun editingReturnsStoredCanvasAsWorkingCopySource() {
        val state = savedState()
        val item = state.workspaces.single()

        assertSame(item.canvas, state.beginEditingWorkspace(item.id))
        assertEquals(item.id, state.editingWorkspaceId)
    }

    @Test fun savingExistingWorkspaceUpdatesWithoutDuplicateAndSortsNewestFirst() {
        val state = viewModel()
        state.createWorkspace()
        val first = state.saveWorkspace(canvas, "First")
        state.createWorkspace()
        val second = state.saveWorkspace(canvas, "Second")
        state.beginEditingWorkspace(first.id)
        val updated = state.saveWorkspace(canvas.assignApp("cell", app))

        assertEquals(2, state.workspaces.size)
        assertEquals(listOf(first.id, second.id), state.workspaces.map { it.id })
        assertEquals(3L, updated.modifiedSequence)
        assertEquals("First", updated.name)
        assertEquals(1, updated.appCount)
    }

    @Test fun idsAndModifiedSequenceAreDeterministic() {
        val state = viewModel()
        state.createWorkspace()
        val first = state.saveWorkspace(canvas)
        state.createWorkspace()
        val second = state.saveWorkspace(canvas)

        assertEquals("workspace-1", first.id)
        assertEquals("workspace-2", second.id)
        assertEquals(1L, first.modifiedSequence)
        assertEquals(2L, second.modifiedSequence)
    }

    @Test fun repeatedCollectorsDoNotResetLibraryState() {
        val state = savedState()
        val firstCollector = state.workspaces
        val recreatedCollector = state.workspaces

        assertSame(firstCollector, recreatedCollector)
        assertEquals("Work", recreatedCollector.single().name)
    }

    @Test fun editingWorkingCopyDoesNotMutateLibraryBeforeSave() {
        val library = savedState()
        val saved = library.workspaces.single()
        val designer = WorkspaceDesignerViewModel()
        designer.loadCanvas(library.beginEditingWorkspace(saved.id))
        designer.assignApp("cell", app)

        assertNull(library.workspaces.single().canvas.cells.single().app)

        library.saveWorkspace(designer.canvas)
        assertEquals(app, library.workspaces.single().canvas.cells.single().app)
    }

    @Test fun switchingWorkspaceAndCreatingNewResetDesignerHistory() {
        val library = savedState()
        val first = library.workspaces.single()
        library.createWorkspace()
        val second = library.saveWorkspace(canvas, "Second")
        val designer = WorkspaceDesignerViewModel()

        designer.loadCanvas(library.beginEditingWorkspace(first.id))
        designer.assignApp("cell", app)
        assertTrue(designer.canUndo)

        designer.loadCanvas(library.beginEditingWorkspace(second.id))
        assertFalse(designer.canUndo)
        assertFalse(designer.canRedo)

        designer.loadCanvas(library.createWorkspace())
        assertEquals(WorkspaceCanvas.singleCell(), designer.canvas)
        assertFalse(designer.canUndo)
    }

    @Test fun appCountIgnoresEmptyCells() {
        val twoCells = WorkspaceCanvas(
            listOf(
                WorkspaceCell("top", NormalizedBounds(0f, 0f, 1f, 0.5f), app),
                WorkspaceCell("bottom", NormalizedBounds(0f, 0.5f, 1f, 1f)),
            ),
        )
        val state = viewModel()
        state.createWorkspace()

        assertEquals(1, state.saveWorkspace(twoCells, "Two").appCount)
    }

    private fun savedState() = viewModel().also {
        it.createWorkspace()
        it.saveWorkspace(canvas, "Work")
    }

    private fun viewModel() = WorkspaceLibraryViewModel()

    private val canvas = WorkspaceCanvas.singleCell()
    private val app = AssignedApp("demo.app", "demo.MainActivity", "Demo")
}
