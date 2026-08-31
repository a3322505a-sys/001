package com.a3322505a.guitarlearning.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PixelPanel(
            modifier = Modifier.weight(1f),
            style = PixelPanelStyle.Success,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 5.dp),
        ) {
            Text(
                text = "✓ $correctCount",
                modifier = Modifier.fillMaxWidth(),
                color = PixelSuccess,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        PixelPanel(
            modifier = Modifier.weight(1f),
            style = PixelPanelStyle.Error,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 5.dp),
        ) {
            Text(
                text = "× $errorCount",
                modifier = Modifier.fillMaxWidth(),
                color = PixelError,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
