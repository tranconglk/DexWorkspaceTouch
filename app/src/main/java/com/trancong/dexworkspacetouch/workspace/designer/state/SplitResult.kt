package com.trancong.dexworkspacetouch.workspace.designer.state

sealed interface SplitResult {
    data class Success(val selectedCellId: String) : SplitResult
    data object NoSelection : SplitResult
    data object CellNotFound : SplitResult
    data object MaximumCellsReached : SplitResult
    data object CellTooSmall : SplitResult
}
