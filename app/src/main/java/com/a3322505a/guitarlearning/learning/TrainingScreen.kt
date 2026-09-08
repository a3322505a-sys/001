package com.a3322505a.guitarlearning.learning

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.a3322505a.guitarlearning.ui.theme.*

@Composable
fun TrainingScreen(state: TrainingUiState, onEvent: (TrainingEvent) -> Unit) {
    if (state.pilot != null) { PilotTrainingScreen(state, onEvent); return }
    if (state.taskId == null) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.Center) {
            Text(state.summary)
            Button(onClick = { onEvent(TrainingEvent.Back) }) { Text("返回") }
        }
        return
    }
    var legendOpen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().displayCutoutPadding().padding(horizontal = 12.dp, vertical = 4.dp)) {
        TrainingToolbar(state, onEvent) { legendOpen = true }
        key(state.taskId) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.hasChord -> ChordTaskLayout(state, onEvent, state.showLegend || legendOpen) {
                        legendOpen = false; onEvent(TrainingEvent.LegendSeen)
                    }
                    state.board != null -> BoardTaskLayout(state, onEvent)
                    else -> SymbolTaskLayout(state, onEvent)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrainingToolbar(state: TrainingUiState, onEvent: (TrainingEvent) -> Unit, showLegend: () -> Unit) {
    var menuOpen by remember(state.taskId) { mutableStateOf(false) }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = { onEvent(TrainingEvent.Back) },
            modifier = Modifier.semantics { contentDescription = "保存并结束本轮返回" }) { Text("‹", fontSize = 28.sp) }
        state.roundProgress?.let { Text(it, Modifier.align(Alignment.CenterVertically), fontSize = 15.sp) }
        if (state.hasChord) IconButton(onClick = { onEvent(TrainingEvent.RotateChord) }, enabled = !state.busy,
            modifier = Modifier.semantics { contentDescription = "旋转和弦图" }) { Text("↻", fontSize = 25.sp) }
        if (state.canReplay) IconButton(onClick = { onEvent(TrainingEvent.Replay) },
            modifier = Modifier.semantics { contentDescription = "重听题目" }) { Text("♫", fontSize = 24.sp) }
        if (state.canNext) Button(onClick = { onEvent(TrainingEvent.Next) }) { Text("下一题") }
        if (state.busy) CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterVertically), strokeWidth = 2.dp)
        Box {
            IconButton(onClick = { menuOpen = true; onEvent(TrainingEvent.Obstructed) },
                modifier = Modifier.semantics { contentDescription = "训练菜单" }) { Text("⋯", fontSize = 26.sp) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (state.hasChord) {
                    DropdownMenuItem(text = { Text("手指颜色说明") }, onClick = { showLegend(); menuOpen = false })
                    FingeringMode.entries.forEach { mode ->
                        DropdownMenuItem(text = { Text("指法：${mode.title}") },
                            onClick = { onEvent(TrainingEvent.Fingering(mode.id)); menuOpen = false })
                    }
                }
                if (state.canHint) DropdownMenuItem(text = { Text(state.hintLabel) }, enabled = !state.busy,
                    onClick = { menuOpen = false; onEvent(TrainingEvent.Hint) })
                if (state.canReplay) DropdownMenuItem(text = { Text("重听题目") },
                    onClick = { menuOpen = false; onEvent(TrainingEvent.Replay) })
            }
        }
    }
}

@Composable
internal fun TrainingPrompt(state: TrainingUiState) {
    if (state.title.isNotBlank()) Text(state.title, fontSize = 22.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { contentDescription = state.accessibilityPrompt })
}

@Composable
internal fun TrainingAudioNotice(state: TrainingUiState, onEvent: (TrainingEvent) -> Unit) {
    state.audio.message?.let { Text(it, fontSize = 13.sp, color = LocalGuitarColors.current.accent) }
    if (state.audio.failed) TextButton(onClick = { onEvent(TrainingEvent.RetryAudio) }, enabled = !state.busy) { Text("重试声音") }
    if (!state.soundEnabled) TextButton(onClick = { onEvent(TrainingEvent.EnableSound) }, enabled = !state.busy) { Text("开启声音") }
}
@Composable
internal fun TrainingMessage(state: TrainingUiState, modifier: Modifier = Modifier, scrollable: Boolean = false) {
    val message = state.message ?: return
    val colors = LocalGuitarColors.current
    val ink = if (state.wrong) colors.error.ink else colors.ink
    Surface(modifier.fillMaxWidth(), shape = CutCornerShape(5.dp),
        color = if (state.wrong) colors.error.background else colors.surface,
        border = BorderStroke(1.dp, if (state.wrong) colors.error.ink else colors.border)) {
        Text(message, color = ink, fontSize = 15.sp, lineHeight = 20.sp,
            modifier = (if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AnswerOptions(options: List<AnswerOptionUi>, answer: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalGuitarColors.current
    val optionStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 19.sp)
    val width = answerOptionWidth(options)
    BoxWithConstraints(modifier) {
    val optionWidth = minOf(maxWidth, maxOf(64.dp, width))
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { option ->
            val confirmed = option.role == MarkRole.CORRECT
            val wrong = option.role == MarkRole.WRONG
            val answerShown = option.role == MarkRole.TARGET
            val optionColors = when {
                wrong -> colors.error
                confirmed -> colors.mastered
                answerShown -> colors.available
                else -> StateColors(colors.surface, colors.ink)
            }
            OutlinedButton(onClick = { answer(option.value) },
                enabled = option.enabled,
                modifier = Modifier.width(optionWidth), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp), shape = CutCornerShape(4.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = optionColors.background, contentColor = optionColors.ink,
                    disabledContainerColor = optionColors.background, disabledContentColor = optionColors.ink)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(option.value, style = optionStyle, modifier = Modifier.weight(1f, fill = false))
                    Box(Modifier.width(12.dp), contentAlignment = Alignment.Center) {
                        if (wrong || confirmed) Text(if (wrong) "×" else "✓", fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun answerOptionWidth(options: List<AnswerOptionUi>): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelLarge.copy(fontSize = 19.sp)
    return maxOf(64.dp, with(density) { options.maxOf { measurer.measure(it.value, style).size.width }.toDp() } + 30.dp)
}

@Composable
internal fun TabPrompt(c: Coordinate, modifier: Modifier = Modifier) {
    val colors = LocalGuitarColors.current
    Box(modifier.height(64.dp).background(colors.surface).semantics { contentDescription = "TAB：${c.label}" }) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            (1..6).forEach { s -> val y = size.height * (s - 0.5f) / 6; drawLine(colors.border, Offset(0f, y), Offset(size.width, y), 1.dp.toPx()) }
        }
        Column(Modifier.fillMaxSize()) { (1..6).forEach { s ->
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (s == c.string) Text(c.fret.toString(), modifier = Modifier.background(colors.surface).padding(horizontal = 5.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        } }
    }
}
