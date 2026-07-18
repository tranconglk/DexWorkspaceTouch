package com.trancong.dexworkspacetouch.workspace.library.state

import com.trancong.dexworkspacetouch.workspace.library.model.WorkspaceLibraryItem

internal fun projectWorkspaceLibrary(
    source: List<WorkspaceLibraryItem>,
    searchQuery: String,
    sortMode: WorkspaceSortMode,
): List<WorkspaceLibraryItem> {
    val query = searchQuery.trim()
    val filtered = if (query.isEmpty()) source else source.filter { it.name.contains(query, ignoreCase = true) }
    return filtered.sortedWith(sortMode.comparator())
}

private fun WorkspaceSortMode.comparator(): Comparator<WorkspaceLibraryItem> {
    val nameAscending = compareBy<WorkspaceLibraryItem> { it.name.lowercase() }
    val nameDescending = compareByDescending<WorkspaceLibraryItem> { it.name.lowercase() }
    return when (this) {
        WorkspaceSortMode.RECENTLY_UPDATED -> compareByDescending<WorkspaceLibraryItem> { it.updatedAtEpochMillis }
            .thenByDescending { it.modifiedSequence }.then(nameAscending).thenBy { it.id }
        WorkspaceSortMode.NAME_ASCENDING -> nameAscending
            .thenByDescending { it.updatedAtEpochMillis }.thenBy { it.id }
        WorkspaceSortMode.NAME_DESCENDING -> nameDescending
            .thenByDescending { it.updatedAtEpochMillis }.thenBy { it.id }
        WorkspaceSortMode.CREATED_NEWEST -> compareByDescending<WorkspaceLibraryItem> { it.createdAtEpochMillis }
            .then(nameAscending).thenBy { it.id }
        WorkspaceSortMode.CREATED_OLDEST -> compareBy<WorkspaceLibraryItem> { it.createdAtEpochMillis }
            .then(nameAscending).thenBy { it.id }
    }
}
