package com.trancong.dexworkspacetouch.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class DwtStatusTone { Info, Success, Warning, Error }

@Composable
fun DwtStatusSurface(
    tone: DwtStatusTone,
    title: String,
    supportingText: String? = null,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val color = tone.color()
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = color.copy(alpha = 0.09f),
        border = BorderStroke(Dimensions.BorderDefault, color.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.M),
            horizontalArrangement = Arrangement.spacedBy(Spacing.M),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tone.symbol,
                color = color,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.size(32.dp).clip(CircleShape)
                    .background(color.copy(alpha = 0.14f)).padding(Spacing.XS),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.XS),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = color)
                supportingText?.takeIf(String::isNotBlank)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailingContent?.invoke()
        }
    }
}

@Composable
private fun DwtStatusTone.color(): Color = when (this) {
    DwtStatusTone.Info -> MaterialTheme.colorScheme.primary
    DwtStatusTone.Success -> DwtStateColors.Success
    DwtStatusTone.Warning -> DwtStateColors.Warning
    DwtStatusTone.Error -> MaterialTheme.colorScheme.error
}

private val DwtStatusTone.symbol: String
    get() = when (this) {
        DwtStatusTone.Info -> "i"
        DwtStatusTone.Success -> "✓"
        DwtStatusTone.Warning -> "!"
        DwtStatusTone.Error -> "×"
    }
