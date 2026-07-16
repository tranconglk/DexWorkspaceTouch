package com.trancong.dexworkspacetouch.workspace.designer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.zIndex
import com.trancong.dexworkspacetouch.ui.design.DesignerColors
import com.trancong.dexworkspacetouch.ui.design.DesignerElevation
import com.trancong.dexworkspacetouch.ui.design.Dimensions
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets
import com.trancong.dexworkspacetouch.ui.design.ZLayers
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
    canvasWidthPx: Float,
    canvasHeightPx: Float,
    onDragStart: () -> Unit,
    onDragRatio: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lineColor = if (selected) DesignerColors.Selection else DesignerColors.Divider
    val percentage = (divider.ratio * 100f).roundToInt()
    val directionLabel = when (divider.direction) {
        SplitDirection.VERTICAL -> "dọc"
        SplitDirection.HORIZONTAL -> "ngang"
    }
    val currentDivider by rememberUpdatedState(divider)
    val currentCanvasWidth by rememberUpdatedState(canvasWidthPx)
    val currentCanvasHeight by rememberUpdatedState(canvasHeightPx)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragRatio by rememberUpdatedState(onDragRatio)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    Box(
        modifier = modifier
            .zIndex(ZLayers.Divider)
            .pointerInput(divider.id, divider.direction) {
                var startPointerCoordinate = 0f
                var accumulatedDrag = 0f
                detectDragGestures(
                    onDragStart = {
                        val activeDivider = currentDivider
                        startPointerCoordinate = when (activeDivider.direction) {
                            SplitDirection.VERTICAL -> activeDivider.position * currentCanvasWidth
                            SplitDirection.HORIZONTAL -> activeDivider.position * currentCanvasHeight
                        }
                        accumulatedDrag = 0f
                        currentOnDragStart()
                    },
                    onDragEnd = { currentOnDragEnd() },
                    onDragCancel = { currentOnDragCancel() },
                ) { change, dragAmount ->
                    change.consume()
                    val activeDivider = currentDivider
                    accumulatedDrag += when (activeDivider.direction) {
                        SplitDirection.VERTICAL -> dragAmount.x
                        SplitDirection.HORIZONTAL -> dragAmount.y
                    }
                    currentOnDragRatio(
                        pointerToDividerRatio(
                            direction = activeDivider.direction,
                            pointerX = if (activeDivider.direction == SplitDirection.VERTICAL) {
                                startPointerCoordinate + accumulatedDrag
                            } else {
                                0f
                            },
                            pointerY = if (activeDivider.direction == SplitDirection.HORIZONTAL) {
                                startPointerCoordinate + accumulatedDrag
                            } else {
                                0f
                            },
                            canvasWidth = currentCanvasWidth.coerceAtLeast(1f),
                            canvasHeight = currentCanvasHeight.coerceAtLeast(1f),
                            parentStart = activeDivider.parentStart,
                            parentEnd = activeDivider.parentEnd,
                            minRatio = Dimensions.MinCellRatio,
                            maxRatio = Dimensions.MaxCellRatio,
                        ),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Đường chia $directionLabel, $percentage phần trăm"
                    stateDescription = "$percentage phần trăm"
                }
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = lineColor,
                modifier = Modifier.then(
                    when (divider.direction) {
                        SplitDirection.VERTICAL -> Modifier
                            .width(Dimensions.DividerLineWidth)
                            .fillMaxHeight()
                        SplitDirection.HORIZONTAL -> Modifier
                            .fillMaxWidth()
                            .height(Dimensions.DividerLineWidth)
                    },
                ),
            ) {}
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
        color = DesignerColors.ActionOverlayBackground.copy(alpha = 0f),
        tonalElevation = DesignerElevation.DividerOverlay,
    ) {
        when (divider.direction) {
            SplitDirection.VERTICAL -> Column(
                modifier = Modifier.fillMaxSize().padding(Spacing.XS).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.XS, Alignment.CenterVertically),
            ) {
                DividerButtons(divider, onDecrease, onIncrease, onReset, onClearSelection)
            }
            SplitDirection.HORIZONTAL -> Row(
                modifier = Modifier.fillMaxSize().padding(Spacing.XS).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.XS, Alignment.CenterHorizontally),
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
        modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
        contentPadding = PaddingValues(horizontal = Spacing.S),
    ) {
        Text(label)
    }
}

private const val MIN_RATIO = Dimensions.MinCellRatio
private const val MAX_RATIO = Dimensions.MaxCellRatio
