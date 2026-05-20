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
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

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
        animationSpec = tween(durationMillis = 1500, easing = LinearEasing),
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
        List(40) {
            ConfettiParticle(
                angleDegrees = Random.nextFloat() * 360f,
                speed = 200f + Random.nextFloat() * 200f,
                color = listOf(
                    Color(0xFF7AC794),
                    Color(0xFFF66561),
                    Color(0xFFC9A227),
                    Color(0xFF4A8FCB),
                ).random(),
                size = 4f + Random.nextFloat() * 4f,
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            particles.forEach { particle ->
                val angleRadians = Math.toRadians(particle.angleDegrees.toDouble()).toFloat()
                val travel = particle.speed * progress
                val gravity = 200f * progress * progress
                val x = center.x + travel * cos(angleRadians)
                val y = center.y + travel * sin(angleRadians) + gravity
                drawCircle(color = particle.color, radius = particle.size, center = Offset(x, y))
            }
        }
    }
}

private data class ConfettiParticle(
    val angleDegrees: Float,
    val speed: Float,
    val color: Color,
    val size: Float,
)
