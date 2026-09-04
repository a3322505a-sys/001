package com.a3322505a.guitarlearning.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.ui.theme.PixelError
import com.a3322505a.guitarlearning.ui.theme.PixelSuccess

@Composable
fun PixelStats(
    correctCount: Int,
    errorCount: Int,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val panelPadding = if (emphasized) {
        PaddingValues(horizontal = 8.dp, vertical = 8.dp)
    } else {
        PaddingValues(horizontal = 6.dp, vertical = 5.dp)
    }
    val textStyle = if (emphasized) {
        MaterialTheme.typography.titleSmall
    } else {
        MaterialTheme.typography.labelMedium
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PixelPanel(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = if (emphasized) 36.dp else 0.dp),
            style = PixelPanelStyle.Success,
            contentPadding = panelPadding,
        ) {
            Text(
                text = "✓ $correctCount",
                modifier = Modifier.fillMaxWidth(),
                color = PixelSuccess,
                textAlign = TextAlign.Center,
                style = textStyle,
            )
        }
        PixelPanel(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = if (emphasized) 36.dp else 0.dp),
            style = PixelPanelStyle.Error,
            contentPadding = panelPadding,
        ) {
            Text(
                text = "× $errorCount",
                modifier = Modifier.fillMaxWidth(),
                color = PixelError,
                textAlign = TextAlign.Center,
                style = textStyle,
            )
        }
    }
}
