package com.cortex.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cortex.app.ui.theme.*

/**
 * Animated Cortex orb — pulsing blue/cyan/green gradient sphere.
 * The signature visual identity of the app.
 */
@Composable
fun CortexOrb(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    pulse: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "orb")
    val scale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbScale"
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbRotate"
    )

    val actualScale = if (pulse) scale else 1f

    Box(
        modifier = modifier
            .size(size * actualScale)
            .clip(RoundedCornerShape(50))
            .background(
                Brush.sweepGradient(
                    colors = listOf(AccentBlue, AccentCyan, AccentGreen, AccentBlue)
                )
            )
    )
}

/**
 * Static orb (no animation) for places where motion would be distracting.
 */
@Composable
fun StaticOrb(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    gradient: Brush = OrbGradient
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(50))
            .background(gradient)
    )
}
