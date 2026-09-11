package com.trancong.dexworkspacetouch.ui.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.trancong.dexworkspacetouch.ui.theme.DwtAccent
import com.trancong.dexworkspacetouch.ui.theme.DwtError
import com.trancong.dexworkspacetouch.ui.theme.DwtOnSurfaceMuted
import com.trancong.dexworkspacetouch.ui.theme.DwtOutline
import com.trancong.dexworkspacetouch.ui.theme.DwtPrimary
import com.trancong.dexworkspacetouch.ui.theme.DwtSuccess
import com.trancong.dexworkspacetouch.ui.theme.DwtSurfaceAlt
import com.trancong.dexworkspacetouch.ui.theme.DwtWarning

object DwtMotion {
    const val FastMillis = 150
    const val NormalMillis = 200
    val StandardEasing = FastOutSlowInEasing
}

object DwtStateColors {
    val Normal: Color @Composable get() = DwtSurfaceAlt
    val Pressed: Color @Composable get() = DwtPrimary.copy(alpha = 0.18f)
    val Focused: Color @Composable get() = DwtPrimary
    val Selected: Color @Composable get() = DwtPrimary
    val Disabled: Color @Composable get() = DwtOnSurfaceMuted.copy(alpha = 0.38f)
    val Error: Color @Composable get() = DwtError
    val Warning: Color @Composable get() = DwtWarning
    val Success: Color @Composable get() = DwtSuccess
    val Accent: Color @Composable get() = DwtAccent
}

/** ARGB bridge for Android View surfaces such as Floating Dock. */
object DwtViewColors {
    val Background = 0xFF070A0F.toInt()
    val Surface = 0xFF111720.toInt()
    val SurfaceAlt = 0xFF182231.toInt()
    val Primary = 0xFF4DA3FF.toInt()
    val OnSurface = 0xFFF3F7FC.toInt()
    val Outline = 0xFF2A394A.toInt()
}

object DwtPreviewTokens {
    val Surface = DwtSurfaceAlt
    val Border = DwtOutline
    val SelectedBorder = DwtPrimary
    val InnerGap = Spacing.XS
    val SmallIcon = Dimensions.WorkspaceSnapshotIconMinSize
    val MediumIcon = Dimensions.WorkspaceSnapshotIconMediumSize
    val LargeIcon = Dimensions.WorkspaceSnapshotIconLargeSize
}
