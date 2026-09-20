package com.kshavrin.mymoney.core.designsystem.spotlight

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.kshavrin.mymoney.core.ui.theme.LocalMotion
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.spotlightCard
import com.kshavrin.mymoney.core.ui.theme.spotlightCardMaxWidth
import com.kshavrin.mymoney.core.ui.theme.spotlightCutoutCornerRadius
import com.kshavrin.mymoney.core.ui.theme.spotlightCutoutPadding
import com.kshavrin.mymoney.core.ui.theme.spotlightCutoutRing
import com.kshavrin.mymoney.core.ui.theme.spotlightCutoutRingStroke
import com.kshavrin.mymoney.core.ui.theme.spotlightScrim
import kotlinx.coroutines.launch

data class SpotlightCutout(
    val key: Any,
    val shape: SpotlightShape,
)

private fun Modifier.pointerBlocker(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}

@Composable
fun SpotlightOverlay(
    registry: SpotlightTargetRegistry,
    cutout: SpotlightCutout?,
    stepTitle: String,
    card: @Composable () -> Unit,
    skipLabel: String,
    primaryLabel: String,
    onSkip: () -> Unit,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val motion = LocalMotion.current

    val targetBoundsPx: Rect? = if (cutout != null) registry[cutout.key] else null
    val targetCutoutPx: Rect? = if (targetBoundsPx != null && cutout != null) {
        val paddingPx = with(density) { Spacing.spotlightCutoutPadding.toPx() }
        cutoutRect(targetBoundsPx, cutout.shape, paddingPx)
    } else null

    val animLeft = remember { Animatable(0f) }
    val animTop = remember { Animatable(0f) }
    val animRight = remember { Animatable(0f) }
    val animBottom = remember { Animatable(0f) }
    var animInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(targetCutoutPx) {
        val target = targetCutoutPx ?: return@LaunchedEffect
        if (!animInitialized) {
            animInitialized = true
            animLeft.snapTo(target.left)
            animTop.snapTo(target.top)
            animRight.snapTo(target.right)
            animBottom.snapTo(target.bottom)
        } else {
            val spec = tween<Float>(motion.durationMedium, easing = motion.easeStandard)
            launch { animLeft.animateTo(target.left, spec) }
            launch { animTop.animateTo(target.top, spec) }
            launch { animRight.animateTo(target.right, spec) }
            launch { animBottom.animateTo(target.bottom, spec) }
        }
    }

    val activeCutoutPx: Rect? = if (animInitialized) {
        Rect(animLeft.value, animTop.value, animRight.value, animBottom.value)
    } else null

    var cardHeightPx by remember { mutableIntStateOf(0) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                isTraversalGroup = true
                paneTitle = stepTitle
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        val containerHeightPx = constraints.maxHeight.toFloat()

        val scrimbColor = MaterialTheme.colorScheme.spotlightScrim
        val ringColor = MaterialTheme.colorScheme.spotlightCutoutRing
        val ringStrokePx = with(density) { Spacing.spotlightCutoutRingStroke.toPx() }
        val cornerRadiusPx = with(density) { Spacing.spotlightCutoutCornerRadius.toPx() }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            drawRect(color = scrimbColor)
            val c = activeCutoutPx ?: return@Canvas
            when (cutout?.shape) {
                SpotlightShape.Circle -> {
                    val radius = c.width / 2f
                    val center = Offset(c.center.x, c.center.y)
                    drawCircle(color = scrimbColor, center = center, radius = radius, blendMode = BlendMode.Clear)
                    drawCircle(color = ringColor, center = center, radius = radius + ringStrokePx / 2f, style = Stroke(ringStrokePx))
                }
                SpotlightShape.RoundedRect -> {
                    drawRoundRect(
                        color = scrimbColor,
                        topLeft = c.topLeft,
                        size = c.size,
                        cornerRadius = CornerRadius(cornerRadiusPx),
                        blendMode = BlendMode.Clear,
                    )
                    drawRoundRect(
                        color = ringColor,
                        topLeft = c.topLeft.copy(
                            x = c.left - ringStrokePx / 2f,
                            y = c.top - ringStrokePx / 2f,
                        ),
                        size = c.size.copy(
                            width = c.width + ringStrokePx,
                            height = c.height + ringStrokePx,
                        ),
                        cornerRadius = CornerRadius(cornerRadiusPx + ringStrokePx / 2f),
                        style = Stroke(ringStrokePx),
                    )
                }
                null -> Unit
            }
        }

        if (activeCutoutPx != null) {
            val cutL: Dp = with(density) { activeCutoutPx.left.toDp() }
            val cutT: Dp = with(density) { activeCutoutPx.top.toDp() }
            val cutR: Dp = with(density) { activeCutoutPx.right.toDp() }
            val cutB: Dp = with(density) { activeCutoutPx.bottom.toDp() }
            val containerW = maxWidth
            val containerH = maxHeight

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(cutT.coerceAtLeast(0.dp))
                    .pointerBlocker(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height((containerH - cutB).coerceAtLeast(0.dp))
                    .offset(y = cutB)
                    .pointerBlocker(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(cutL.coerceAtLeast(0.dp))
                    .height((cutB - cutT).coerceAtLeast(0.dp))
                    .offset(y = cutT)
                    .pointerBlocker(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width((containerW - cutR).coerceAtLeast(0.dp))
                    .height((cutB - cutT).coerceAtLeast(0.dp))
                    .offset(x = cutR, y = cutT)
                    .pointerBlocker(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().pointerBlocker())
        }

        val placement = if (activeCutoutPx != null) {
            cardPlacement(activeCutoutPx, containerHeightPx, cardHeightPx.toFloat())
        } else CardPlacement.Below

        val cardTopDp: Dp = when {
            activeCutoutPx == null -> Spacing.xl
            placement == CardPlacement.Above -> {
                val cutTopDp = with(density) { activeCutoutPx.top.toDp() }
                val cardH = with(density) { cardHeightPx.toDp() }
                (cutTopDp - cardH - Spacing.s).coerceAtLeast(Spacing.s)
            }
            else -> with(density) { activeCutoutPx.bottom.toDp() } + Spacing.s
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = Spacing.spotlightCardMaxWidth)
                .fillMaxWidth()
                .offset(y = cardTopDp)
                .onSizeChanged { cardHeightPx = it.height },
        ) {
            Surface(
                shape = MaterialTheme.shapes.spotlightCard,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = Spacing.xs,
            ) {
                Box(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.l),
                ) {
                    card()
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onSkip,
                modifier = Modifier.heightIn(min = Spacing.minimumTouchTargetSize),
            ) {
                Text(text = skipLabel, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onPrimary,
                modifier = Modifier.heightIn(min = Spacing.minimumTouchTargetSize),
            ) {
                Text(text = primaryLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
