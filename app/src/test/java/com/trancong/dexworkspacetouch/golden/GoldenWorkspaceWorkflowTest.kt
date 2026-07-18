package com.trancong.dexworkspacetouch.golden

import com.trancong.dexworkspacetouch.platform.launch.android.AndroidWorkspaceLauncher
import com.trancong.dexworkspacetouch.platform.launch.android.SingleAppLaunchResult
import com.trancong.dexworkspacetouch.platform.launch.android.WorkspaceLaunchLogger
import com.trancong.dexworkspacetouch.workspace.apppicker.model.toStableKey
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.AppPickerViewModel
import com.trancong.dexworkspacetouch.workspace.designer.model.SplitDirection
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvasValidator
import com.trancong.dexworkspacetouch.workspace.designer.state.WorkspaceDesignerStateHolder
import com.trancong.dexworkspacetouch.workspace.designer.state.WorkspaceDesignerViewModel
import com.trancong.dexworkspacetouch.workspace.launcher.WorkspaceLaunchRequestFactory
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchFailure
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchFailureReason
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTargetResult
import com.trancong.dexworkspacetouch.workspace.launcher.model.LaunchReadiness
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchResult
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.WorkspaceLaunchUiState
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.summaryMessage
import com.trancong.dexworkspacetouch.workspace.library.state.WorkspaceLibraryViewModel
import com.trancong.dexworkspacetouch.workspace.templates.WorkspaceTemplateCatalog
import com.trancong.dexworkspacetouch.workspace.templates.WorkspaceTemplateCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoldenWorkspaceWorkflowTest {
    @Test fun `template from every rendered category assigns saves recreates and is ready within five cells`() {
        val catalog = WorkspaceTemplateCatalog.default()
        val representatives = mapOf(
            WorkspaceTemplateCategory.BASIC to "four-grid",
            WorkspaceTemplateCategory.LEFT_RIGHT to "left-large-grid-right",
            WorkspaceTemplateCategory.TOP_BOTTOM to "top-large-four-bottom",
        )
        representatives.forEach { (category, id) ->
            val repository = FakeWorkspaceRepository()
            val library = library(repository)
            val template = catalog.find(id)!!
            assertEquals(category, template.category)
            val designer = WorkspaceDesignerStateHolder(template.factory())
            designer.canvas.cells.forEachIndexed { index, cell ->
                designer.assignApp(
                    cell.id,
                    if (index % 2 == 0) GoldenWorkspaceFixtures.mapsAssigned else GoldenWorkspaceFixtures.musicAssigned,
                )
            }
            library.saveWorkspace(designer.canvas, "Golden ${category.name}")

            val saved = library(repository).workspaces.single()
            assertTrue(saved.canvas.cells.size in 1..5)
            val readiness = WorkspaceLaunchRequestFactory(
                FakeInstalledAppCatalog(GoldenWorkspaceFixtures.installedApps),
            ).create(saved.id, saved.name, saved.canvas)
            assertTrue(readiness is LaunchReadiness.Ready)
        }
    }

    @Test fun `five cell direct activation assignment save and recreation are launch ready`() {
        val repository = FakeWorkspaceRepository()
        val library = library(repository)
        val designer = WorkspaceDesignerViewModel()
        designer.loadCanvas(WorkspaceTemplateCatalog.default().find("top-large-four-bottom")!!.factory())

        designer.canvas.cells.forEachIndexed { index, cell ->
            val activation = designer.activateCell(cell.id)
            assertEquals(cell.id, activation?.cellId)
            designer.assignApp(
                cell.id,
                if (index % 2 == 0) GoldenWorkspaceFixtures.mapsAssigned else GoldenWorkspaceFixtures.musicAssigned,
            )
            designer.onAppPickerClosed()
        }
        assertEquals(5, designer.summary.assignedAppCount)
        library.saveWorkspace(designer.canvas, "Direct activation")

        val recreated = library(repository).workspaces.single()
        val readiness = WorkspaceLaunchRequestFactory(
            FakeInstalledAppCatalog(GoldenWorkspaceFixtures.installedApps),
        ).create(recreated.id, recreated.name, recreated.canvas)
        assertTrue(readiness is LaunchReadiness.Ready)
    }

    @Test fun `create split assign save and process recreation preserve final canvas`() {
        val repository = FakeWorkspaceRepository()
        val library = library(repository)
        val designer = WorkspaceDesignerStateHolder(library.createWorkspace())
        assertEquals(1, designer.canvas.cells.size)
        assertEquals(0, repository.countBlocking())

        designer.selectCell("cell")
        designer.splitSelectedCell(SplitDirection.VERTICAL)
        designer.splitSelectedCell(SplitDirection.HORIZONTAL)
        designer.assignApp(designer.canvas.cells.first().id, GoldenWorkspaceFixtures.mapsAssigned)
        assertTrue(WorkspaceCanvasValidator().validate(designer.canvas).isEmpty())

        library.saveWorkspace(designer.canvas, "Golden")
        assertEquals(1, repository.values.size)
        assertEquals(designer.canvas, repository.values.single().canvas)

        val recreated = library(repository)
        assertEquals(designer.canvas, recreated.workspaces.single().canvas)
    }

    @Test fun `direct pin survives recreation and unpin returns workspace to regular section`() {
        val repository = FakeWorkspaceRepository()
        val library = library(repository)
        library.saveWorkspace(GoldenWorkspaceFixtures.oneEmpty(), "Pinned Golden")
        library.setWorkspacePinned("golden-workspace", true)

        val recreated = library(repository)
        assertEquals(listOf("golden-workspace"), recreated.pinnedWorkspaces.map { it.id })
        assertTrue(repository.values.single().isPinned)

        recreated.setWorkspacePinned("golden-workspace", false)
        assertTrue(recreated.pinnedWorkspaces.isEmpty())
        assertEquals(listOf("golden-workspace"), recreated.regularWorkspaces.map { it.id })
        assertFalse(repository.values.single().isPinned)
    }

    @Test fun `edit working copy cancel and save obey repository boundary`() {
        val repository = FakeWorkspaceRepository()
        val library = library(repository)
        library.saveWorkspace(GoldenWorkspaceFixtures.assigned(), "Original")
        library.finishEditing()
        val persisted = repository.values.single()

        val designer = WorkspaceDesignerStateHolder(library.beginEditingWorkspace(persisted.id))
        designer.selectCell("cell")
        designer.splitSelectedCell(SplitDirection.HORIZONTAL)
        assertNotEquals(persisted.canvas, designer.canvas)
        assertEquals(persisted.canvas, repository.values.single().canvas)

        library.finishEditing()
        assertEquals(0, repository.updates)
        library.beginEditingWorkspace(persisted.id)
        library.saveWorkspace(designer.canvas, "Edited")
        assertEquals(1, repository.values.size)
        assertEquals(1, repository.updates)
        assertEquals(designer.canvas, repository.values.single().canvas)
    }

    @Test fun `rename and delete preserve identity metadata rules and clear selection`() {
        val repository = FakeWorkspaceRepository()
        val library = library(repository)
        val saved = library.saveWorkspace(GoldenWorkspaceFixtures.assigned(), "Before")
        library.finishEditing()
        val before = repository.values.single()
        library.selectWorkspace(saved.id)
        library.renameWorkspace(saved.id, "After")
        val renamed = repository.values.single()
        assertEquals(before.id, renamed.id)
        assertEquals(before.canvas, renamed.canvas)
        assertTrue(renamed.updatedAtEpochMillis > before.updatedAtEpochMillis)
        assertTrue(renamed.modifiedSequence > before.modifiedSequence)
        library.deleteWorkspace(saved.id)
        assertTrue(repository.values.isEmpty())
        assertNull(library.selectedWorkspaceId)
    }

    @Test fun `duplicate persists assignments independently through recreation rename and delete`() {
        val repository = FakeWorkspaceRepository()
        val library = library(repository)
        val source = library.saveWorkspace(GoldenWorkspaceFixtures.assigned(), "Đi đường")
        library.finishEditing()
        library.duplicateWorkspace(source.id)

        val recreated = library(repository)
        assertEquals(2, recreated.workspaces.size)
        recreated.updateSearchQuery("bản sao")
        assertEquals(1, recreated.visibleWorkspaces.size)
        recreated.updateSortMode(com.trancong.dexworkspacetouch.workspace.library.state.WorkspaceSortMode.NAME_DESCENDING)
        val original = recreated.workspaces.first { it.id == source.id }
        val copy = recreated.workspaces.first { it.id != source.id }
        assertEquals(original.canvas, copy.canvas)
        assertEquals("Đi đường (Bản sao)", copy.name)
        recreated.renameWorkspace(copy.id, "Bản sao riêng")
        assertEquals(listOf(copy.id), recreated.visibleWorkspaces.map { it.id })
        assertEquals("Đi đường", repository.values.first { it.id == source.id }.name)
        recreated.deleteWorkspace(copy.id)
        assertEquals(listOf(source.id), repository.values.map { it.id })
    }

    @Test fun `designer undo redo remain draft only and save final redo state`() {
        val repository = FakeWorkspaceRepository()
        val library = library(repository)
        val designer = WorkspaceDesignerStateHolder(library.createWorkspace())
        designer.selectCell("cell")
        designer.assignApp("cell", GoldenWorkspaceFixtures.mapsAssigned)
        val assigned = designer.canvas
        assertTrue(designer.undo())
        assertTrue(repository.values.isEmpty())
        assertTrue(designer.redo())
        assertEquals(assigned, designer.canvas)
        library.saveWorkspace(designer.canvas, "History")
        assertEquals(assigned, repository.values.single().canvas)
    }

    @Test fun `app picker recognizes exact current identity and exposes bundle safe stable key`() {
        val catalog = FakeInstalledAppCatalog(GoldenWorkspaceFixtures.installedApps)
        val picker = AppPickerViewModel(catalog, selectedIdentity = GoldenWorkspaceFixtures.mapsIdentity)
        assertTrue(picker.isSelected(GoldenWorkspaceFixtures.mapsInstalled))
        assertFalse(picker.isSelected(GoldenWorkspaceFixtures.musicInstalled))
        val key = GoldenWorkspaceFixtures.mapsIdentity.toStableKey()
        assertTrue(key.isNotEmpty())
        assertEquals(key, GoldenWorkspaceFixtures.mapsIdentity.toStableKey())
    }

    @Test fun `ready launch request preserves canvas order bounds identities including duplicates`() {
        val catalog = FakeInstalledAppCatalog(GoldenWorkspaceFixtures.installedApps)
        val ready = WorkspaceLaunchRequestFactory(catalog).create(
            "id", "Duplicate", GoldenWorkspaceFixtures.duplicateIdentity(),
        ) as LaunchReadiness.Ready
        assertEquals(listOf(0, 1), ready.request.targets.map { it.order })
        assertEquals(GoldenWorkspaceFixtures.duplicateIdentity().cells.map { it.bounds }, ready.request.targets.map { it.bounds })
        assertEquals(listOf(GoldenWorkspaceFixtures.mapsIdentity, GoldenWorkspaceFixtures.mapsIdentity), ready.request.targets.map { it.identity })
    }

    @Test fun `empty and missing app readiness stop before launcher`() {
        val catalog = FakeInstalledAppCatalog(GoldenWorkspaceFixtures.installedApps)
        val factory = WorkspaceLaunchRequestFactory(catalog)
        assertTrue(factory.create("empty", "Empty", GoldenWorkspaceFixtures.oneEmpty()) is LaunchReadiness.EmptyCells)
        assertTrue(factory.create("missing", "Missing", GoldenWorkspaceFixtures.missingApp()) is LaunchReadiness.MissingApplications)
    }

    @Test fun `multi app launch follows target order and waits only between targets`() = runBlocking {
        val request = (WorkspaceLaunchRequestFactory(FakeInstalledAppCatalog(GoldenWorkspaceFixtures.installedApps))
            .create("id", "Two", GoldenWorkspaceFixtures.twoAssigned()) as LaunchReadiness.Ready).request
        val single = FakeSingleAppLauncher { target ->
            SingleAppLaunchResult.Success(AppLaunchTargetResult(target))
        }
        val delay = FakeLaunchDelay()
        val result = AndroidWorkspaceLauncher(
            single,
            launchDelay = delay,
            logger = WorkspaceLaunchLogger.None,
        ).launch(request)
        assertTrue(result is WorkspaceLaunchResult.Success)
        assertEquals(listOf(0, 1), single.targets.map { it.order })
        assertEquals(listOf(400L), delay.waits)
    }

    @Test fun `partial success maps user summary while display unavailable stops remaining sequence`() = runBlocking {
        val request = (WorkspaceLaunchRequestFactory(FakeInstalledAppCatalog(GoldenWorkspaceFixtures.installedApps))
            .create("id", "Two", GoldenWorkspaceFixtures.twoAssigned()) as LaunchReadiness.Ready).request
        val partial = WorkspaceLaunchResult.PartialSuccess(
            listOf(AppLaunchTargetResult(request.targets[0])),
            listOf(AppLaunchFailure(request.targets[1], AppLaunchFailureReason.APP_NOT_FOUND)),
        )
        val message = WorkspaceLaunchUiState.Completed("id", "Two", 2, partial).summaryMessage()
        assertTrue(message.contains("1/2"))

        val single = FakeSingleAppLauncher { target ->
            SingleAppLaunchResult.Failure(AppLaunchFailure(target, AppLaunchFailureReason.DISPLAY_UNAVAILABLE))
        }
        val result = AndroidWorkspaceLauncher(
            single,
            launchDelay = FakeLaunchDelay(),
            logger = WorkspaceLaunchLogger.None,
        ).launch(request)
        assertTrue(result is WorkspaceLaunchResult.Failure)
        result as WorkspaceLaunchResult.Failure
        assertEquals(2, result.failures.size)
        assertEquals(1, single.targets.size)
    }

    private fun library(repository: FakeWorkspaceRepository) = WorkspaceLibraryViewModel(
        repository = repository,
        clock = FakeClock(),
        idGenerator = FakeIdGenerator("golden-workspace", "golden-copy"),
        suppliedScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    )

    private fun FakeWorkspaceRepository.countBlocking() = runBlocking { count() }
}
