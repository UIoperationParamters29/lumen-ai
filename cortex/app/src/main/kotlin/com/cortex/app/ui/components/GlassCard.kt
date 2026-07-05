package com.cortex.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cortex.app.ui.theme.*

/**
 * Glassmorphism card — semi-transparent with subtle gradient border and inner highlight.
 * The signature visual element of Cortex's futuristic UI.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    gradient: Brush = CardGradient,
    borderGradient: Brush = GlassGradient,
    borderWidth: Dp = 1.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(gradient)
            .border(BorderStroke(borderWidth, borderGradient), RoundedCornerShape(cornerRadius))
    ) {
        // Subtle top highlight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .clip(RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius))
                .background(GlassHighlight)
        )
        Column(content = content)
    }
}

/**
 * Glow modifier — adds a subtle radial glow behind the element.
 */
fun Modifier.glow(
    color: Color = AccentBlue.copy(alpha = 0.15f),
    radius: Dp = 24.dp
): Modifier = this.then(
    Modifier.padding(radius)
).then(
    Modifier.background(
        Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            radius = radius.value * 2f
        )
    )
)
