package com.cortex.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cortex.app.ui.theme.*

/**
 * High-performance animated Cortex orb.
 *
 * Performance strategy (critical for phones):
 *  - Single Canvas draw call instead of nested Box composables → fewer nodes
 *  - graphicsLayer { } block reads animated State INSIDE the lambda → only
 *    invalidates the GPU layer, never triggers recomposition of the composable
 *  - All drawing (sphere gradient + orbiting dot) in one drawScope pass
 *
 * This keeps a LazyColumn full of assistant-message orbs buttery smooth even
 * on mid-range devices, because each frame is just a layer transform + a
 * single invalidated draw, not a tree recomposition.
 */
@Composable
fun CortexOrb(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    pulse: Boolean = true,
    spin: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "orb")

    // Keep State objects (do NOT use `by` delegate) so we can read .value
    // inside the graphicsLayer / draw lambdas without recomposing.
    val scaleState = transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val rotationState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Container is larger than the orb to give the orbiting dot room.
    val containerSize = size * 1.25f

    // Colors captured once (stable across frames).
    val orbColors = remember {
        listOf(
            AccentBlue,
            AccentCyan,
            AccentGreen,
            AccentIndigo,
            AccentBlue
        )
    }

    Box(
        modifier = modifier
            .size(containerSize)
            // graphicsLayer block: reading State.value here only re-runs the
            // block (updates the GPU layer), it does NOT recompose this
            // composable. This is the key to smooth list performance.
            .graphicsLayer {
                val s = if (pulse) scaleState.value else 1f
                scaleX = s
                scaleY = s
                rotationZ = if (spin) rotationState.value else 0f
            }
    ) {
        Canvas(modifier = Modifier.size(containerSize)) {
            val w = this.size.width
            val h = this.size.height
            val center = Offset(w / 2f, h / 2f)
            val orbRadius = (this.size.minDimension * 0.4f) // = size/2 → diameter = size
            val dotRadius = orbRadius * 0.22f
            val dotOrbitRadius = orbRadius * 1.18f

            // Main orb sphere — sweep gradient (rotation handled by graphicsLayer
            // on the parent, so the gradient visually spins with the whole layer).
            drawCircle(
                brush = Brush.sweepGradient(orbColors, center),
                radius = orbRadius,
                center = center
            )

            // Inner highlight — gives the orb a glassy 3D feel.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(center.x - orbRadius * 0.3f, center.y - orbRadius * 0.3f),
                    radius = orbRadius * 0.7f
                ),
                radius = orbRadius,
                center = center
            )

            // Orbiting dot — drawn at top center, then the whole layer rotates.
            // Because graphicsLayer rotationZ is applied to the parent, the dot
            // appears to orbit. (No need for an inner rotate() when the layer
            // already spins; but we keep the draw static here so the gradient
            // + dot rotate together cleanly.)
            drawCircle(
                color = Color.White,
                radius = dotRadius,
                center = Offset(center.x, center.y - dotOrbitRadius)
            )
            // Tiny glow under the dot for depth.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(center.x, center.y - dotOrbitRadius),
                    radius = dotRadius * 2.2f
                ),
                radius = dotRadius * 2.2f,
                center = Offset(center.x, center.y - dotOrbitRadius)
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
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val r = this.size.minDimension / 2f
            drawCircle(brush = gradient, radius = r, center = center)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.3f), Color.Transparent),
                    center = Offset(center.x - r * 0.3f, center.y - r * 0.3f),
                    radius = r * 0.7f
                ),
                radius = r,
                center = center
            )
        }
    }
}


