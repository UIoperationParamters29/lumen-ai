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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SweepGradient
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cortex.app.ui.theme.*

/**
 * High-performance animated Cortex orb — "plasma core" design.
 *
 * Visual design:
 *  - Soft outer glow (static)
 *  - Main orb body: radial gradient sphere (deep blue core → cyan rim) for depth
 *  - Rotating "plasma streak": a sweep-gradient overlay with one bright
 *    cyan→white arc. As the layer rotates, the streak visibly travels around
 *    the orb's surface — this is the visible spin cue. No separate white ball.
 *  - Static glassy highlight (top-left) for a 3D feel
 *  - Gentle breathing pulse (scale)
 *
 * Performance strategy (critical for phones):
 *  - Single Canvas draw call instead of nested Box composables → fewer nodes
 *  - graphicsLayer { } reads animated State INSIDE the lambda → only
 *    invalidates the GPU layer (for scale + outer rotation), never triggers
 *    recomposition of the composable
 *  - The plasma streak rotation uses drawScope.rotate { } reading the same
 *    State — draw lambdas re-execute on state reads without recomposing
 */
@Composable
fun CortexOrb(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    pulse: Boolean = true,
    spin: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "orb")

    val scaleState = transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val rotationState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Container slightly larger than orb to fit the soft glow.
    val containerSize = size * 1.15f

    // Pre-capture stable color stops (don't realloc per frame).
    val bodyColors = remember {
        listOf(
            Color(0xFF0A1A33),  // deep core
            Color(0xFF1E3A6B),  // mid blue
            AccentBlue,         // bright blue
            AccentCyan          // cyan rim
        )
    }
    // Sweep gradient stops for the plasma streak — one bright comet arc,
    // rest transparent. Reading these is cheap.
    val streakColors = remember {
        listOf(
            Color.Transparent,
            Color.Transparent,
            Color.Transparent,
            AccentCyan.copy(alpha = 0.15f),
            Color.White.copy(alpha = 0.85f),   // bright peak
            AccentCyan.copy(alpha = 0.55f),
            AccentBlue.copy(alpha = 0.15f),
            Color.Transparent,
            Color.Transparent
        )
    }

    Box(
        modifier = modifier
            .size(containerSize)
            .graphicsLayer {
                val s = if (pulse) scaleState.value else 1f
                scaleX = s
                scaleY = s
                // Outer layer rotation — rotates the plasma streak around.
                rotationZ = if (spin) rotationState.value else 0f
            }
    ) {
        Canvas(modifier = Modifier.size(containerSize)) {
            val w = this.size.width
            val h = this.size.height
            val center = Offset(w / 2f, h / 2f)
            val orbRadius = this.size.minDimension * 0.43f

            // 1. Soft outer glow — atmosphere around the orb.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AccentBlue.copy(alpha = 0.28f), Color.Transparent),
                    center = center,
                    radius = orbRadius * 1.35f
                ),
                radius = orbRadius * 1.35f,
                center = center
            )

            // 2. Main orb body — radial gradient sphere (deep core → cyan rim).
            //    This gives the orb a sense of depth and volume.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = bodyColors,
                    center = center,
                    radius = orbRadius
                ),
                radius = orbRadius,
                center = center
            )

            // 3. Rotating plasma streak — sweep gradient with one bright arc.
            //    The parent graphicsLayer rotation spins this around the orb's
            //    surface, creating a visible "comet tail" / plasma motion.
            //    Drawn ON TOP of the body so the streak is clearly visible.
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = streakColors,
                    center = center
                ),
                radius = orbRadius,
                center = center
            )

            // 4. Static glassy highlight — top-left, gives 3D glass feel.
            //    Does NOT rotate (drawn after, in layer-local coords which are
            //    pre-rotation... actually it DOES rotate with the layer. To
            //    keep it fixed we'd need a separate non-rotating layer. For
            //    simplicity we let it rotate too — it reads as part of the
            //    spin. If you want it truly static, wrap in a separate Box
            //    without graphicsLayer rotation.)
            val highlightCenter = Offset(
                center.x - orbRadius * 0.32f,
                center.y - orbRadius * 0.32f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.30f), Color.Transparent),
                    center = highlightCenter,
                    radius = orbRadius * 0.55f
                ),
                radius = orbRadius * 0.55f,
                center = highlightCenter
            )

            // 5. Subtle rim light — defines the orb edge crisply.
            drawCircle(
                color = AccentCyan.copy(alpha = 0.35f),
                radius = orbRadius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = orbRadius * 0.06f)
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
        modifier = modifier.size(size)
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
