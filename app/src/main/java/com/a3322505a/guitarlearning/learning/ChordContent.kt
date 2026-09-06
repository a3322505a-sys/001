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

val FingerColors = listOf(Color(0xFF60D5FA), Color(0xFFFFD373), Color(0xFFC3A3FF), Color(0xFFFF9EC5))

@Composable
fun ChordOverlay(state: ChordOverlayUiState, g: TeachingGeometry, left: Dp, top: Dp, width: Dp, height: Dp) {
    Canvas(Modifier.fillMaxSize()) {
        val gap = height.toPx() / 6
        state.fingers.forEach { finger ->
            val x = left.toPx() + width.toPx() * g.center(finger.fret)
            val y = top.toPx() + gap * (finger.firstString - 0.5f)
            val w = width.toPx() * (g.right(finger.fret) - g.left(finger.fret)) * 0.55f
            val h = gap * (finger.lastString - finger.firstString + 0.56f)
            val start = Offset(x - w / 2, y - gap * 0.28f)
            drawRoundRect(Color.Black.copy(alpha = 0.7f), start, Size(w, h), CornerRadius(3.dp.toPx()), style = Stroke(6.dp.toPx()))
            drawRoundRect(FingerColors[finger.finger - 1], start, Size(w, h), CornerRadius(3.dp.toPx()), style = Stroke(3.dp.toPx()))
        }
        state.tones.forEach { tone ->
            val c = tone.coordinate
            val center = Offset(left.toPx() + width.toPx() * g.center(c.fret), top.toPx() + gap * (c.string - 0.5f))
            if (tone.dot) drawCircle(Color.White, 2.dp.toPx(), center)
            if (tone.rootRing) drawCircle(Color.White, 12.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
        }
    }
    state.openMutedLabels.forEach { (string, label) ->
        Text(label, color = Color.White, fontSize = 14.sp,
            modifier = Modifier.absoluteOffset(x = 2.dp, y = top + height * ((string - 1) / 6f))
                .background(Color.Black.copy(alpha = 0.8f)).padding(horizontal = 4.dp))
    }
    state.tones.filter { it.label.isNotEmpty() }.forEach { tone ->
        val c = tone.coordinate
        Box(Modifier.absoluteOffset(x = left + width * g.left(c.fret), y = top + height * ((c.string - 1) / 6f))
            .width(width * (g.right(c.fret) - g.left(c.fret))).height(height / 6), contentAlignment = Alignment.Center) {
            Text(tone.label, color = Color.White, fontSize = 13.sp,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = 2.dp))
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
fun FingeringSettings(selectedId: String, busy: Boolean, select: (String) -> Unit) {
    Text("指法显示")
    FingeringMode.entries.forEach { mode ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selectedId == mode.id, onClick = { select(mode.id) }, enabled = !busy)
            Text(mode.title)
        }
    }
}

@Composable
fun ChordExamples(state: ChordExamplesUiState, select: (String) -> Unit, play: () -> Unit, fingering: (String) -> Unit) {
    state.choices.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        row.forEach { item -> OutlinedButton(onClick = { select(item.id) }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text(item.title, fontSize = 13.sp) } }
    } }
    Text("${state.title} · O 空弦 / X 不弹；这里查看的是推荐形态。", fontSize = 13.sp)
    Text("青蓝 1 食指 · 金黄 2 中指\n浅紫 3 无名指 · 粉色 4 小指", fontSize = 13.sp)
    TeachingFretboard(state.board, {}, Modifier.fillMaxWidth().height(250.dp))
    Button(onClick = play, enabled = state.soundEnabled) { Text("试听形态") }
    state.audio.message?.let { Text(it, fontSize = 13.sp) }
    FingeringSettings(state.fingeringMode, state.busy, fingering)
    Text("颜色与手指固定对应；音名视图的白环表示根音。屏幕逐点操作不识别真实手指或按弦力度。", fontSize = 13.sp)
}

@Composable
fun ChordInputControls(state: ChordControlsUiState, onEvent: (TrainingEvent) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("第 ${state.string} 弦 · ${state.progress}", fontSize = 14.sp)
        OutlinedButton(onClick = { onEvent(TrainingEvent.OpenString) }, enabled = state.enabled) { Text("空弦 O") }
        OutlinedButton(onClick = { onEvent(TrainingEvent.MuteString) }, enabled = state.enabled) { Text("不弹 X") }
        if (state.canDemonstrate) TextButton(onClick = { onEvent(TrainingEvent.Demonstrate) }) { Text("试听形态") }
    }
}
