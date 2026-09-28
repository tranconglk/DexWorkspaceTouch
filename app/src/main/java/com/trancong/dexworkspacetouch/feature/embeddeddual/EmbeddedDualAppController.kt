package com.trancong.dexworkspacetouch.feature.embeddeddual

data class EmbeddedDualAppProofState(val aActive: Boolean = false, val bActive: Boolean = false)

class EmbeddedDualAppController {
    var state = EmbeddedDualAppProofState(); private set
    private var disposed = false
    fun updateA(active: Boolean) { state = state.copy(aActive = active) }
    fun updateB(active: Boolean) { state = state.copy(bActive = active) }
    fun dispose(): Boolean { if (disposed) return false; disposed = true; return true }
}
