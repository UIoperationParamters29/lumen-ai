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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cortex.app.ui.theme.*

/**
 * Animated Cortex orb — single clean orb with a visible orbiting dot.
 * The dot physically orbits around the orb so rotation is OBVIOUS.
 */
@Composable
fun CortexOrb(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    pulse: Boolean = true,
    spin: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "orb")

    val scaleAnim by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val finalScale = if (pulse) scaleAnim else 1f
    val finalRotation = if (spin) rotation else 0f

    // Container — large enough for the orbiting dot
    val containerSize = size * 1.25f

    Box(modifier = modifier.size(containerSize * finalScale)) {

        // Main orb — solid gradient sphere (static, doesn't rotate)
        Box(
            modifier = Modifier
                .size(size * finalScale)
                .align(Alignment.Center)
                .clip(CircleShape)
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

        // Orbiting dot — rotating container holds dot at top, creating orbit
        Box(
            modifier = Modifier
                .size(containerSize * finalScale)
                .align(Alignment.Center)
                .rotate(finalRotation)
        ) {
            Box(
                modifier = Modifier
                    .size(size * 0.2f * finalScale)
                    .align(Alignment.TopCenter)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
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
            .clip(CircleShape)
            .background(gradient)
    )
}
