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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a3322505a.guitarlearning.ui.theme.*

@Composable
fun TrainingScreen(state: TrainingUiState, onEvent: (TrainingEvent) -> Unit) {
    if (state.pilot != null) { PilotTrainingScreen(state, onEvent); return }
    val colors = LocalGuitarColors.current
    if (state.taskId == null) {
        Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(state.summary)
            Button(onClick = { onEvent(TrainingEvent.Back) }) { Text("返回") }
        }
        return
    }
    var menuOpen by remember(state.taskId) { mutableStateOf(false) }
    var legendOpen by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize().displayCutoutPadding().padding(horizontal = 12.dp, vertical = 4.dp)) {
        val fixedBoardHeight = maxHeight * 0.48f
        val messageHeight = (maxHeight * 0.24f).coerceIn(48.dp, 88.dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { onEvent(TrainingEvent.Back) }, modifier = Modifier.semantics { contentDescription = "暂停并返回" }) {
                    Text("‹", fontSize = 28.sp)
                }
                Text(state.title + if (state.soundEnabled) "  ♫" else "", fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    modifier = Modifier.weight(1f).clickable(enabled = state.canReplay, onClickLabel = "重听题目") { onEvent(TrainingEvent.Replay) })
                state.tab?.let { TabPrompt(it, Modifier.width(144.dp)) }
                if (state.canNext) Button(onClick = { onEvent(TrainingEvent.Next) }) { Text("下一题") }
                if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.semantics { contentDescription = "训练菜单" }) {
                        Text("⋯", fontSize = 26.sp)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (state.hasChord) {
                            DropdownMenuItem(text = { Text("手指颜色说明") }, onClick = { legendOpen = true; menuOpen = false })
                            FingeringMode.entries.forEach { mode -> DropdownMenuItem(text = { Text("指法：${mode.title}") }, onClick = { onEvent(TrainingEvent.Fingering(mode.id)); menuOpen = false }) }
                        }
                        if (state.canHint) DropdownMenuItem(
                            text = { Text(state.hintLabel) }, enabled = !state.busy,
                            onClick = { menuOpen = false; onEvent(TrainingEvent.Hint) })
                        if (state.canReplay) DropdownMenuItem(text = { Text("重听题目") }, onClick = { menuOpen = false; onEvent(TrainingEvent.Replay) })
                        DropdownMenuItem(text = { Text("结束练习") }, enabled = !state.busy,
                            onClick = { menuOpen = false; onEvent(TrainingEvent.End) })
                    }
                }
            }
            if (state.hasChord && (state.showLegend || legendOpen)) FingerLegend { legendOpen = false; onEvent(TrainingEvent.LegendSeen) }
            state.chordControls?.let { ChordInputControls(it, onEvent) }
            state.relation?.let { RelationContent(it) { onEvent(TrainingEvent.Demonstrate) } }
            state.notation?.let { NotationView(it, state.notationIndex, Modifier.fillMaxWidth().height(86.dp)) }
            if (state.message != null) {
                val wrong = state.wrong
                Surface(Modifier.fillMaxWidth(), shape = CutCornerShape(5.dp),
                    color = if (wrong) colors.error.background else colors.available.background,
                    border = BorderStroke(1.dp, if (wrong) colors.error.ink else colors.available.ink)) {
                    Text(state.message, color = if (wrong) colors.error.ink else colors.available.ink, fontSize = 15.sp,
                        modifier = Modifier.heightIn(max = messageHeight).verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
            if (state.options.isNotEmpty()) AnswerOptions(state.options, { onEvent(TrainingEvent.Answer(it)) },
                Modifier.fillMaxWidth().padding(horizontal = 48.dp).align(Alignment.CenterHorizontally))
          }
            if (state.board != null) TeachingFretboard(state.board, { onEvent(TrainingEvent.Position(it)) }, Modifier.fillMaxWidth().height(fixedBoardHeight))
        }
    }
}

@Composable
private fun AnswerOptions(options: List<AnswerOptionUi>, answer: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalGuitarColors.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                modifier = Modifier.weight(1f), contentPadding = PaddingValues(8.dp), shape = CutCornerShape(4.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = optionColors.background, contentColor = optionColors.ink,
                    disabledContainerColor = optionColors.background, disabledContentColor = optionColors.ink)) {
                Text(option.value + if (wrong) " ×" else if (confirmed) " ✓" else "", fontSize = 19.sp)
            }
        }
    }
}

@Composable
private fun TabPrompt(c: Coordinate, modifier: Modifier = Modifier) {
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
