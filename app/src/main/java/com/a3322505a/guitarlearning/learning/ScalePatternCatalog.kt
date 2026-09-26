package com.a3322505a.guitarlearning.learning

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.core.MusicFacts

/** A single selected shape is shown at a time; the ordinary training route owns the exercises. */
@Composable
internal fun ScalePatternCatalog(state: LearnerState, start: (String) -> Unit, resume: () -> Unit) {
    val current = MajorScalePatterns.all.firstOrNull { MajorScalePatterns.nodeId(it) == state.currentNode && state.sessionId != null }
        ?: MajorScalePatterns.all.lastOrNull { p -> state.attempts.any { it.task.nodeId == MajorScalePatterns.nodeId(p) } }
        ?: MajorScalePatterns.all.first()
    var selectedId by rememberSaveable { mutableStateOf(current.id) }
    var degrees by rememberSaveable { mutableStateOf(false) }
    val pattern = MajorScalePatterns.all.first { it.id == selectedId }
    val nodeId = MajorScalePatterns.nodeId(pattern)
    Text("C大调 · 当前${current.title}", style = MaterialTheme.typography.titleMedium)
    Button(onClick = {
        if (state.sessionId != null && state.currentNode == MajorScalePatterns.nodeId(current)) resume()
        else start(MajorScalePatterns.nodeId(current))
    }, modifier = Modifier.heightIn(min = 48.dp)) { Text("继续当前指型") }
    MajorScalePatterns.all.forEach { item ->
        val id = MajorScalePatterns.nodeId(item)
        OutlinedButton(onClick = { selectedId = item.id }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("${item.title} · ${item.firstFret}–${item.lastFret}品" +
                if (Curriculum.mastered(state, id)) " · 已练习" else "")
        }
    }
    Text("${pattern.title} · C大调", style = MaterialTheme.typography.titleMedium)
    TextButton(onClick = { degrees = !degrees }) { Text(if (degrees) "显示音名" else "显示级数") }
    val board = FretboardUiState("preview:$nodeId", pattern.firstFret, pattern.lastFret,
        marks = pattern.positions.map { c -> BoardMark(c,
            if (c in pattern.roots) MarkRole.REFERENCE else MarkRole.TARGET,
            if (degrees) "${pattern.degrees.getValue(c)}" else MusicFacts.note(c.string, c.fret)) })
    TeachingFretboard(board, {}, Modifier.fillMaxWidth().height(220.dp))
    Text("金色为主音 C；相邻指型的重叠音共用同一音高。", style = MaterialTheme.typography.bodySmall)
    Button(onClick = { if (state.sessionId != null && state.currentNode == nodeId) resume() else start(nodeId) },
        modifier = Modifier.heightIn(min = 48.dp)) {
        Text(if (state.sessionId != null && state.currentNode == nodeId) "继续练习" else "练习这个指型")
    }
}
