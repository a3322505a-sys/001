package com.a3322505a.guitarlearning.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.ui.theme.PixelBorder
import com.a3322505a.guitarlearning.ui.theme.PixelError
import com.a3322505a.guitarlearning.ui.theme.PixelErrorDark
import com.a3322505a.guitarlearning.ui.theme.PixelGreen
import com.a3322505a.guitarlearning.ui.theme.PixelGreenDark
import com.a3322505a.guitarlearning.ui.theme.PixelGreenLight
import com.a3322505a.guitarlearning.ui.theme.PixelInk
import com.a3322505a.guitarlearning.ui.theme.PixelInkMuted
import com.a3322505a.guitarlearning.ui.theme.PixelSuccess
import com.a3322505a.guitarlearning.ui.theme.PixelSurface
import com.a3322505a.guitarlearning.ui.theme.PixelSurfaceAlt

enum class PixelButtonStyle {
    Primary,
    Secondary,
    Success,
    Error,
}

@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: PixelButtonStyle = PixelButtonStyle.Primary,
    leadingSymbol: String? = null,
    maxLines: Int = 1,
) {
    val (container, border) = when (style) {
        PixelButtonStyle.Primary -> PixelGreen to PixelGreenDark
        PixelButtonStyle.Secondary -> PixelSurfaceAlt to PixelBorder
        PixelButtonStyle.Success -> PixelSuccess to PixelGreenDark
        PixelButtonStyle.Error -> PixelError to PixelErrorDark
    }
    val contentColor = if (style == PixelButtonStyle.Secondary) PixelInk else PixelSurface
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(2.dp, border),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = contentColor,
            disabledContainerColor = container,
            disabledContentColor = contentColor,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 3.dp,
            pressedElevation = 0.dp,
            disabledElevation = 1.dp,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = listOfNotNull(leadingSymbol, text).joinToString(" "),
            style = MaterialTheme.typography.labelLarge,
            maxLines = maxLines,
        )
    }
}

@Composable
fun PixelOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    val borderColor = if (selected) PixelGreenDark else PixelBorder
    val background = if (selected) PixelGreenLight else PixelSurface
    val textColor = if (selected) PixelGreenDark else if (enabled) PixelInk else PixelInkMuted
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = background,
            contentColor = textColor,
            disabledContainerColor = background,
            disabledContentColor = textColor,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = if (selected) "✓  $text" else text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}
