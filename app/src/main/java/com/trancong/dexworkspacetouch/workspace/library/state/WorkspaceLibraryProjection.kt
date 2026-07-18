package com.trancong.dexworkspacetouch.workspace.library.state

import com.trancong.dexworkspacetouch.workspace.library.model.WorkspaceLibraryItem

internal fun projectWorkspaceLibrary(
    source: List<WorkspaceLibraryItem>,
    searchQuery: String,
    sortMode: WorkspaceSortMode,
): List<WorkspaceLibraryItem> = projectWorkspaceLibrarySections(source, searchQuery, sortMode).all

internal data class WorkspaceLibrarySections(
    val pinned: List<WorkspaceLibraryItem>,
    val regular: List<WorkspaceLibraryItem>,
) {
    val all: List<WorkspaceLibraryItem> get() = pinned + regular
}

internal fun projectWorkspaceLibrarySections(
    source: List<WorkspaceLibraryItem>,
    searchQuery: String,
    sortMode: WorkspaceSortMode,
): WorkspaceLibrarySections {
    val query = searchQuery.trim()
    val filtered = if (query.isEmpty()) source else source.filter { it.name.contains(query, ignoreCase = true) }
    val (pinned, regular) = filtered.partition(WorkspaceLibraryItem::isPinned)
    val comparator = sortMode.comparator()
    return WorkspaceLibrarySections(pinned.sortedWith(comparator), regular.sortedWith(comparator))
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
