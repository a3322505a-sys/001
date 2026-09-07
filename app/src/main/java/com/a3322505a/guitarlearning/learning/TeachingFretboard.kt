package com.a3322505a.guitarlearning.learning

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.DpSize
import kotlin.math.sin

private val TargetCyan = Color(0xFF45DEFF)
private val MarkerInk = Color(0xFF062A39)
private val CorrectMint = Color(0xFF73F0BB)
private val WrongPink = Color(0xFFFF668D)

@Composable
fun TeachingFretboard(state: FretboardUiState, onPosition: (PositionTapped) -> Unit, modifier: Modifier = Modifier) {
    val geometry = remember(state.firstFret, state.lastFret) { TeachingGeometry(state.firstFret, state.lastFret) }
    val inherited = LocalViewConfiguration.current
    val hitConfiguration = remember(inherited) { object : ViewConfiguration by inherited {
        override val minimumTouchTargetSize = DpSize.Zero
    } }
    CompositionLocalProvider(LocalViewConfiguration provides hitConfiguration) {
    BoxWithConstraints(modifier) {
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        val layout = geometry.layout(availableHeight.value, 40f)
        val boardLeft = layout.left.dp
        val boardWidth = layout.width.dp
        val boardHeight = layout.height.dp
        val boardTop = layout.top.dp
        // One continuous viewport: never scroll to a hidden answer when a task changes.
        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
          Box(Modifier.width(boardWidth + boardLeft).height(availableHeight)) {
        Canvas(Modifier.fillMaxSize()) {
            drawInstrument(geometry, boardLeft.toPx(), boardTop.toPx(), boardWidth.toPx(), boardHeight.toPx())
        }
        state.chord?.let { ChordOverlay(it, geometry, boardLeft, boardTop, boardWidth, boardHeight) }
        // Drawing, targets and accessibility share the same fret and string coordinates.
        // Numbers remain available to screen readers, never as a permanent visual answer grid.
        (1..6).forEach { s -> (geometry.first..geometry.last).forEach { f ->
            val c = Coordinate(s, f)
            val mark = state.marks.firstOrNull { it.coordinate == c }
            val target = mark?.role in listOf(MarkRole.TARGET, MarkRole.REFERENCE)
            val correct = mark?.role == MarkRole.CORRECT
            val wrong = mark?.role == MarkRole.WRONG
            key(state.viewId, state.marks, state.interaction) { Box(Modifier.absoluteOffset(x = boardLeft + boardWidth * geometry.left(f), y = boardTop + boardHeight * ((s - 1) / 6f))
                .width(boardWidth * (geometry.right(f) - geometry.left(f))).height(boardHeight / 6)
                .semantics { contentDescription = "${s}弦${if (f == 0) "空弦" else "${f}品格"}${if (correct) "，已确认" else ""}" }
                .clickable(enabled = state.interaction != BoardInteraction.DISABLED && c in state.interactivePositions) { onPosition(PositionTapped(state.viewId, c)) }, contentAlignment = Alignment.Center) {
                if (state.chord != null && (correct || wrong)) Text(if (wrong) "×" else "✓", color = if (wrong) WrongPink else CorrectMint,
                    fontSize = 16.sp, modifier = Modifier.align(Alignment.TopEnd).padding(2.dp))
                if (state.chord == null && (target || correct || wrong)) {
                    val color = if (wrong) WrongPink else if (correct) CorrectMint else TargetCyan
                    val band = mark?.band == true
                    Canvas(Modifier.fillMaxSize().padding(2.dp)) {
                        if (band) {
                            drawRect(color.copy(alpha = 0.32f))
                            drawRect(color, style = Stroke(2.dp.toPx()))
                        } else {
                            val radius = (minOf(size.width, size.height) * 0.43f).coerceAtMost(20.dp.toPx())
                            drawCircle(Color.Black.copy(alpha = 0.6f), radius + 3.dp.toPx())
                            drawCircle(Color.White, radius + 1.5.dp.toPx())
                            drawCircle(color, radius)
                        }
                    }
                    val symbol = mark?.label.orEmpty()
                    if (symbol.isNotEmpty()) Text(symbol, color = MarkerInk, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        } } }
        state.stringLabel?.let { Text(it, color = MarkerInk, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopEnd)) }
        state.fretLabel?.let { (fret, label) ->
            Box(Modifier.absoluteOffset(x = boardLeft + boardWidth * geometry.left(fret))
                .width(boardWidth * (geometry.right(fret) - geometry.left(fret))).height(boardTop), contentAlignment = Alignment.Center) {
                Text(label, fontSize = 12.sp, color = MarkerInk)
            }
        }
    }
    }
  }
  }
}

/** A native drawing, so wood, hardware and interactive marks scale together without bitmap blur. */
private fun DrawScope.drawInstrument(g: TeachingGeometry, left: Float, top: Float, width: Float, height: Float) {
    val bottom = top + height
    val end = left + width
    val nut = if (g.first == 0) left + width * g.right(0) else left
    val board = Path().apply { moveTo(nut, top); lineTo(end, top); lineTo(end, bottom); lineTo(nut, bottom); close() }
    // Neck edge and a restrained shadow give the slab thickness.
    drawRect(Color.Black.copy(alpha = 0.17f), Offset(nut, top + 4.dp.toPx()), Size(end - nut, height))
    drawRect(Color(0xFFC59D62), Offset(nut, top - 2.dp.toPx()), Size(end - nut, height + 4.dp.toPx()))
    drawPath(board, Brush.verticalGradient(listOf(Color(0xFF35251F), Color(0xFF51352A), Color(0xFF30221E)), top, bottom))
    clipPath(board) { woodGrain(nut, top, end - nut, height, Color(0xFFC48D57).copy(alpha = 0.08f)) }
    drawLine(Color(0xFF977A51), Offset(nut, top), Offset(end, top), 1.dp.toPx())
    drawLine(Color(0xFF211812), Offset(nut, bottom), Offset(end, bottom), 2.dp.toPx())

    if (g.first == 0) drawHeadstock(left, nut, top, height)
    (g.first..g.last).filter { it > 0 }.forEach { f ->
        val x = left + width * g.right(f)
        drawLine(Color.Black.copy(alpha = 0.4f), Offset(x + 2.dp.toPx(), top), Offset(x + 2.dp.toPx(), bottom), 3.dp.toPx())
        drawLine(Color(0xFF717778), Offset(x, top), Offset(x, bottom), 3.6.dp.toPx())
        drawLine(Color(0xFFDCE4E3), Offset(x - 0.5.dp.toPx(), top), Offset(x - 0.5.dp.toPx(), bottom), 1.4.dp.toPx())
        val center = left + width * g.center(f)
        val ys = when (g.inlays(f)) { 1 -> listOf(top + height / 2); 2 -> listOf(top + height / 3, top + height * 2 / 3); else -> emptyList() }
        ys.forEach { y ->
            val radius = minOf(4.dp.toPx(), height / 30)
            drawCircle(Color(0xFF201B18), radius + 1.dp.toPx(), Offset(center, y))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFF5EAD2), Color(0xFFBBA780)), Offset(center - radius / 3, y - radius / 3), radius * 2), radius, Offset(center, y))
        }
    }
    if (g.first == 0) {
        // The ivory nut is a physical boundary. There is no fictitious wooden "zero fret" cell.
        drawRect(Color(0xFF8F826A), Offset(nut - 4.dp.toPx(), top), Size(7.dp.toPx(), height))
        drawRect(Brush.horizontalGradient(listOf(Color(0xFFB6AD91), Color(0xFFFFF5D9), Color(0xFFE0D5B8)), nut - 3.dp.toPx(), nut + 2.dp.toPx()),
            Offset(nut - 3.dp.toPx(), top), Size(5.dp.toPx(), height))
    }
    (1..6).forEach { s ->
        val y = top + height * g.stringCenter(s)
        val gauge = (0.65f + (s - 1) * 0.38f).dp.toPx()
        drawLine(Color.Black.copy(alpha = 0.5f), Offset(left, y + 1.5.dp.toPx()), Offset(end, y + 1.5.dp.toPx()), gauge + 1.dp.toPx())
        drawLine(if (s < 4) Color(0xFFD5DDE0) else Color(0xFFADB4B6), Offset(left, y), Offset(end, y), gauge)
        drawLine(Color(0xFFF5F8F9), Offset(left, y - gauge * 0.23f), Offset(end, y - gauge * 0.23f), maxOf(0.45.dp.toPx(), gauge * 0.25f))
        if (s >= 4) {
            var x = left
            while (x < end) {
                drawLine(Color(0xFF5F6669).copy(alpha = 0.7f), Offset(x, y - gauge / 2), Offset(x + gauge * 0.4f, y + gauge / 2), 0.45.dp.toPx())
                x += 2.8.dp.toPx()
            }
        }
    }
}

/** Six inline tuners, curved maple outline, shafts and string paths terminating at the posts. */
private fun DrawScope.drawHeadstock(left: Float, nut: Float, top: Float, height: Float) {
    val bottom = top + height
    val head = Path().apply {
        moveTo(nut, top)
        cubicTo(nut * 0.85f, top, nut * 0.81f, top - height * 0.05f, nut * 0.68f, top - height * 0.04f)
        cubicTo(nut * 0.46f, top + height * 0.10f, nut * 0.20f, top + height * 0.40f, nut * 0.07f, top + height * 0.69f)
        cubicTo(nut * -0.02f, bottom, nut * 0.14f, bottom + height * 0.06f, nut * 0.28f, bottom)
        cubicTo(nut * 0.50f, bottom - height * 0.10f, nut * 0.63f, bottom - height * 0.02f, nut * 0.76f, bottom)
        lineTo(nut, bottom)
        close()
    }
    drawPath(head, Brush.verticalGradient(listOf(Color(0xFFE5C48D), Color(0xFFC49555), Color(0xFFE3BD7B)), top, bottom))
    clipPath(head) { woodGrain(0f, top, nut, height, Color(0xFF795027).copy(alpha = 0.12f)) }
    drawPath(head, Color(0xFF916337), style = Stroke(1.4.dp.toPx()))
    val postRadius = minOf(5.5.dp.toPx(), height / 28, nut / 24)
    (1..6).forEach { s ->
        val x = nut * (0.20f + (6 - s) * 0.115f)
        val y = top + height * (s - 0.5f) / 6
        val keyX = maxOf(postRadius * 1.7f, x - nut * 0.09f)
        val keyY = y - height * 0.075f
        val metal = Brush.linearGradient(listOf(Color(0xFF70787A), Color(0xFFF1F4F4), Color(0xFF8B959B)), Offset(keyX - postRadius, keyY), Offset(keyX + postRadius, y))
        drawLine(Color(0xFF71797D), Offset(keyX, keyY), Offset(x, y), postRadius * 1.1f)
        drawLine(Color(0xFFE5EDEF), Offset(keyX, keyY), Offset(x, y), postRadius * 0.35f)
        drawRoundRect(metal, Offset(keyX - postRadius * 1.5f, keyY - postRadius), Size(postRadius * 3, postRadius * 1.8f), CornerRadius(postRadius * 0.6f))
        // The open-string touch region sits on these strings immediately before the nut.
        drawLine(Color(0xFFBDC5C7), Offset(x, y), Offset(nut, y), (0.65f + (s - 1) * 0.38f).dp.toPx())
        drawCircle(Color(0xFF7A674F), postRadius * 1.3f, Offset(x, y))
        drawCircle(metal, postRadius, Offset(x, y))
        drawCircle(Color(0xFF475055), postRadius * 0.43f, Offset(x, y))
        drawLine(Color(0xFFE1E7E7), Offset(x - postRadius * 0.5f, y), Offset(x + postRadius * 0.5f, y), 0.8.dp.toPx())
    }
}

private fun DrawScope.woodGrain(left: Float, top: Float, width: Float, height: Float, color: Color) {
    repeat(16) { row ->
        val path = Path()
        repeat(25) { step ->
            val x = left + width * step / 24
            val y = top + height * (row + 0.5f) / 16 + sin(step * 0.45f + row * 1.7f) * height * 0.008f
            if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(if (row % 4 == 0) 0.9.dp.toPx() else 0.45.dp.toPx()))
    }
}
