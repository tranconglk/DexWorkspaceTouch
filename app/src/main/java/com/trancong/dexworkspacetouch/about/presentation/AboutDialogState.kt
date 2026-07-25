package com.trancong.dexworkspacetouch.about.presentation

data class AboutDialogState(
    val isOpen: Boolean = false,
    val copySuccessVisible: Boolean = false,
)

sealed interface AboutDialogEvent {
    data object Open : AboutDialogEvent
    data object Close : AboutDialogEvent
    data object CopyRequested : AboutDialogEvent
    data object CopySucceeded : AboutDialogEvent
    data object HostResized : AboutDialogEvent
}

sealed interface AboutDialogEffect {
    data object CopyDiagnostics : AboutDialogEffect
}

data class AboutDialogTransition(
    val state: AboutDialogState,
    val effect: AboutDialogEffect? = null,
)

fun reduceAboutDialog(
    state: AboutDialogState,
    event: AboutDialogEvent,
): AboutDialogTransition = when (event) {
    AboutDialogEvent.Open -> AboutDialogTransition(AboutDialogState(isOpen = true))
    AboutDialogEvent.Close -> AboutDialogTransition(AboutDialogState())
    AboutDialogEvent.CopyRequested -> AboutDialogTransition(
        state.copy(copySuccessVisible = false),
        AboutDialogEffect.CopyDiagnostics,
    )
    AboutDialogEvent.CopySucceeded -> AboutDialogTransition(state.copy(copySuccessVisible = true))
    AboutDialogEvent.HostResized -> AboutDialogTransition(state)
}
