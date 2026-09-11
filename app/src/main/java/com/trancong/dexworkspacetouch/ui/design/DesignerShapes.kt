package com.trancong.dexworkspacetouch.ui.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val DwtShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

object DesignerShapes {
    val Workspace = DwtShapes.medium
    val Cell = RoundedCornerShape(6.dp)
    val DividerFeedback = RoundedCornerShape(percent = 50)
}
