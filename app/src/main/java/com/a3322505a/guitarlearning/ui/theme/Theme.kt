package com.a3322505a.guitarlearning.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AppColors = lightColorScheme(
    primary = PixelGreen,
    onPrimary = PixelSurface,
    primaryContainer = PixelGreenLight,
    onPrimaryContainer = PixelGreenDark,
    secondary = PixelGold,
    onSecondary = PixelInk,
    background = PixelBackground,
    onBackground = PixelInk,
    surface = PixelSurface,
    onSurface = PixelInk,
    surfaceVariant = PixelSurfaceAlt,
    onSurfaceVariant = PixelInkMuted,
    outline = PixelBorder,
    error = PixelError,
    onError = PixelSurface,
    errorContainer = PixelErrorSurface,
    onErrorContainer = PixelErrorDark,
)

@Composable
fun GuitarLearningTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = PixelTypography,
        shapes = PixelShapes,
        content = content,
    )
}
