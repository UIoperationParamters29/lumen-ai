package com.cortex.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val CortexDarkScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = BgElevated,
    onPrimaryContainer = TextPrimary,
    secondary = AccentCyan,
    onSecondary = Color.White,
    secondaryContainer = BgSurfaceHigh,
    onSecondaryContainer = TextPrimary,
    tertiary = AccentGreen,
    onTertiary = Color.Black,
    tertiaryContainer = BgSurfaceHigh,
    onTertiaryContainer = TextPrimary,
    background = BgPrimary,
    onBackground = TextPrimary,
    surface = BgSurface,
    onSurface = TextPrimary,
    surfaceVariant = BgSurfaceHigh,
    onSurfaceVariant = TextSecondary,
    surfaceTint = AccentBlue,
    outline = BorderSubtle,
    outlineVariant = BorderStrong,
    error = StatusError,
    onError = Color.White,
    errorContainer = StatusError.copy(alpha = 0.15f),
    onErrorContainer = StatusError,
    scrim = Color.Black
)

@Composable
fun CortexTheme(content: @Composable () -> Unit) {
    val colorScheme = CortexDarkScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.statusBarColor = BgPrimary.toArgb()
                window.navigationBarColor = BgPrimary.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
