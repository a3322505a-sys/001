package com.a3322505a.guitarlearning.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.ui.theme.PixelError
import com.a3322505a.guitarlearning.ui.theme.PixelSuccess

@Composable
fun PixelFeedbackPanel(
    success: Boolean,
    text: String,
    modifier: Modifier = Modifier,
) {
    val statusColor = if (success) PixelSuccess else PixelError
    PixelPanel(
        modifier = modifier.fillMaxWidth(),
        style = if (success) PixelPanelStyle.Success else PixelPanelStyle.Error,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (success) "✓" else "×",
                color = statusColor,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = text,
                color = statusColor,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
