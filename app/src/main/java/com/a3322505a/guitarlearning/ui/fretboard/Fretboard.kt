package com.a3322505a.guitarlearning.ui.fretboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CornerRadius
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a3322505a.guitarlearning.core.FretPosition

const val FIRST_FRET = 0
const val LAST_FRET = 12
private const val STRING_LABEL_WIDTH_DP = 42
private const val FRET_COUNT = LAST_FRET - FIRST_FRET + 1

private val selectedCellColor = Color(0xFFFFD54F)
private val fretboardColor = Color(0xFF795548)
private val fretLineColor = Color(0xFFE8DCCB)
private val stringColor = Color(0xFFF4F1EA)
private val markerColor = Color(0xFF2D211B)

fun rowIndexForString(string: Int): Int {
    require(string in 1..6) { "string must be between 1 and 6" }
    return 6 - string
}

fun isHighlighted(
    selectedPosition: FretPosition?,
    string: Int,
    fret: Int,
): Boolean = selectedPosition?.string == string && selectedPosition.fret == fret

/** The horizontal start of a fret cell, expressed as a fraction of board width. */
fun fretLeftFraction(fret: Int): Float {
    require(fret in FIRST_FRET..LAST_FRET) { "fret must be between 0 and 12" }
    return (fret - FIRST_FRET).toFloat() / FRET_COUNT
}

/** The horizontal end of a fret cell, expressed as a fraction of board width. */
fun fretRightFraction(fret: Int): Float {
    require(fret in FIRST_FRET..LAST_FRET) { "fret must be between 0 and 12" }
    return (fret - FIRST_FRET + 1).toFloat() / FRET_COUNT
}

/** Number of inlay dots rendered for a fret. */
fun markerCountForFret(fret: Int): Int = when (fret) {
    3, 5, 7, 9 -> 1
    12 -> 2
    else -> 0
}

@Composable
fun Fretboard(
    selectedPosition: FretPosition? = null,
    modifier: Modifier = Modifier,
) {
    val selectedDescription = selectedPosition?.let {
        "，当前位置：${it.string}弦${it.fret}品"
    }.orEmpty()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "真实六弦零至十二品指板$selectedDescription"
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FretHeader()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp),
        ) {
            StringLabels()
            FretboardCanvas(
                selectedPosition = selectedPosition,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun FretHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.width(STRING_LABEL_WIDTH_DP.dp))
        (FIRST_FRET..LAST_FRET).forEach { fret ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = fret.toString(),
                    fontSize = 11.sp,
                    fontWeight = if (fret == FIRST_FRET) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun StringLabels() {
    Column(
        modifier = Modifier
            .width(STRING_LABEL_WIDTH_DP.dp)
            .fillMaxHeight(),
    ) {
        (6 downTo 1).forEach { string ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${string}弦",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun FretboardCanvas(
    selectedPosition: FretPosition?,
    modifier: Modifier,
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .semantics {
                contentDescription = "代码绘制的真实结构指板"
            },
    ) {
        drawRoundRect(
            color = fretboardColor,
            cornerRadius = CornerRadius(8.dp.toPx()),
        )

        selectedPosition?.let { position ->
            val left = size.width * fretLeftFraction(position.fret)
            val right = size.width * fretRightFraction(position.fret)
            drawRect(
                color = selectedCellColor.copy(alpha = 0.75f),
                topLeft = androidx.compose.ui.geometry.Offset(left, 0f),
                size = androidx.compose.ui.geometry.Size(right - left, size.height),
            )
        }

        drawFretLines()
        drawInlayMarkers()
        drawStrings()
    }
}

private fun DrawScope.drawFretLines() {
    for (boundary in 0..FRET_COUNT) {
        val x = size.width * boundary / FRET_COUNT
        drawLine(
            color = if (boundary == 0) Color(0xFFFFF4D6) else fretLineColor,
            start = androidx.compose.ui.geometry.Offset(x, 0f),
            end = androidx.compose.ui.geometry.Offset(x, size.height),
            strokeWidth = if (boundary == 0) 5.dp.toPx() else 1.dp.toPx(),
        )
    }
}

private fun DrawScope.drawInlayMarkers() {
    for (fret in FIRST_FRET..LAST_FRET) {
        val count = markerCountForFret(fret)
        if (count == 0) continue

        val centerX = size.width * (fretLeftFraction(fret) + fretRightFraction(fret)) / 2f
        val yPositions = if (count == 1) {
            listOf(size.height / 2f)
        } else {
            listOf(size.height * 0.36f, size.height * 0.64f)
        }
        yPositions.forEach { y ->
            drawCircle(
                color = markerColor,
                radius = 4.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(centerX, y),
            )
        }
    }
}

private fun DrawScope.drawStrings() {
    (6 downTo 1).forEachIndexed { index, _ ->
        val y = size.height * (index + 0.5f) / 6f
        drawLine(
            color = stringColor,
            start = androidx.compose.ui.geometry.Offset(0f, y),
            end = androidx.compose.ui.geometry.Offset(size.width, y),
            strokeWidth = (4.5f - index * 0.65f).coerceAtLeast(1.2f).dp.toPx(),
        )
    }
}
