package com.a3322505a.guitarlearning.training

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.storage.Session

/** Compact status blocks shared by both training surfaces. */
@Composable
fun CorrectErrorStats(
    session: Session,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatBlock(
            text = "正确 ${session.correctCount}",
            color = Color(0xFF2E7D32),
            modifier = Modifier.weight(1f),
        )
        StatBlock(
            text = "错误 ${session.questionCount - session.correctCount}",
            color = Color(0xFFC62828),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatBlock(
    text: String,
    color: Color,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = color,
        border = BorderStroke(1.dp, color),
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
