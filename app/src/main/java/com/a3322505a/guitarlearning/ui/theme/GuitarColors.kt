package com.a3322505a.guitarlearning.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppTheme(val id: String, val title: String, val dark: Boolean) {
    CLEAR("clear", "清爽青白", false),
    FOREST("forest", "暖纸森林", false),
    MIDNIGHT("midnight", "午夜蓝", true),
    GRAPHITE("graphite", "石墨紫", true);

    companion object {
        // Persist a string so a future/unknown theme in a backup remains readable.
        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: CLEAR
    }
}

data class StateColors(val background: Color, val ink: Color)

data class GuitarColors(
    val background: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val border: Color,
    val accent: Color,
    val onAccent: Color,
    val mastered: StateColors,
    val available: StateColors,
    val locked: StateColors,
    val review: StateColors,
    val error: StateColors,
)

private val lightMastered = StateColors(Color(0xFF116B50), Color.White)
private val darkMastered = StateColors(Color(0xFF145C47), Color.White)
private val lightAvailable = StateColors(Color(0xFFE2EDFF), Color(0xFF173E8A))
private val lightReview = StateColors(Color(0xFFFFF0C8), Color(0xFF7A4800))
private val darkReview = StateColors(Color(0xFF49380E), Color(0xFFFFDAA0))
private val lightError = StateColors(Color(0xFFFEE4E2), Color(0xFF9B1C13))
private val darkError = StateColors(Color(0xFF4A211F), Color(0xFFFFD3CE))

private val clearColors = GuitarColors(
    background = Color(0xFFF3F5F7), surface = Color.White,
    ink = Color(0xFF17212F), muted = Color(0xFF53616F),
    border = Color(0xFFA0ADB9), accent = Color(0xFF175E72), onAccent = Color.White,
    mastered = lightMastered, available = lightAvailable,
    locked = StateColors(Color(0xFFDDE2E8), Color(0xFF4B5563)),
    review = lightReview, error = lightError,
)
private val forestColors = GuitarColors(
    background = Color(0xFFF2EAD8), surface = Color(0xFFFFFBF2),
    ink = Color(0xFF27271F), muted = Color(0xFF625D4F),
    border = Color(0xFFA59B84), accent = Color(0xFF28614A), onAccent = Color.White,
    mastered = lightMastered, available = lightAvailable,
    locked = StateColors(Color(0xFFDCD8CB), Color(0xFF55574F)),
    review = lightReview, error = lightError,
)
private val midnightColors = GuitarColors(
    background = Color(0xFF101B2B), surface = Color(0xFF1B2B3F),
    ink = Color(0xFFEDF4FE), muted = Color(0xFFBBCBDC),
    border = Color(0xFF768CA6), accent = Color(0xFF84DBF1), onAccent = Color(0xFF101B2B),
    mastered = darkMastered,
    available = StateColors(Color(0xFF21446C), Color(0xFFDEEFFF)),
    locked = StateColors(Color(0xFF334052), Color(0xFFC0CCDA)),
    review = darkReview, error = darkError,
)
private val graphiteColors = GuitarColors(
    background = Color(0xFF211E29), surface = Color(0xFF302B3B),
    ink = Color(0xFFF5EFFB), muted = Color(0xFFC9BFD3),
    border = Color(0xFF91859F), accent = Color(0xFFD8BBFF), onAccent = Color(0xFF211E29),
    mastered = darkMastered,
    available = StateColors(Color(0xFF293C61), Color(0xFFDEE9FF)),
    locked = StateColors(Color(0xFF45404E), Color(0xFFD0C8D7)),
    review = darkReview, error = darkError,
)

fun colorsFor(theme: AppTheme): GuitarColors = when (theme) {
    AppTheme.CLEAR -> clearColors
    AppTheme.FOREST -> forestColors
    AppTheme.MIDNIGHT -> midnightColors
    AppTheme.GRAPHITE -> graphiteColors
}

val LocalGuitarColors = staticCompositionLocalOf { clearColors }
