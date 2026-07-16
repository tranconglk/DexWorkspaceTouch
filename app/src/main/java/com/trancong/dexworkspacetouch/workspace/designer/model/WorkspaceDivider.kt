package com.trancong.dexworkspacetouch.workspace.designer.model

data class WorkspaceDivider(
    val id: String,
    val direction: SplitDirection,
    val position: Float,
    val start: Float,
    val end: Float,
    val ratio: Float,
    internal val firstCellIds: Set<String>,
    internal val secondCellIds: Set<String>,
) {
    init {
        require(id.isNotBlank()) { "Divider id must not be blank" }
        require(position in 0f..1f) { "Divider position must be normalized" }
        require(start in 0f..<end && end in 0f..1f) { "Divider span must be normalized" }
        require(ratio in 0f..1f) { "Divider ratio must be normalized" }
        require(firstCellIds.isNotEmpty() && secondCellIds.isNotEmpty()) {
            "Divider must have cells on both sides"
        }
    }
}
