package com.trancong.dexworkspacetouch.workspace.designer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.trancong.dexworkspacetouch.workspace.designer.model.SplitDirection
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceDivider
import kotlin.math.roundToInt

@Composable
fun WorkspaceDividerView(
    divider: WorkspaceDivider,
    selected: Boolean,
    onClick: () -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onReset: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lineColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .semantics(mergeDescendants = false) {
                contentDescription = "Vách ngăn ${(divider.ratio * 100f).roundToInt()} phần trăm"
            }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.then(
                when (divider.direction) {
                    SplitDirection.VERTICAL -> Modifier.width(3.dp).fillMaxHeight()
                    SplitDirection.HORIZONTAL -> Modifier.fillMaxWidth().height(3.dp)
                },
            ).align(Alignment.Center),
        ) {
            Surface(color = lineColor, modifier = Modifier.fillMaxSize()) {}
        }
        if (selected) {
            DividerActionOverlay(
                divider = divider,
                onDecrease = onDecrease,
                onIncrease = onIncrease,
                onReset = onReset,
                onClearSelection = onClearSelection,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun DividerActionOverlay(
    divider: WorkspaceDivider,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onReset: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
        tonalElevation = 4.dp,
    ) {
        when (divider.direction) {
            SplitDirection.VERTICAL -> Column(
                modifier = Modifier.fillMaxSize().padding(4.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                DividerButtons(divider, onDecrease, onIncrease, onReset, onClearSelection)
            }
            SplitDirection.HORIZONTAL -> Row(
                modifier = Modifier.fillMaxSize().padding(4.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DividerButtons(divider, onDecrease, onIncrease, onReset, onClearSelection)
            }
        }
    }
}

@Composable
private fun DividerButtons(
    divider: WorkspaceDivider,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onReset: () -> Unit,
    onClearSelection: () -> Unit,
) {
    DividerButton("−", divider.ratio > MIN_RATIO, onDecrease)
    DividerButton("+", divider.ratio < MAX_RATIO, onIncrease)
    DividerButton("${(divider.ratio * 100f).roundToInt()}%", true, onReset)
    DividerButton("Bỏ chọn", true, onClearSelection)
}

@Composable
private fun DividerButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 56.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
    ) {
        Text(label)
    }
}

private const val MIN_RATIO = 0.2f
private const val MAX_RATIO = 0.8f
