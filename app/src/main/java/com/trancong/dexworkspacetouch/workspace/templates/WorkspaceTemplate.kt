package com.trancong.dexworkspacetouch.workspace.templates

import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas

enum class WorkspaceTemplateCategory {
    BASIC,
    COLUMNS,
    ROWS,
    GRID,
    SIDEBAR,
}

class WorkspaceTemplate internal constructor(
    val id: String,
    val name: String,
    val description: String,
    val category: WorkspaceTemplateCategory,
    val previewId: String,
    private val canvasFactory: () -> WorkspaceCanvas,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(name.isNotBlank()) { "name must not be blank" }
        require(description.isNotBlank()) { "description must not be blank" }
        require(previewId.isNotBlank()) { "previewId must not be blank" }
    }

    fun factory(): WorkspaceCanvas = canvasFactory()
}
