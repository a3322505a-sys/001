package com.a3322505a.guitarlearning.ui.fretboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a3322505a.guitarlearning.core.FretPosition

const val FIRST_FRET = 0
const val LAST_FRET = 12
private const val STRING_LABEL_WIDTH_DP = 36
private val selectedCellColor = Color(0xFFFFE082)

fun rowIndexForString(string: Int): Int {
    require(string in 1..6) { "string must be between 1 and 6" }
    return 6 - string
}

fun isHighlighted(
    selectedPosition: FretPosition?,
    string: Int,
    fret: Int,
): Boolean = selectedPosition?.string == string && selectedPosition.fret == fret

@Composable
fun Fretboard(
    selectedPosition: FretPosition? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "静态六弦十二品指板" },
    ) {
        FretHeader()
        (6 downTo 1).forEach { string ->
            FretStringRow(
                string = string,
                selectedPosition = selectedPosition,
            )
        }
        FretMarkers()
    }
}

@Composable
private fun FretHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        BoardLabel(text = "品")
        (FIRST_FRET..LAST_FRET).forEach { fret ->
            BoardCell(
                text = fret.toString(),
                modifier = Modifier.weight(1f),
                textSize = 10.sp,
                bold = fret == 0,
            )
        }
    }
}

@Composable
private fun FretStringRow(
    string: Int,
    selectedPosition: FretPosition?,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        BoardLabel(text = "${string}弦")
        (FIRST_FRET..LAST_FRET).forEach { fret ->
            BoardCell(
                text = "",
                modifier = Modifier.weight(1f),
                selected = isHighlighted(selectedPosition, string, fret),
            )
        }
    }
}

@Composable
private fun FretMarkers() {
    Row(modifier = Modifier.fillMaxWidth()) {
        BoardLabel(text = "标记")
        (FIRST_FRET..LAST_FRET).forEach { fret ->
            BoardCell(
                text = markerForFret(fret),
                modifier = Modifier.weight(1f),
                textSize = 12.sp,
            )
        }
    }
}

private fun markerForFret(fret: Int): String = when (fret) {
    3, 5, 7, 9 -> "●"
    12 -> "●●"
    else -> ""
}

@Composable
private fun BoardLabel(text: String) {
    Box(
        modifier = Modifier
            .width(STRING_LABEL_WIDTH_DP.dp)
            .height(36.dp)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline))
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BoardCell(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    textSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    bold: Boolean = false,
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline))
            .background(if (selected) selectedCellColor else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = textSize,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
