package com.a3322505a.guitarlearning.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

data class PilotControlsUi(val mode: PilotMode, val bpm: Int, val playing: Boolean, val canPlay: Boolean,
    val completed: Boolean, val loop: Boolean, val metronome: Boolean, val compare: Boolean)
data class PilotMenuUi(val resume: Boolean, val next: Map<PilotMode, Int?>, val enabled: Map<PilotMode, Boolean>, val results: List<String>, val resumeMode: PilotMode? = null)

@Composable
internal fun PilotMenu(state: PilotMenuUi, start: (PilotMode) -> Unit) {
    Panel("短谱试用", "每次5–10分钟，分3–5次完成；先测读，再练习，最后读陌生谱。") {
        Text("五线谱与TAB使用同一份音符。先四分音符，再练二分音符和休止。")
        (if(state.resume) listOf(state.resumeMode ?: PilotMode.SLOW) else PilotMode.entries).forEach { mode ->
            val next = state.next[mode]
            Button(onClick = { start(mode) }, enabled = state.resume || state.enabled[mode] == true) {
                Text(if (state.resume) "继续${mode.title}" else if (next == null) "${mode.title} · 已完成" else "${mode.title} · 第${next + 1}/8段")
            }
            if (!state.resume && next != null && state.enabled[mode] != true) Text("先完成 TAB 入门；五线谱段还需看过吉他记谱八度说明。")
        }
    }
    if (state.results.isNotEmpty()) Panel("试用记录") { state.results.forEach { Text(it) } }
}

@Composable
internal fun PilotTrainingScreen(state: TrainingUiState, onEvent: (TrainingEvent) -> Unit) {
    val pilot = requireNotNull(state.pilot)
    var comment by remember(state.taskId) { mutableStateOf("") }
    BoxWithConstraints(Modifier.fillMaxSize().displayCutoutPadding().padding(8.dp)) {
        val boardHeight = maxHeight * 0.43f
        val scoreHeight = if (pilot.mode == PilotMode.GUITAR) maxHeight * 0.55f else (maxHeight * 0.32f).coerceIn(80.dp,120.dp)
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onEvent(TrainingEvent.Back) }) { Text("‹ 返回") }
                    Text(state.title, Modifier.weight(1f))
                    if (pilot.completed) Button(onClick = { onEvent(TrainingEvent.PilotFinish(null, comment)) }, enabled = !state.busy) { Text("完成此段") }
                    Text("${pilot.bpm} BPM")
                    TextButton(onClick = { onEvent(TrainingEvent.PilotTempo((pilot.bpm - 5).coerceAtLeast(40))) }, enabled = !pilot.playing, modifier = Modifier.width(40.dp), contentPadding = PaddingValues(0.dp)) { Text("−") }
                    TextButton(onClick = { onEvent(TrainingEvent.PilotTempo((pilot.bpm + 5).coerceAtMost(80))) }, enabled = !pilot.playing, modifier = Modifier.width(40.dp), contentPadding = PaddingValues(0.dp)) { Text("＋") }
                    if (pilot.canPlay) TextButton(onClick = { onEvent(TrainingEvent.PilotPlay) }) { Text(if (pilot.playing) "暂停" else "试听 / 继续") }
                }
                state.notation?.let { NotationView(it, if (pilot.mode == PilotMode.GUITAR) -1 else state.notationIndex, Modifier.fillMaxWidth().height(scoreHeight)) }
                if (pilot.canPlay) Row {
                    TextButton(onClick = { onEvent(TrainingEvent.PilotLoop) }) { Text(if (pilot.loop) "循环 ✓" else "循环") }
                    TextButton(onClick = { onEvent(TrainingEvent.PilotCompare) }) { Text(if (pilot.compare) "收起对照" else "对照谱") }
                }
                if (pilot.compare) state.notation?.score?.let { NotationView(it.notation(if (state.notation.kind == NotationKind.TAB) NotationKind.STAFF else NotationKind.TAB),state.notationIndex,Modifier.fillMaxWidth().height(120.dp)) }
                if (pilot.mode == PilotMode.GUITAR) {
                    TextButton(onClick = { onEvent(TrainingEvent.PilotMetronome) }) { Text(if (pilot.metronome) "停止节拍器" else "节拍器 · 一小节预备拍") }
                    Row {
                        listOf("顺畅", "有停顿", "困难").forEach { rating -> Button(onClick = { onEvent(TrainingEvent.PilotFinish(rating, comment)) }, enabled = !state.busy, modifier = Modifier.padding(end = 6.dp)) { Text(rating) } }
                    }
                    OutlinedTextField(comment,{ comment = it.take(200) },label = { Text("可选：哪里停顿？") },singleLine = true)
                } else if (state.wrong) Text("再看当前音，答对后继续。")
            }
            state.board?.let { TeachingFretboard(it,{ onEvent(TrainingEvent.Position(it)) },Modifier.fillMaxWidth().height(boardHeight)) }
        }
    }
}
