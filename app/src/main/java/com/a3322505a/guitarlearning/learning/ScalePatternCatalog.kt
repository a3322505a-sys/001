package com.a3322505a.guitarlearning.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.core.MusicFacts

/** A single selected shape is shown at a time; the ordinary training route owns the exercises. */
@Composable
internal fun ScalePatternCatalog(state: LearnerState, start: (String) -> Unit, resume: () -> Unit) {
    val currentId = state.currentNode.takeIf { it in ScalePatternLessons.ids && state.sessionId != null }
        ?: state.attempts.lastOrNull { it.task.nodeId in ScalePatternLessons.ids }?.task?.nodeId
        ?: "pattern-mi"
    var selectedId by rememberSaveable { mutableStateOf(currentId) }
    var degrees by rememberSaveable { mutableStateOf(false) }
    var minor by rememberSaveable { mutableStateOf(false) }
    val key = ScalePatternLessons.transposed.firstOrNull { it.id == selectedId }
    val pattern = MajorScalePatterns.all.first { it.id == (if (key == null) selectedId.removePrefix("pattern-") else "si") }
    val nodeId = selectedId
    val positions = if (key == null) pattern.positions else pattern.positions.map { Coordinate(it.string, it.fret + key.shift) }
    val tonic = if (key != null) key.tonic else if (minor) 9 else 0
    Text("当前：${Curriculum.node(currentId).title}", style = MaterialTheme.typography.titleMedium)
    Button(onClick = {
        if (state.sessionId != null && state.currentNode == currentId) resume() else start(currentId)
    }, modifier = Modifier.heightIn(min = 48.dp)) { Text("继续当前内容") }
    Text("选择指型 · 左右滑动", style = MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val choices = MajorScalePatterns.all.map { MajorScalePatterns.nodeId(it) to "${it.solfegeName} · ${it.cagedName}形" } +
            ScalePatternLessons.transposed.map { it.id to "${it.title} · si形" }
        choices.forEach { (id, label) ->
            FilterChip(selected = selectedId == id, onClick = { selectedId = id },
                label = { Text(label + if (Curriculum.mastered(state, id)) " ✓" else "") },
                modifier = Modifier.heightIn(min = 48.dp))
        }
    }
    Text("${pattern.title} · ${key?.title ?: if (minor) "A自然小调" else "C大调"} · ${positions.minOf { it.fret }}–${positions.maxOf { it.fret }}品",
        style = MaterialTheme.typography.titleMedium)
    if (key == null) TextButton(onClick = { minor = !minor }) { Text(if (minor) "切回C大调" else "对比A自然小调") }
    TextButton(onClick = { degrees = !degrees }) { Text(if (degrees) "显示音名" else "显示级数") }
    val board = FretboardUiState("preview:$nodeId:$tonic", positions.minOf { it.fret }, positions.maxOf { it.fret },
        marks = positions.map { c ->
            val midi = MusicFacts.midi(c.string, c.fret)
            val degree = if (minor && key == null) MusicRelations.naturalMinor.dropLast(1).indexOf(Math.floorMod(midi - 9, 12)) + 1
                else MusicFacts.majorDegree(midi, tonic)
            BoardMark(c, if (midi % 12 == tonic) MarkRole.REFERENCE else MarkRole.TARGET,
                if (degrees) "$degree" else key?.spelling(midi) ?: MusicFacts.note(c.string, c.fret))
        })
    TeachingFretboard(board, {}, Modifier.fillMaxWidth().height(220.dp))
    Text("金色为当前调主音；相邻指型的重叠音共用实际音高。", style = MaterialTheme.typography.bodySmall)
    if (Curriculum.available(state, Curriculum.node(nodeId))) {
        Button(onClick = { if (state.sessionId != null && state.currentNode == nodeId) resume() else start(nodeId) },
            modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (state.sessionId != null && state.currentNode == nodeId) "继续练习" else "练习这个指型")
        }
    } else Text("先在 C 大调的 si 指型练习，再把同一形态移到新调。")
}
