package com.cortex.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// z.ai-dark with blue/green futuristic accent
val BgPrimary = Color(0xFF06070B)
val BgSurface = Color(0xFF0D0F16)
val BgSurfaceHigh = Color(0xFF141826)
val BgElevated = Color(0xFF1A1E2E)
val BgInput = Color(0xFF0F1219)

val AccentBlue = Color(0xFF3B82F6)
val AccentCyan = Color(0xFF06B6D4)
val AccentGreen = Color(0xFF10B981)
val AccentIndigo = Color(0xFF6366F1)
val AccentTeal = Color(0xFF14B8A6)
val AccentPurple = Color(0xFF8B5CF6)

// Gradients
val OrbStart = Color(0xFF3B82F6)
val OrbMid = Color(0xFF06B6D4)
val OrbEnd = Color(0xFF10B981)

// Glassmorphism
val GlassSurface = Color(0xFF11141C).copy(alpha = 0.72f)
val GlassBorder = Color(0xFF3B82F6).copy(alpha = 0.12f)
val GlassHighlight = Color(0xFFFFFFFF).copy(alpha = 0.03f)

val TextPrimary = Color(0xFFE8EBF0)
val TextSecondary = Color(0xFF9CA3AF)
val TextTertiary = Color(0xFF6B7280)

val BorderSubtle = Color(0xFF1F2937)
val BorderStrong = Color(0xFF374151)

val StatusError = Color(0xFFEF4444)
val StatusWarning = Color(0xFFF59E0B)
val StatusSuccess = Color(0xFF10B981)

val UserBubbleBg = Color(0xFF1A1E2E)
val AssistantBubbleBg = Color(0xFF0D0F16)
val ThinkingBg = Color(0xFF0A1A1A)
val CodeBlockBg = Color(0xFF0D1117)

// Gradient brushes
val AccentGradient = Brush.linearGradient(listOf(AccentBlue, AccentCyan))
val AccentGradientFull = Brush.linearGradient(listOf(AccentIndigo, AccentBlue, AccentCyan, AccentGreen))
val OrbGradient = Brush.radialGradient(listOf(OrbStart, OrbMid, OrbEnd))
val OrbGradientAnimated = Brush.sweepGradient(listOf(AccentBlue, AccentCyan, AccentGreen, AccentBlue))
val GlassGradient = Brush.linearGradient(listOf(GlassSurface, GlassSurface.copy(alpha = 0.5f)))
val CardGradient = Brush.linearGradient(listOf(BgSurface.copy(alpha = 0.9f), BgSurfaceHigh.copy(alpha = 0.6f)))
val InputGradient = Brush.linearGradient(listOf(BgInput, BgSurface))
val GlowGradient = Brush.radialGradient(listOf(AccentBlue.copy(alpha = 0.15f), Color.Transparent))
