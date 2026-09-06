package com.a3322505a.guitarlearning.learning

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a3322505a.guitarlearning.core.MusicFacts

private fun pitchLabel(midi: Int) = "${MusicFacts.noteNames[midi % 12]}${midi / 12 - 1}"

@Composable
fun RelationContent(active: ActiveTask, state: LearnerState, busy: Boolean, model: TrainingViewModel) {
    val relation = active.task.relation ?: return
    val playing by model.playing.collectAsState()
    val notice by model.notice.collectAsState()
    val reveal = active.task.guided || active.hintLevel >= 2 || active.phase != Phase.ANSWERING
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text("参考音 ${relation.referencePitches.joinToString(" / ") { pitchLabel(it) }}", fontSize = 14.sp)
            if (active.task.completion == CompletionKind.SEQUENCE) Text(
                if (reveal) relation.targetPitches.mapIndexed { i, p -> (if (i == active.sequenceIndex) "▸" else "") + pitchLabel(p) }.joinToString("  ")
                else "第${(active.sequenceIndex + 1).coerceAtMost(active.task.sequence.size)} / ${active.task.sequence.size}项 · 点击范围内正确音高", fontSize = 14.sp)
            else if (relation.ear) Text(if (active.audioReady) "已播放，可作答或重听" else "完整播放后作答", fontSize = 13.sp)
            notice?.let { Text(it, fontSize = 12.sp) }
        }
        if (!state.soundEnabled) OutlinedButton(onClick = { model.sound(true) }, enabled = !busy) { Text("开启声音") }
        else OutlinedButton(onClick = { model.playRelation(active.task.id) }, enabled = !busy && !playing) {
            Text(if (playing) "播放中…" else if (!relation.ear) "试听示范" else if (relation.chord) "根音 → 和弦" else "参照 → 目标")
        }
    }
}
