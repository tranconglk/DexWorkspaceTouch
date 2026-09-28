package com.trancong.dexworkspacetouch.workspace.execution.embedded

import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds

data class EmbeddedWorkspacePlan(
    val workspaceId: String,
    val workspaceName: String,
    val items: List<EmbeddedWorkspacePlanItem>,
) {
    init {
        require(workspaceId.isNotBlank()) { "workspaceId must not be blank" }
        require(workspaceName.isNotBlank()) { "workspaceName must not be blank" }
        require(items.isNotEmpty()) { "items must not be empty" }
        require(items.map(EmbeddedWorkspacePlanItem::sourceCellId).distinct().size == items.size) {
            "source cell ids must be unique"
        }
        require(items.map(EmbeddedWorkspacePlanItem::order).distinct().size == items.size) {
            "item orders must be unique"
        }
    }
}

data class EmbeddedWorkspacePlanItem(
    val sourceCellId: String,
    val packageName: String,
    val componentName: String,
    val normalizedBounds: NormalizedBounds,
    val order: Int,
) {
    init {
        require(sourceCellId.isNotBlank()) { "sourceCellId must not be blank" }
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(componentName.isNotBlank()) { "componentName must not be blank" }
        require(order >= 0) { "order must be non-negative" }
    }
}
