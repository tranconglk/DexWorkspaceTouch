package com.trancong.dexworkspacetouch.workspace.execution.embedded.layout

sealed interface PaneTouchMappingResult {
    data class Mapped(val x: Float, val y: Float) : PaneTouchMappingResult
    data object Rejected : PaneTouchMappingResult
}

class EmbeddedPaneTouchMapper {
    fun map(
        x: Float,
        y: Float,
        hostWidth: Int,
        hostHeight: Int,
        guestWidth: Int,
        guestHeight: Int,
    ): PaneTouchMappingResult {
        if (!x.isFinite() || !y.isFinite() || hostWidth <= 0 || hostHeight <= 0 ||
            guestWidth <= 0 || guestHeight <= 0
        ) return PaneTouchMappingResult.Rejected
        return PaneTouchMappingResult.Mapped(
            (x.toDouble() * guestWidth / hostWidth).coerceIn(0.0, (guestWidth - 1).toDouble()).toFloat(),
            (y.toDouble() * guestHeight / hostHeight).coerceIn(0.0, (guestHeight - 1).toDouble()).toFloat(),
        )
    }
}
