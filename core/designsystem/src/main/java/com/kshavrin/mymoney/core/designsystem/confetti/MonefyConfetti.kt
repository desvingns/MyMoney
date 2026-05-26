package com.kshavrin.mymoney.core.designsystem.confetti

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val CONFETTI_DURATION_MS = 1500
private const val PARTICLE_COUNT = 80

@Composable
fun MonefyConfetti(
    show: Boolean,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {},
) {
    var trigger by remember { mutableStateOf(false) }
    LaunchedEffect(show) {
        if (show) {
            trigger = true
        }
    }
    val progress by animateFloatAsState(
        targetValue = if (trigger) 1f else 0f,
        animationSpec = tween(durationMillis = CONFETTI_DURATION_MS, easing = LinearEasing),
        label = "ConfettiProgress",
        finishedListener = {
            if (it == 1f) {
                trigger = false
                onFinished()
            }
        },
    )

    if (!trigger && progress == 0f) return

    val particles = remember {
        List(PARTICLE_COUNT) {
            ConfettiParticle(
                angleDegrees = -90f + (Random.nextFloat() - 0.5f) * 160f,
                speed = 600f + Random.nextFloat() * 900f,
                drift = (Random.nextFloat() - 0.5f) * 320f,
                spin = (Random.nextFloat() - 0.5f) * 1440f,
                color = CONFETTI_COLORS.random(),
                width = 8f + Random.nextFloat() * 8f,
                height = 4f + Random.nextFloat() * 6f,
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val origin = Offset(size.width / 2f, size.height * 0.35f)
            val gravity = size.height * 1.2f
            val alpha = (1f - progress).coerceIn(0f, 1f)
            particles.forEach { particle ->
                val angleRadians = Math.toRadians(particle.angleDegrees.toDouble()).toFloat()
                val x = origin.x + particle.speed * progress * cos(angleRadians) +
                    particle.drift * progress
                val y = origin.y + particle.speed * progress * sin(angleRadians) +
                    gravity * progress * progress
                rotate(degrees = particle.spin * progress, pivot = Offset(x, y)) {
                    drawRect(
                        color = particle.color.copy(alpha = alpha),
                        topLeft = Offset(x - particle.width / 2f, y - particle.height / 2f),
                        size = Size(particle.width, particle.height),
                    )
                }
            }
        }
    }
}

private val CONFETTI_COLORS = listOf(
    Color(0xFF7AC794),
    Color(0xFFF66561),
    Color(0xFFC9A227),
    Color(0xFF4A8FCB),
    Color(0xFFE0729A),
    Color(0xFF8E6FD8),
)

private data class ConfettiParticle(
    val angleDegrees: Float,
    val speed: Float,
    val drift: Float,
    val spin: Float,
    val color: Color,
    val width: Float,
    val height: Float,
)
