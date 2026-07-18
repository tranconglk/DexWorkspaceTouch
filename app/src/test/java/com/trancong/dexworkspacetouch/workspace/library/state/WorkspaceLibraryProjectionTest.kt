package com.trancong.dexworkspacetouch.workspace.library.state

import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.library.model.WorkspaceLibraryItem
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceLibraryProjectionTest {
    private val source = listOf(
        item("b", "beta", sequence = 2, created = 30, updated = 40),
        item("a", "Ánh xạ", sequence = 3, created = 10, updated = 50),
        item("c", "ALPHA", sequence = 1, created = 20, updated = 40),
    )

    @Test fun `empty query returns every item and source remains unchanged`() {
        val before = source.toList()
        assertEquals(3, projectWorkspaceLibrary(source, "  ", WorkspaceSortMode.RECENTLY_UPDATED).size)
        assertEquals(before, source)
    }

    @Test fun `search trims and ignores case including Vietnamese names`() {
        assertEquals(listOf("a"), projectWorkspaceLibrary(source, "  ÁNH  ", WorkspaceSortMode.NAME_ASCENDING).map { it.id })
        assertEquals(emptyList<String>(), projectWorkspaceLibrary(source, "missing", WorkspaceSortMode.NAME_ASCENDING).map { it.id })
    }

    @Test fun `all sort modes follow deterministic tie breaks`() {
        assertEquals(listOf("a", "b", "c"), ids(WorkspaceSortMode.RECENTLY_UPDATED))
        assertEquals(listOf("c", "b", "a"), ids(WorkspaceSortMode.NAME_ASCENDING))
        assertEquals(listOf("a", "b", "c"), ids(WorkspaceSortMode.NAME_DESCENDING))
        assertEquals(listOf("b", "c", "a"), ids(WorkspaceSortMode.CREATED_NEWEST))
        assertEquals(listOf("a", "c", "b"), ids(WorkspaceSortMode.CREATED_OLDEST))
    }

    @Test fun `same case-insensitive name uses updated then id`() {
        val tied = listOf(
            item("b", "Name", 1, 1, 5),
            item("a", "name", 1, 1, 5),
            item("c", "NAME", 1, 1, 6),
        )
        assertEquals(listOf("c", "a", "b"), projectWorkspaceLibrary(tied, "", WorkspaceSortMode.NAME_ASCENDING).map { it.id })
    }

    @Test fun `search and sort compose in one projection`() {
        val values = source + item("d", "beta two", 4, 40, 60)
        assertEquals(listOf("d", "b"), projectWorkspaceLibrary(values, "beta", WorkspaceSortMode.CREATED_NEWEST).map { it.id })
    }

    private fun ids(mode: WorkspaceSortMode) = projectWorkspaceLibrary(source, "", mode).map { it.id }
    private fun item(id: String, name: String, sequence: Long, created: Long, updated: Long) =
        WorkspaceLibraryItem(id, name, WorkspaceCanvas.singleCell(), sequence, created, updated)
}
