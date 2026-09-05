package com.a3322505a.guitarlearning.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun GuitarLearningTheme(themeId: String = AppTheme.CLEAR.id, content: @Composable () -> Unit) {
    val theme = AppTheme.fromId(themeId)
    val colors = colorsFor(theme)
    val scheme = (if (theme.dark) darkColorScheme() else lightColorScheme()).copy(
        primary = colors.accent, onPrimary = colors.onAccent,
        primaryContainer = colors.available.background, onPrimaryContainer = colors.available.ink,
        secondary = colors.accent, onSecondary = colors.onAccent,
        secondaryContainer = colors.available.background, onSecondaryContainer = colors.available.ink,
        tertiary = colors.mastered.background, onTertiary = colors.mastered.ink,
        tertiaryContainer = colors.review.background, onTertiaryContainer = colors.review.ink,
        background = colors.background, onBackground = colors.ink,
        surface = colors.surface, onSurface = colors.ink,
        surfaceVariant = colors.locked.background, onSurfaceVariant = colors.muted,
        outline = colors.border, outlineVariant = colors.border,
        surfaceTint = colors.accent,
        surfaceDim = colors.background, surfaceBright = colors.surface,
        surfaceContainerLowest = colors.background, surfaceContainerLow = colors.surface,
        surfaceContainer = colors.surface, surfaceContainerHigh = colors.surface,
        surfaceContainerHighest = colors.surface,
        error = colors.error.ink, onError = colors.error.background,
        errorContainer = colors.error.background, onErrorContainer = colors.error.ink,
    )
    CompositionLocalProvider(LocalGuitarColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = PixelTypography, shapes = PixelShapes, content = content)
    }
}
