package com.trancong.dexworkspacetouch.workspace.librarytransfer

data class LibraryOutputActionState(
    val inProgress: Boolean = false,
)

data class LibraryOutputActionTransition(
    val state: LibraryOutputActionState,
    val shouldLaunch: Boolean,
)

fun beginLibraryOutputAction(
    state: LibraryOutputActionState,
): LibraryOutputActionTransition = if (state.inProgress) {
    LibraryOutputActionTransition(state, shouldLaunch = false)
} else {
    LibraryOutputActionTransition(
        LibraryOutputActionState(inProgress = true),
        shouldLaunch = true,
    )
}

fun finishLibraryOutputAction(): LibraryOutputActionState = LibraryOutputActionState()
