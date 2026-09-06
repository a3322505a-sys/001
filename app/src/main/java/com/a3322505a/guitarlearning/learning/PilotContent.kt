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
        Text("共8段，跨练法也不重复测读素材。复测沿用同谱式基线的练法；练习段可自由选择。")
        (if(state.resume) listOf(state.resumeMode ?: PilotMode.SLOW) else PilotMode.entries).forEach { mode ->
            val next = state.next[mode]
            Button(onClick = { start(mode) }, enabled = state.resume || state.enabled[mode] == true) {
                Text(if (state.resume) "继续${mode.title}" else if (next == null) "${mode.title} · 已完成" else "${mode.title} · 第${next + 1}/8段")
            }
            if (!state.resume && next != null && state.enabled[mode] != true) Text("需满足读谱前置；复测须沿用同谱式基线的练法。")
        }
    }
    if (state.results.isNotEmpty()) Panel("试用记录") { state.results.forEach { Text(it) } }
}

@Composable
internal fun PilotTrainingScreen(state: TrainingUiState, onEvent: (TrainingEvent) -> Unit) {
    val pilot = requireNotNull(state.pilot)
    var comment by remember(state.taskId) { mutableStateOf("") }
    var menuOpen by remember(state.taskId) { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize().displayCutoutPadding().padding(horizontal = 12.dp, vertical = 4.dp)) {
        val boardHeight = maxHeight * 0.48f
        val split = maxWidth >= 600.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onEvent(TrainingEvent.Back) }) { Text("‹ 返回") }
                Text(state.title, Modifier.weight(1f))
                if (pilot.completed) Button(onClick = { onEvent(TrainingEvent.PilotFinish(null, comment)) }, enabled = !state.busy) { Text("完成此段") }
            }
            val score: @Composable ColumnScope.() -> Unit = {
                state.notation?.let { notation ->
                    NotationView(notation, if (pilot.mode == PilotMode.GUITAR) -1 else state.notationIndex,
                        Modifier.fillMaxWidth().height(if (pilot.mode == PilotMode.GUITAR) 144.dp else 104.dp))
                    if (pilot.compare) notation.score?.let {
                        NotationView(it.notation(if (notation.kind == NotationKind.TAB) NotationKind.STAFF else NotationKind.TAB),
                            state.notationIndex, Modifier.fillMaxWidth().height(120.dp))
                    }
                }
            }
            val controls: @Composable ColumnScope.() -> Unit = {
                PilotTempoControls(pilot, onEvent)
                if (pilot.canPlay) Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onEvent(TrainingEvent.PilotPlay) }) { Text(if (pilot.playing) "暂停" else "试听 / 继续") }
                    Box {
                        TextButton(onClick = { menuOpen = true }) { Text("⋯") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(if (pilot.loop) "循环 ✓" else "循环") }, onClick = { menuOpen = false; onEvent(TrainingEvent.PilotLoop) })
                            DropdownMenuItem(text = { Text(if (pilot.compare) "收起对照" else "对照谱") }, onClick = { menuOpen = false; onEvent(TrainingEvent.PilotCompare) })
                        }
                    }
                }
                if (pilot.mode == PilotMode.GUITAR) {
                    TextButton(onClick = { onEvent(TrainingEvent.PilotMetronome) }) { Text(if (pilot.metronome) "停止节拍器" else "节拍器 · 一小节预备拍") }
                    PilotRatings(state.busy, comment, onEvent)
                    OutlinedTextField(comment, { comment = it.take(200) }, label = { Text("可选：哪里停顿？") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                } else if (state.wrong) TrainingMessage(state.copy(message = "再看当前音，答对后继续。"))
            }
            if (split) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(0.64f).fillMaxHeight().verticalScroll(rememberScrollState()), content = score)
                    Column(Modifier.weight(0.36f).fillMaxHeight().verticalScroll(rememberScrollState()), content = controls)
                }
            } else {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) { score(); controls() }
            }
            state.board?.let { TeachingFretboard(it, { onEvent(TrainingEvent.Position(it)) }, Modifier.fillMaxWidth().height(boardHeight)) }
        }
    }
}

@Composable
private fun PilotTempoControls(pilot: PilotControlsUi, onEvent: (TrainingEvent) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${pilot.bpm} BPM")
        TextButton(onClick = { onEvent(TrainingEvent.PilotTempo((pilot.bpm - 5).coerceAtLeast(40))) }, enabled = !pilot.playing,
            modifier = Modifier.width(48.dp), contentPadding = PaddingValues(0.dp)) { Text("−") }
        TextButton(onClick = { onEvent(TrainingEvent.PilotTempo((pilot.bpm + 5).coerceAtMost(80))) }, enabled = !pilot.playing,
            modifier = Modifier.width(48.dp), contentPadding = PaddingValues(0.dp)) { Text("＋") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PilotRatings(busy: Boolean, comment: String, onEvent: (TrainingEvent) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("顺畅", "有停顿", "困难").forEach { rating ->
            Button(onClick = { onEvent(TrainingEvent.PilotFinish(rating, comment)) }, enabled = !busy,
                contentPadding = PaddingValues(horizontal = 12.dp)) { Text(rating) }
        }
    }
}
