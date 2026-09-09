package com.a3322505a.guitarlearning.learning

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a3322505a.guitarlearning.ui.theme.LocalGuitarColors

internal val PageCardShape = RoundedCornerShape(12.dp)
internal val PageButtonShape = RoundedCornerShape(10.dp)

/** Scoped to ordinary page chrome/body; training and diagram themes stay unchanged. */
@Composable
internal fun LearningPageStyle(content: @Composable () -> Unit) {
    val base = MaterialTheme.typography
    val typography = base.copy(
        titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    )
    MaterialTheme(typography = typography, shapes = MaterialTheme.shapes.copy(
        small = PageButtonShape, medium = PageCardShape, large = PageCardShape,
        extraLarge = PageButtonShape,
    )) {
        CompositionLocalProvider(LocalContentColor provides LocalGuitarColors.current.ink) {
            ProvideTextStyle(typography.bodyLarge, content)
        }
    }
}
