package com.a3322505a.guitarlearning.learning

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a3322505a.guitarlearning.core.MusicFacts

val FingerColors = listOf(Color(0xFF60D5FA), Color(0xFFFFD373), Color(0xFFC3A3FF), Color(0xFFFF9EC5))

fun chordVisible(a: ActiveTask): Boolean = a.task.guided || a.hintLevel >= 2 || a.phase != Phase.ANSWERING

@Composable
fun ChordOverlay(active: ActiveTask, mode: FingeringMode, g: TeachingGeometry, left: Dp, top: Dp, width: Dp, height: Dp) {
    val shape = active.task.chord ?: return
    val visible = chordVisible(active)
    if (visible) Canvas(Modifier.fillMaxSize()) {
        val gap = height.toPx() / 6
        if (mode != FingeringMode.NOTES) shape.fingers.forEach { finger ->
            val x = left.toPx() + width.toPx() * g.center(finger.fret)
            val y = top.toPx() + gap * (finger.firstString - 0.5f)
            val w = width.toPx() * (g.right(finger.fret) - g.left(finger.fret)) * 0.55f
            val h = gap * (finger.lastString - finger.firstString + 0.56f)
            val start = Offset(x - w / 2, y - gap * 0.28f)
            drawRoundRect(Color.Black.copy(alpha = 0.7f), start, Size(w, h), CornerRadius(3.dp.toPx()), style = Stroke(6.dp.toPx()))
            drawRoundRect(FingerColors[finger.finger - 1], start, Size(w, h), CornerRadius(3.dp.toPx()), style = Stroke(3.dp.toPx()))
        }
        shape.sounding().filter { it.fret > 0 }.forEach { c ->
            val center = Offset(left.toPx() + width.toPx() * g.center(c.fret), top.toPx() + gap * (c.string - 0.5f))
            if (mode == FingeringMode.COLORS) drawCircle(Color.White, 2.dp.toPx(), center)
            if (mode == FingeringMode.NOTES && MusicFacts.midi(c.string, c.fret) % 12 == shape.root)
                drawCircle(Color.White, 12.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
        }
    }
    (1..6).forEach { string ->
        val fret = shape.fret(string)
        val confirmedMute = active.inputs.any { input -> input.symbol == "X" && input.result != ClickResult.WRONG &&
            input.targetIndex?.let { active.task.sequence.getOrNull(it)?.string } == string }
        if ((visible || confirmedMute || Coordinate(string, 0) in active.confirmed) && (fret == null || fret == 0))
            Text(if (fret == null) "X" else "O", color = Color.White, fontSize = 14.sp,
                modifier = Modifier.absoluteOffset(x = 2.dp, y = top + height * ((string - 1) / 6f))
                    .background(Color.Black.copy(alpha = 0.8f)).padding(horizontal = 4.dp))
        if (visible && fret != null && fret > 0 && mode != FingeringMode.COLORS) {
            val c = Coordinate(string, fret)
            Box(Modifier.absoluteOffset(x = left + width * g.left(fret), y = top + height * ((string - 1) / 6f))
                .width(width * (g.right(fret) - g.left(fret))).height(height / 6), contentAlignment = Alignment.Center) {
                Text(if (mode == FingeringMode.NUMBERS) shape.fingerAt(c)?.toString().orEmpty() else MusicFacts.note(string, fret),
                    color = Color.White, fontSize = 13.sp, modifier = Modifier.background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = 2.dp))
            }
        }
    }
}

@Composable
fun FingerLegend(onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("食指", "中指", "无名指", "小指").forEachIndexed { index, name ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(12.dp).background(FingerColors[index]))
                Text("${index + 1} $name", fontSize = 12.sp)
            }
        }
        TextButton(onClick = onClose, contentPadding = PaddingValues(0.dp)) { Text("收起", fontSize = 12.sp) }
    }
}

@Composable
fun FingeringSettings(state: LearnerState, busy: Boolean, model: TrainingViewModel) {
    Text("指法显示")
    FingeringMode.entries.forEach { mode ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = FingeringMode.fromId(state.fingeringMode) == mode, onClick = { model.fingering(mode.id) }, enabled = !busy)
            Text(mode.title)
        }
    }
}

@Composable
fun ChordExamples(state: LearnerState, busy: Boolean, model: TrainingViewModel) {
    var shapeId by rememberSaveable { mutableStateOf(ChordShapes.am.id) }
    val shape = ChordShapes.get(shapeId)
    LaunchedEffect(shapeId) { model.viewChord(shapeId) }
    ChordShapes.all.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        row.forEach { item -> OutlinedButton(onClick = { shapeId = item.id }, enabled = !busy, modifier = Modifier.weight(1f)) { Text(item.title, fontSize = 13.sp) } }
    } }
    Text("${shape.title} · O 空弦 / X 不弹；这里查看的是推荐形态。", fontSize = 13.sp)
    Text("青蓝 1 食指 · 金黄 2 中指\n浅紫 3 无名指 · 粉色 4 小指", fontSize = 13.sp)
    val task = remember(shapeId) { ChordLessons.make(shape, "chord-am", TaskSource.DEMONSTRATION) }
    TeachingFretboard(ActiveTask(task), false, {}, Modifier.fillMaxWidth().height(250.dp), FingeringMode.fromId(state.fingeringMode))
    Button(onClick = { model.playShape(shape) }, enabled = state.soundEnabled) { Text("试听形态") }
    FingeringSettings(state, busy, model)
    Text("颜色与手指固定对应；音名视图的白环表示根音。屏幕逐点操作不识别真实手指或按弦力度。", fontSize = 13.sp)
}

@Composable
fun ChordInputControls(active: ActiveTask, busy: Boolean, model: TrainingViewModel) {
    val rule = active.task.sequence.getOrNull(active.sequenceIndex) ?: return
    val string = rule.coordinate?.string ?: rule.string ?: return
    val enabled = !busy && active.phase in listOf(Phase.ANSWERING, Phase.CORRECTING)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("第 $string 弦 · ${active.sequenceIndex + 1}/${active.task.sequence.size}", fontSize = 14.sp)
        OutlinedButton(onClick = { model.answer(active.task.id, coordinate = Coordinate(string, 0)) }, enabled = enabled) { Text("空弦 O") }
        OutlinedButton(onClick = { model.answer(active.task.id, symbol = "X") }, enabled = enabled) { Text("不弹 X") }
        if (chordVisible(active)) TextButton(onClick = { active.task.chord?.let(model::playShape) }) { Text("试听形态") }
    }
}
