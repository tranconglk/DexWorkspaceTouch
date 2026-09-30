package com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime

import android.view.Surface

class AndroidEmbeddedWorkspaceExecutionSurface(
    internal val surface: Surface,
    private val validityQuery: () -> Boolean = { surface.isValid },
) : EmbeddedWorkspaceExecutionSurface {
    override val isValid: Boolean
        get() = validityQuery()
}
