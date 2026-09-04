package com.a3322505a.guitarlearning.ui.fretboard

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.ui.theme.FretMetal
import com.a3322505a.guitarlearning.ui.theme.FretboardWoodDark

private const val BOARD_BORDER_STROKE_DP = 2f

/** Vertical fret-wire positions for a visible range. Cropped ranges include both outer boundaries. */
fun fretLineFractions(
    firstFret: Int = FIRST_FRET,
    lastFret: Int = LAST_FRET,
): List<Float> {
    require(firstFret in FIRST_FRET..LAST_FRET) { "firstFret must be between 0 and 12" }
    require(lastFret in firstFret..LAST_FRET) {
        "lastFret must be between firstFret and 12"
    }
    val rightEdges = (firstFret..lastFret).map {
        fretRightFraction(it, lastFret, firstFret)
    }
    return if (firstFret == FIRST_FRET) rightEdges else listOf(0f) + rightEdges
}

/** Adds the missing visible fret wires at both edges when the board starts above fret 0. */
fun Modifier.croppedFretboardBoundary(
    firstFret: Int,
    lastFret: Int,
): Modifier {
    if (firstFret == FIRST_FRET) return this
    val fractions = fretLineFractions(firstFret, lastFret)
        .filter { it <= 0f || it >= 1f }
    return drawWithContent {
        drawContent()
        val regularWidth = 1.dp.toPx()
        val shadowWidth = regularWidth + 2.dp.toPx()
        val edgeInset = BOARD_BORDER_STROKE_DP.dp.toPx() + regularWidth / 2f
        fractions.forEach { fraction ->
            val x = if (fraction <= 0f) edgeInset else size.width - edgeInset
            drawLine(
                color = FretboardWoodDark,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = shadowWidth,
            )
            drawLine(
                color = FretMetal,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = regularWidth,
            )
        }
    }
}
