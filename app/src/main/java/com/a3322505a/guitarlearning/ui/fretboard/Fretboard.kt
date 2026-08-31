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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a3322505a.guitarlearning.core.FretPosition
import com.a3322505a.guitarlearning.ui.theme.FretMetal
import com.a3322505a.guitarlearning.ui.theme.FretboardWood
import com.a3322505a.guitarlearning.ui.theme.FretboardWoodDark
import com.a3322505a.guitarlearning.ui.theme.FretboardWoodLight
import com.a3322505a.guitarlearning.ui.theme.Inlay
import com.a3322505a.guitarlearning.ui.theme.NutIvory
import com.a3322505a.guitarlearning.ui.theme.PixelGlow
import com.a3322505a.guitarlearning.ui.theme.StringMetal

const val FIRST_FRET = 0
const val LAST_FRET = 12
private const val STRING_LABEL_WIDTH_DP = 42
private const val FRET_HEADER_HEIGHT_DP = 22
private const val FRET_COUNT = LAST_FRET - FIRST_FRET + 1

data class FretboardCell(
    val string: Int,
    val fret: Int,
)

fun rowIndexForString(string: Int): Int {
    require(string in 1..6) { "string must be between 1 and 6" }
    return 6 - string
}

fun isHighlighted(
    selectedPosition: FretPosition?,
    string: Int,
    fret: Int,
): Boolean = selectedPosition?.string == string && selectedPosition.fret == fret

/** Cells highlighted for a target. The renderer uses this same list as the regression tests. */
fun highlightedCells(selectedPosition: FretPosition?): List<FretboardCell> =
    (6 downTo 1).flatMap { string ->
        (FIRST_FRET..LAST_FRET).mapNotNull { fret ->
            if (isHighlighted(selectedPosition, string, fret)) {
                FretboardCell(string = string, fret = fret)
            } else {
                null
            }
        }
    }

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

/** The vertical center of a string row, expressed as a fraction of board height. */
fun stringCenterFraction(string: Int): Float =
    (rowIndexForString(string) + 0.5f) / 6f

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
        "，当前位置：" + it.string + "弦" + it.fret + "品"
    }.orEmpty()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .semantics {
                contentDescription = "真实六弦零至十二品指板" + selectedDescription
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FretHeader()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
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
                    .height(FRET_HEADER_HEIGHT_DP.dp),
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
                    text = string.toString() + "弦",
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
            .fillMaxSize()
            .clip(CutCornerShape(6.dp))
            .semantics {
                contentDescription = "代码绘制的真实结构指板"
            },
    ) {
        drawRect(color = FretboardWood)
        drawPixelWoodGrain()
        drawTargetHighlights(selectedPosition)
        drawFretLines()
        drawInlayMarkers()
        drawStrings()
        drawRect(
            color = FretboardWoodDark,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

private fun DrawScope.drawPixelWoodGrain() {
    for (index in 0 until 12) {
        val y = size.height * (index + 1) / 13f
        val start = size.width * ((index * 17) % 29) / 100f
        val length = size.width * (0.18f + (index % 4) * 0.06f)
        drawRect(
            color = if (index % 2 == 0) {
                FretboardWoodLight.copy(alpha = 0.72f)
            } else {
                FretboardWoodDark.copy(alpha = 0.55f)
            },
            topLeft = androidx.compose.ui.geometry.Offset(start, y),
            size = androidx.compose.ui.geometry.Size(
                width = length.coerceAtMost(size.width - start),
                height = 1.dp.toPx(),
            ),
        )

        val secondStart = size.width * (0.58f + ((index * 7) % 16) / 100f)
        drawRect(
            color = FretboardWoodDark.copy(alpha = 0.38f),
            topLeft = androidx.compose.ui.geometry.Offset(secondStart, y + 3.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(
                width = (size.width * 0.14f).coerceAtMost(size.width - secondStart),
                height = 1.dp.toPx(),
            ),
        )
    }
}

private fun DrawScope.drawTargetHighlights(selectedPosition: FretPosition?) {
    highlightedCells(selectedPosition).forEach { cell ->
        val left = size.width * fretLeftFraction(cell.fret)
        val right = size.width * fretRightFraction(cell.fret)
        val cellHeight = size.height / 6f
        val center = androidx.compose.ui.geometry.Offset(
            x = (left + right) / 2f,
            y = cellHeight * (rowIndexForString(cell.string) + 0.5f),
        )
        val outerSize = minOf(right - left, cellHeight) * 0.72f
        val outerTopLeft = androidx.compose.ui.geometry.Offset(
            center.x - outerSize / 2f,
            center.y - outerSize / 2f,
        )
        drawRect(
            color = PixelGlow.copy(alpha = 0.18f),
            topLeft = outerTopLeft,
            size = androidx.compose.ui.geometry.Size(outerSize, outerSize),
        )
        val ringSize = outerSize * 0.68f
        drawRect(
            color = PixelGlow,
            topLeft = androidx.compose.ui.geometry.Offset(
                center.x - ringSize / 2f,
                center.y - ringSize / 2f,
            ),
            size = androidx.compose.ui.geometry.Size(ringSize, ringSize),
            style = Stroke(width = 2.dp.toPx()),
        )
        val coreSize = 5.dp.toPx()
        drawRect(
            color = NutIvory,
            topLeft = androidx.compose.ui.geometry.Offset(
                center.x - coreSize / 2f,
                center.y - coreSize / 2f,
            ),
            size = androidx.compose.ui.geometry.Size(coreSize, coreSize),
        )
    }
}

private fun DrawScope.drawFretLines() {
    val regularWidth = 1.dp.toPx()
    val nutWidth = 5.dp.toPx()
    for (boundary in 0..FRET_COUNT) {
        val rawX = size.width * boundary / FRET_COUNT
        val x = when (boundary) {
            0 -> nutWidth / 2f
            FRET_COUNT -> size.width - regularWidth / 2f
            else -> rawX
        }
        val strokeWidth = if (boundary == 0) nutWidth else regularWidth
        drawLine(
            color = FretboardWoodDark,
            start = androidx.compose.ui.geometry.Offset(x, 0f),
            end = androidx.compose.ui.geometry.Offset(x, size.height),
            strokeWidth = strokeWidth + 2.dp.toPx(),
        )
        drawLine(
            color = if (boundary == 0) NutIvory else FretMetal,
            start = androidx.compose.ui.geometry.Offset(x, 0f),
            end = androidx.compose.ui.geometry.Offset(x, size.height),
            strokeWidth = strokeWidth,
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
            val markerSize = 8.dp.toPx()
            drawRect(
                color = FretboardWoodDark,
                topLeft = androidx.compose.ui.geometry.Offset(
                    centerX - markerSize / 2f,
                    y - markerSize / 2f,
                ),
                size = androidx.compose.ui.geometry.Size(markerSize, markerSize),
            )
            val innerSize = 5.dp.toPx()
            drawRect(
                color = Inlay,
                topLeft = androidx.compose.ui.geometry.Offset(
                    centerX - innerSize / 2f,
                    y - innerSize / 2f,
                ),
                size = androidx.compose.ui.geometry.Size(innerSize, innerSize),
            )
        }
    }
}

private fun DrawScope.drawStrings() {
    (6 downTo 1).forEach { string ->
        val y = size.height * stringCenterFraction(string)
        val rowIndex = rowIndexForString(string)
        val strokeWidth = (4.5f - rowIndex * 0.65f).coerceAtLeast(1.2f).dp.toPx()
        drawLine(
            color = FretboardWoodDark.copy(alpha = 0.8f),
            start = androidx.compose.ui.geometry.Offset(0f, y + 1.dp.toPx()),
            end = androidx.compose.ui.geometry.Offset(size.width, y + 1.dp.toPx()),
            strokeWidth = strokeWidth + 1.dp.toPx(),
        )
        drawLine(
            color = StringMetal,
            start = androidx.compose.ui.geometry.Offset(0f, y),
            end = androidx.compose.ui.geometry.Offset(size.width, y),
            strokeWidth = strokeWidth,
        )
    }
}
