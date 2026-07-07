package com.cortex.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cortex.app.ui.theme.*

/**
 * Animated Cortex orb — pulsing + spinning with a visible rotating ring.
 * The signature visual identity of the app.
 */
@Composable
fun CortexOrb(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    pulse: Boolean = true,
    spin: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "orb")
    val scale by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbScale"
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbRotate"
    )
    val ringRotation by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringRotate"
    )

    val actualScale = if (pulse) scale else 1f

    Box(modifier = modifier.size(size * 1.3f * actualScale)) {
        // Outer rotating ring — VISIBLE gradient border that spins
        Box(
            modifier = Modifier
                .size(size * 1.3f)
                .graphicsLayer { rotationZ = if (spin) ringRotation else 0f }
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            AccentCyan.copy(alpha = 0.6f),
                            Color.Transparent,
                            AccentBlue.copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Inner orb — solid gradient sphere that pulses
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    this.rotationZ = if (spin) rotation else 0f
                }
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.sweepGradient(
                        colors = listOf(
                            AccentBlue,
                            AccentCyan,
                            AccentGreen,
                            AccentIndigo,
                            AccentBlue
                        )
                    )
                )
        )

        // Highlight dot — orbits around to make rotation VISIBLE
        Box(
            modifier = Modifier
                .size(size * 0.15f)
                .graphicsLayer {
                    this.rotationZ = if (spin) rotation else 0f
                    this.translationY = -size.toPx() * 0.4f
                }
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.9f))
        )
    }
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
