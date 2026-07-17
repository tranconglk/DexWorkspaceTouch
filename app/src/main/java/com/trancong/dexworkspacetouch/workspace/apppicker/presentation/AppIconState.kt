package com.trancong.dexworkspacetouch.workspace.apppicker.presentation

import androidx.compose.ui.graphics.ImageBitmap
import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity

sealed interface AppIconState {
    data object Loading : AppIconState

    data class Ready(val image: ImageBitmap) : AppIconState

    data object Fallback : AppIconState
}

interface AppIconLoader {
    fun loadIcon(identity: AppIdentity): AppIconState
}
