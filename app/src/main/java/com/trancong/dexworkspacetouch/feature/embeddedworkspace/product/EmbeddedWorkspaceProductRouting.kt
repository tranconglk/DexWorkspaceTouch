package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

class EmbeddedWorkspaceProductRouting(
    private val gate: EmbeddedProductRunGate,
    private val navigateEmbedded: (String) -> Unit,
) {
    fun openClassic(dispatch: () -> Unit): Boolean = gate.tryDispatchClassic(dispatch)

    fun openEmbedded(workspaceId: String): Boolean {
        if (!gate.canEnterEmbedded()) return false
        navigateEmbedded(workspaceId)
        return true
    }
}
