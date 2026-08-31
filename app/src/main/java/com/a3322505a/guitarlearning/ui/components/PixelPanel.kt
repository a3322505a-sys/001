package com.a3322505a.guitarlearning.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.ui.theme.PixelBorder
import com.a3322505a.guitarlearning.ui.theme.PixelError
import com.a3322505a.guitarlearning.ui.theme.PixelErrorSurface
import com.a3322505a.guitarlearning.ui.theme.PixelGreenDark
import com.a3322505a.guitarlearning.ui.theme.PixelGreenLight
import com.a3322505a.guitarlearning.ui.theme.PixelInk
import com.a3322505a.guitarlearning.ui.theme.PixelSuccess
import com.a3322505a.guitarlearning.ui.theme.PixelSuccessSurface
import com.a3322505a.guitarlearning.ui.theme.PixelSurface

enum class PixelPanelStyle {
    Default,
    Selected,
    Success,
    Error,
}

@Composable
fun PixelPanel(
    modifier: Modifier = Modifier,
    style: PixelPanelStyle = PixelPanelStyle.Default,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable () -> Unit,
) {
    val colors = when (style) {
        PixelPanelStyle.Default -> PixelSurface to PixelBorder
        PixelPanelStyle.Selected -> PixelGreenLight to PixelGreenDark
        PixelPanelStyle.Success -> PixelSuccessSurface to PixelSuccess
        PixelPanelStyle.Error -> PixelErrorSurface to PixelError
    }
    Surface(
        modifier = modifier,
        color = colors.first,
        contentColor = PixelInk,
        shape = androidx.compose.material3.MaterialTheme.shapes.medium,
        border = BorderStroke(2.dp, colors.second),
        shadowElevation = 3.dp,
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}
