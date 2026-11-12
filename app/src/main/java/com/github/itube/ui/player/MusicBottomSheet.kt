package com.github.itube

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

const val BottomSheetExpandedAnchor = 2
const val BottomSheetCollapsedAnchor = 1
const val BottomSheetDismissedAnchor = 0

val SheetAnimationSpec: AnimationSpec<Dp> = spring(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
)

@Stable
class BottomSheetState(
    draggableState: DraggableState,
    private val coroutineScope: CoroutineScope,
    private val animatable: Animatable<Dp, AnimationVector1D>,
    private val onAnchorChanged: (Int) -> Unit,
    val collapsedBound: Dp,
) : DraggableState by draggableState {

    val dismissedBound: Dp
        get() = animatable.lowerBound ?: 0.dp

    val expandedBound: Dp
        get() = animatable.upperBound ?: 1000.dp

    val value: Dp by animatable.asState()

    val isDismissed by derivedStateOf {
        value <= (animatable.lowerBound ?: 0.dp) + 2.dp
    }

    val isCollapsed by derivedStateOf {
        kotlin.math.abs(value.value - collapsedBound.value) < 4f
    }

    val isExpanded by derivedStateOf {
        kotlin.math.abs(value.value - expandedBound.value) < 4f
    }

    val progress by derivedStateOf {
        val range = (expandedBound - collapsedBound).value
        if (range <= 0f) 0f else {
            ((value - collapsedBound).value / range).coerceIn(0f, 1f)
        }
    }

    fun collapse(animationSpec: AnimationSpec<Dp> = SheetAnimationSpec) {
        onAnchorChanged(BottomSheetCollapsedAnchor)
        coroutineScope.launch {
            animatable.animateTo(collapsedBound, animationSpec)
        }
    }

    fun expand(animationSpec: AnimationSpec<Dp> = SheetAnimationSpec) {
        onAnchorChanged(BottomSheetExpandedAnchor)
        coroutineScope.launch {
            animatable.animateTo(expandedBound, animationSpec)
        }
    }

    fun collapseSoft() = collapse(spring(stiffness = Spring.StiffnessMediumLow))
    fun expandSoft() = expand(spring(stiffness = Spring.StiffnessMediumLow))

    fun dismiss(animationSpec: AnimationSpec<Dp> = SheetAnimationSpec) {
        onAnchorChanged(BottomSheetDismissedAnchor)
        coroutineScope.launch {
            animatable.animateTo(dismissedBound, animationSpec)
        }
    }

    fun snapTo(target: Dp) {
        coroutineScope.launch {
            animatable.snapTo(target)
        }
    }

    fun performFling(velocity: Float, onDismiss: (() -> Unit)?) {
        if (velocity > 250f) {
            expand()
        } else if (velocity < -250f) {
            if (value <= collapsedBound + 12.dp) {
                dismiss()
                onDismiss?.invoke()
            } else {
                collapse()
            }
        } else {
            val l1 = (collapsedBound - dismissedBound) / 2 + dismissedBound
            val l2 = (expandedBound - collapsedBound) / 2 + collapsedBound

            when {
                value <= l1 -> {
                    dismiss()
                    onDismiss?.invoke()
                }
                value <= l2 -> collapse()
                else -> expand()
            }
        }
    }
}

@Composable
fun rememberBottomSheetState(
    dismissedBound: Dp,
    expandedBound: Dp,
    collapsedBound: Dp = dismissedBound,
    initialAnchor: Int = BottomSheetCollapsedAnchor,
): BottomSheetState {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    var previousAnchor by rememberSaveable { mutableIntStateOf(initialAnchor) }
    val animatable = remember {
        val startVal = when (initialAnchor) {
            BottomSheetExpandedAnchor -> expandedBound
            BottomSheetDismissedAnchor -> dismissedBound
            else -> collapsedBound
        }
        Animatable(startVal, Dp.VectorConverter)
    }

    return remember(dismissedBound, expandedBound, collapsedBound, coroutineScope) {
        val initialValue = when (previousAnchor) {
            BottomSheetExpandedAnchor -> expandedBound
            BottomSheetCollapsedAnchor -> collapsedBound
            BottomSheetDismissedAnchor -> dismissedBound
            else -> collapsedBound
        }

        animatable.updateBounds(dismissedBound.coerceAtMost(expandedBound), expandedBound)
        coroutineScope.launch {
            animatable.animateTo(initialValue, SheetAnimationSpec)
        }

        BottomSheetState(
            draggableState = DraggableState { delta ->
                coroutineScope.launch {
                    val newTarget = (animatable.value - with(density) { delta.toDp() })
                        .coerceIn(dismissedBound, expandedBound)
                    animatable.snapTo(newTarget)
                }
            },
            onAnchorChanged = { previousAnchor = it },
            coroutineScope = coroutineScope,
            animatable = animatable,
            collapsedBound = collapsedBound
        )
    }
}

/** Modifier that binds vertical sheet dragging to a specific component (e.g. mini player or full player header). */
fun Modifier.sheetDragGesture(
    state: BottomSheetState,
    onDismiss: (() -> Unit)? = null
): Modifier = this.pointerInput(state) {
    val velocityTracker = VelocityTracker()
    detectVerticalDragGestures(
        onVerticalDrag = { change, dragAmount ->
            velocityTracker.addPointerInputChange(change)
            state.dispatchRawDelta(dragAmount)
        },
        onDragCancel = {
            velocityTracker.resetTracking()
            state.snapTo(if (state.value < state.collapsedBound) state.dismissedBound else state.collapsedBound)
        },
        onDragEnd = {
            val velocity = -velocityTracker.calculateVelocity().y
            velocityTracker.resetTracking()
            state.performFling(velocity, onDismiss)
        }
    )
}

@Composable
fun MusicBottomSheet(
    state: BottomSheetState,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(
        topStart = if (!state.isExpanded) 20.dp else 0.dp,
        topEnd = if (!state.isExpanded) 20.dp else 0.dp
    ),
    onDismiss: (() -> Unit)? = null,
    collapsedContent: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!state.isCollapsed && !state.isDismissed) {
        BackHandler(onBack = { state.collapseSoft() })
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // ── Full Player Container (slides up and down with state.value) ──
        if (state.progress > 0.02f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset {
                        val y = (state.expandedBound - state.value)
                            .roundToPx()
                            .coerceAtLeast(0)
                        IntOffset(x = 0, y = y)
                    }
                    .sheetDragGesture(state, onDismiss)
                    .clip(shape)
                    .graphicsLayer {
                        alpha = ((state.progress - 0.02f) * 1.1f).coerceIn(0f, 1f)
                    },
                content = content
            )
        }

        // ── Mini Player Container (visible when collapsed or dragging below collapsedBound) ──
        if (state.progress < 0.98f && !state.isDismissed) {
            val miniYOffset = if (state.value < state.collapsedBound) {
                (state.collapsedBound - state.value)
            } else {
                0.dp
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset {
                        IntOffset(x = 0, y = miniYOffset.roundToPx())
                    }
                    .graphicsLayer {
                        alpha = (1f - state.progress * 3.5f).coerceIn(0f, 1f)
                    },
                content = collapsedContent
            )
        }
    }
}
