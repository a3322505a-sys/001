package com.a3322505a.guitarlearning.ui.feedback

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val correctBorderColor = Color(0xFF2E7D32)
private val incorrectBorderColor = Color(0xFFC62828)

/** Compact, shared session counters used by every training screen. */
@Composable
fun SessionStatsBadges(
    correctCount: Int,
    incorrectCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatBadge(
            text = "正确 $correctCount",
            borderColor = correctBorderColor,
            modifier = Modifier.weight(1f),
        )
        StatBadge(
            text = "错误 $incorrectCount",
            borderColor = incorrectBorderColor,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatBadge(
    text: String,
    borderColor: Color,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 36.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, borderColor),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = borderColor,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
