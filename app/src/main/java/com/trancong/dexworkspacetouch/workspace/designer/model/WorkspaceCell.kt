package com.trancong.dexworkspacetouch.workspace.designer.model

data class WorkspaceCell(
    val id: String,
    val bounds: NormalizedBounds,
    val app: AssignedApp? = null,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
    }
}
