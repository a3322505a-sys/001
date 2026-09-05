package com.a3322505a.guitarlearning.learning

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a3322505a.guitarlearning.ui.theme.*

@Composable
fun TeachingFretboard(active: ActiveTask, enabled: Boolean, onPosition: (Coordinate) -> Unit, modifier: Modifier = Modifier) {
    val task = active.task
    val geometry = remember(task.range.firstFret, task.range.lastFret) { TeachingGeometry(task.range.firstFret, task.range.lastFret) }
    val reveal = task.guided || active.hintLevel >= 2 || active.phase in listOf(Phase.CORRECTING, Phase.CORRECTED)
    val answers = if (reveal) AnswerEvaluator.validPositions(task, active.sequenceIndex).toSet() else emptySet()
    val reference = task.coordinate.takeIf { task.direction == Direction.POSITION_TO_NOTE }
    val mistake = active.inputs.lastOrNull { it.result == ClickResult.WRONG }?.coordinate
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(22.dp)) {
            Spacer(Modifier.width(34.dp))
            (geometry.first..geometry.last).forEach { f ->
                Box(Modifier.weight(geometry.right(f) - geometry.left(f)), contentAlignment = Alignment.Center) {
                    if (!task.hideFretLabels) Text(if (f == 0) "0" else f.toString(), fontSize = 12.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.width(34.dp).fillMaxHeight()) {
                (1..6).forEach { s -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(if (task.hideStringLabels) if (s == 1) "细" else if (s == 6) "粗" else "" else "${s}弦", fontSize = 12.sp)
                } }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().background(FretboardWood)) {
                Canvas(Modifier.fillMaxSize()) {
                    if (geometry.first == 0) {
                        drawRect(FretboardWoodDark, size = size.copy(width = size.width * geometry.right(0)))
                        val x = size.width * geometry.right(0)
                        drawLine(NutIvory, Offset(x, 0f), Offset(x, size.height), 5.dp.toPx())
                    }
                    (geometry.first..geometry.last).forEach { f ->
                        val x = size.width * geometry.right(f)
                        drawLine(FretMetal, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                        val center = size.width * geometry.center(f)
                        when (geometry.inlays(f)) {
                            1 -> drawCircle(Inlay, 4.dp.toPx(), Offset(center, size.height * 0.5f))
                            2 -> { drawCircle(Inlay, 4.dp.toPx(), Offset(center, size.height / 3)); drawCircle(Inlay, 4.dp.toPx(), Offset(center, size.height * 2 / 3)) }
                        }
                    }
                    (1..6).forEach { s ->
                        val y = size.height * (s - 0.5f) / 6
                        drawLine(StringMetal, Offset.Zero.copy(y = y), Offset(size.width, y), (0.6f + s * 0.23f).dp.toPx())
                    }
                }
                // Separate accessible cells also accept whole-string/fret answers without hidden coordinates.
                (1..6).forEach { s -> (geometry.first..geometry.last).forEach { f ->
                    val c = Coordinate(s, f)
                    val target = c in answers || c == reference
                    val correct = c in active.confirmed
                    val wrong = c == mistake
                    Box(Modifier.absoluteOffset(x = maxWidth * geometry.left(f), y = maxHeight * ((s - 1) / 6f))
                        .width(maxWidth * (geometry.right(f) - geometry.left(f))).height(maxHeight / 6)
                        .semantics { contentDescription = "${s}弦${if (f == 0) "空弦" else "${f}品格"}${if (correct) "，已确认" else ""}" }
                        .clickable(enabled = enabled) { onPosition(c) }, contentAlignment = Alignment.Center) {
                        if (target || correct || wrong) {
                            val color = if (wrong) PixelError else if (correct) PixelSuccess else PixelGold
                            Canvas(Modifier.fillMaxSize().padding(3.dp)) {
                                if (task.constraint.kind in listOf(ConstraintKind.STRING, ConstraintKind.FRET) && target) {
                                    drawRect(color.copy(alpha = 0.35f))
                                } else {
                                    val radius = minOf(size.width, size.height) * 0.42f
                                    drawCircle(color, radius)
                                    drawCircle(Color.White, radius, style = Stroke(2.dp.toPx()))
                                }
                            }
                            if (wrong) Text("×", color = Color.White, fontSize = 18.sp)
                            else if (correct) Text("✓", color = Color.White, fontSize = 15.sp)
                            else if (task.guided && task.coordinate == c) Text(task.constraint.symbol.orEmpty(), color = PixelInk, fontSize = 13.sp)
                            else if (c == reference) Text("?", color = PixelInk, fontSize = 15.sp)
                        }
                    }
                } }
            }
        }
        Text("${geometry.first}–${geometry.last}品 · 上细下粗 · 左侧靠近琴头", fontSize = 11.sp, color = PixelInkMuted,
            modifier = Modifier.padding(start = 34.dp, top = 3.dp))
    }
}
