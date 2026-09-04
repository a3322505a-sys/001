package com.a3322505a.guitarlearning

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.ui.components.PixelButton
import com.a3322505a.guitarlearning.ui.components.PixelButtonStyle
import com.a3322505a.guitarlearning.ui.components.PixelHeader
import com.a3322505a.guitarlearning.ui.components.PixelPanel
import com.a3322505a.guitarlearning.ui.fretboard.Fretboard
import com.a3322505a.guitarlearning.ui.theme.PixelBorder
import com.a3322505a.guitarlearning.ui.theme.PixelInkMuted

enum class ReadingNotation(
    val title: String,
    val lineCount: Int,
) {
    Tab(title = "TAB 训练", lineCount = 6),
    Staff(title = "五线谱训练", lineCount = 5),
}

@Composable
fun ReadingTrainingMenuScreen(
    onOpenTab: () -> Unit,
    onOpenStaff: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PixelHeader(title = "读谱训练", onBack = onBack)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(0.78f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PixelButton(
                        text = "TAB 训练",
                        onClick = onOpenTab,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PixelButton(
                        text = "五线谱训练",
                        onClick = onOpenStaff,
                        modifier = Modifier.fillMaxWidth(),
                        style = PixelButtonStyle.Secondary,
                    )
                }
            }
        }
    }
}

@Composable
fun ReadingTrainingScreen(
    notation: ReadingNotation,
    onBack: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PixelHeader(
                title = notation.title,
                subtitle = "第一把位 · 0–4 品",
                onBack = onBack,
            )
            PixelPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                NotationPlaceholder(notation)
            }
            PixelPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.35f),
                contentPadding = PaddingValues(8.dp),
            ) {
                Fretboard(
                    lastFret = 4,
                    showLabels = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun NotationPlaceholder(notation: ReadingNotation) {
    Box(modifier = Modifier.fillMaxSize()) {
        val lineColor = PixelBorder
        Canvas(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            val spacing = size.height / (notation.lineCount + 1)
            repeat(notation.lineCount) { index ->
                val y = spacing * (index + 1)
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }
        Text(
            text = if (notation == ReadingNotation.Tab) "TAB 题面" else "五线谱题面",
            color = PixelInkMuted,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
        )
    }
}
