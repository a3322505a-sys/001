package com.a3322505a.guitarlearning.learning

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.ui.theme.LocalGuitarColors

/** Position-only notation: no barlines, time signature or claim of a rhythmic measure. */
@Composable
fun NotationView(notation: NotationPrompt, index: Int, modifier: Modifier = Modifier) {
    val colors = LocalGuitarColors.current
    Canvas(modifier.semantics { contentDescription = if (notation.kind == NotationKind.TAB) "TAB，依次读取第${index + 1}个弦品数字" else "高音谱号吉他谱，实际发声低八度，第${index + 1}个音" }) {
        val gap = size.height / if (notation.score != null) 10f else 7.8f
        val top = gap * 1.6f
        val left = if (notation.score != null) 76.dp.toPx() else 58.dp.toPx()
        val right = size.width - 12.dp.toPx()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.ink.toArgb(); textAlign = Paint.Align.CENTER; textSize = 18.dp.toPx() }
        val count = if (notation.kind == NotationKind.TAB) 6 else 5
        repeat(count) { line -> drawLine(colors.muted, Offset(12.dp.toPx(), top + line * gap), Offset(right, top + line * gap), 1.dp.toPx()) }
        if (notation.kind == NotationKind.TAB) {
            paint.textSize = 11.dp.toPx()
            listOf("T", "A", "B").forEachIndexed { i, s -> drawContext.canvas.nativeCanvas.drawText(s, 28.dp.toPx(), top + (i * 1.5f + 1) * gap, paint) }
            paint.textSize = 18.dp.toPx()
        } else {
            // A vector G clef. Its inner spiral encircles the second line from the bottom.
            val x = 29.dp.toPx(); val y = top + 3 * gap
            fun px(a: Float) = x + a * gap
            fun py(a: Float) = y + a * gap
            val clef = Path().apply {
                moveTo(px(0.6f), py(0.4f))
                cubicTo(px(1.2f), py(-0.8f), px(-0.7f), py(-1.2f), px(-0.9f), py(0f))
                cubicTo(px(-1.3f), py(1.4f), px(1.2f), py(1.8f), px(1.4f), py(0.3f))
                cubicTo(px(1.6f), py(-1.3f), px(-0.7f), py(-1.7f), px(-1.2f), py(-0.5f))
                cubicTo(px(-2f), py(-2.3f), px(1.7f), py(-3.1f), px(0.3f), py(-4.3f))
                cubicTo(px(-0.7f), py(-3.2f), px(0.3f), py(-1f), px(0.6f), py(1.8f))
                cubicTo(px(0.9f), py(3f), px(-0.8f), py(3f), px(-0.7f), py(2.1f))
            }
            drawPath(clef, colors.ink, style = Stroke(2.dp.toPx()))
            drawCircle(colors.ink, 2.3.dp.toPx(), Offset(px(-0.7f), py(2.1f)))
        }
        notation.score?.let { score ->
            paint.textSize = 16.dp.toPx()
            drawContext.canvas.nativeCanvas.drawText("4", 56.dp.toPx(), top + 1.8f * gap, paint)
            drawContext.canvas.nativeCanvas.drawText("4", 56.dp.toPx(), top + 3.7f * gap, paint)
            (1..score.bars).forEach { bar ->
                val x = left + (right-left) * bar / score.bars
                drawLine(colors.ink,Offset(x,top),Offset(x,top+(count-1)*gap),1.dp.toPx())
            }
            score.events.filter { it.midi == null }.forEach { e ->
                val x = left + (right-left) * (e.tick + 1f) / (score.bars * 16)
                val y = top + 2*gap
                // Quarter rest zigzag; half rest rests above the middle staff line.
                if(e.duration == 8) drawRect(colors.ink,Offset(x-5.dp.toPx(),y-4.dp.toPx()),Size(10.dp.toPx(),4.dp.toPx()))
                else if (e.duration == 2) {
                    drawCircle(colors.ink,2.5.dp.toPx(),Offset(x+2.dp.toPx(),y-gap/2))
                    drawLine(colors.ink,Offset(x+5.dp.toPx(),y-gap/2),Offset(x-2.dp.toPx(),y+gap),1.8.dp.toPx())
                }
                else drawPath(Path().apply { moveTo(x-3.dp.toPx(),y-gap); lineTo(x+3.dp.toPx(),y-gap/2); lineTo(x-3.dp.toPx(),y); lineTo(x+3.dp.toPx(),y+gap/2); quadraticBezierTo(x-7.dp.toPx(),y+gap/3,x-2.dp.toPx(),y+gap) },colors.ink,style=Stroke(2.dp.toPx()))
            }
        }
        notation.pitches.indices.forEach { i ->
            val event = notation.score?.notes?.get(i)
            val x = if (event != null) left + (right-left) * (event.tick + 1f) / (notation.score!!.bars * 16)
                else left + (right - left) * (i + 0.5f) / notation.pitches.size
            if (i == index) drawCircle(colors.accent, 3.dp.toPx(), Offset(x, 4.dp.toPx()))
            if (notation.kind == NotationKind.TAB) {
                val c = notation.coordinates[i]; val y = top + (c.string - 1) * gap
                drawRect(colors.background, Offset(x - 12.dp.toPx(), y - 10.dp.toPx()), Size(24.dp.toPx(), 20.dp.toPx()))
                drawContext.canvas.nativeCanvas.drawText(c.fret.toString(), x, y + 6.dp.toPx(), paint)
                if(event != null) {
                    val stemY = top + 5.7f * gap
                    drawLine(colors.ink,Offset(x,stemY),Offset(x,stemY+gap),1.dp.toPx())
                    if(event.duration == 8) drawOval(colors.ink,Offset(x-4.dp.toPx(),stemY-2.dp.toPx()),Size(8.dp.toPx(),4.dp.toPx()),style=Stroke(1.dp.toPx()))
                    else drawOval(colors.ink,Offset(x-4.dp.toPx(),stemY-2.dp.toPx()),Size(8.dp.toPx(),4.dp.toPx()))
                    if(event.duration == 2) drawLine(colors.ink,Offset(x,stemY+gap),Offset(x+5.dp.toPx(),stemY+gap/2),2.dp.toPx())
                }
            } else {
                val written = notation.writtenPitches[i]
                val step = staffStep(written)
                val bottom = top + 4 * gap
                val y = bottom - step * gap / 2
                if (step < 0) for (ledger in -2 downTo step step 2) drawLine(colors.ink, Offset(x - 12.dp.toPx(), bottom - ledger * gap / 2), Offset(x + 12.dp.toPx(), bottom - ledger * gap / 2), 1.dp.toPx())
                if (step > 8) for (ledger in 10..step step 2) drawLine(colors.ink, Offset(x - 12.dp.toPx(), bottom - ledger * gap / 2), Offset(x + 12.dp.toPx(), bottom - ledger * gap / 2), 1.dp.toPx())
                // Stemless noteheads intentionally communicate pitch and order, without invented durations.
                if(event?.duration == 8) drawOval(colors.ink, Offset(x - 6.dp.toPx(), y - 3.8.dp.toPx()), Size(12.dp.toPx(), 7.6.dp.toPx()),style=Stroke(1.5.dp.toPx()))
                else drawOval(colors.ink, Offset(x - 6.dp.toPx(), y - 3.8.dp.toPx()), Size(12.dp.toPx(), 7.6.dp.toPx()))
                if(event != null) {
                    val down = step >= 4
                    val stemX = x + if(down) -5.dp.toPx() else 5.dp.toPx()
                    val endY = y + if(down) 2.7f*gap else -2.7f*gap
                    drawLine(colors.ink,Offset(stemX,y),Offset(stemX,endY),1.3.dp.toPx())
                    if(event.duration == 2) drawLine(colors.ink,Offset(stemX,endY),Offset(stemX+6.dp.toPx(),endY+if(down) -gap else gap),2.dp.toPx())
                }
            }
        }
    }
}

/** Diatonic staff step above written E4, bottom line of the treble staff. */
internal fun staffStep(midi: Int): Int {
    val natural = mapOf(0 to 0, 2 to 1, 4 to 2, 5 to 3, 7 to 4, 9 to 5, 11 to 6)
    return (midi / 12 - 1) * 7 + requireNotNull(natural[midi % 12]) - (4 * 7 + 2)
}
