package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.core.MusicFacts
import kotlin.math.abs

/** One projection for correction text, visible references and their exposure facts. */
object CorrectionPresentation {
    fun references(a: ActiveTask, introduced: Set<String>): List<Coordinate> = BoardTeachingPolicy.correctionReferences(a, introduced)

    fun message(a: ActiveTask, introduced: Set<String>): String {
        val t = a.task
        val rule = if (t.completion == CompletionKind.SEQUENCE) t.sequence.getOrNull(a.sequenceIndex) else t.constraint
        val target = rule?.coordinate ?: t.coordinate
        val mistake = a.inputs.lastOrNull { it.result == ClickResult.WRONG }?.coordinate
        if (target != null && mistake != null && mistake != target && rule?.kind == ConstraintKind.COORDINATE &&
            MusicFacts.midi(target.string, target.fret) == MusicFacts.midi(mistake.string, mistake.fret))
            return "音高相同；本题请点指定位置。"
        if (t.chord != null) return if (rule?.symbol == "X") "本弦不弹，选择 X。" else if (rule?.coordinate?.fret == 0) "本弦弹空弦，选择 O。" else "按亮起位置设置本弦。"
        if (t.direction in AdaptiveEvidence.positionDirections && target != null) {
            val reference = references(a, introduced).firstOrNull()
            if (reference != null) {
                val gap = target.fret - reference.fret
                val count = when (abs(gap)) { 1 -> "一品"; 2 -> "两品"; else -> "${abs(gap)}品" }
                val interval = when (abs(gap)) { 1 -> "一个半音"; 2 -> "一个全音"; 12 -> "一个八度"; else -> "${abs(gap)}个半音" }
                return "向${if (gap > 0) "右" else "左"}$count，${if (gap > 0) "升高" else "降低"}$interval。"
            }
            return if (t.direction == Direction.POSITION_TO_NOTE) "读出亮起位置的音名。" else "点击亮起的正确位置。"
        }
        if (t.direction == Direction.TAB_TO_POSITION) return "按谱线找弦，按数字找品。"
        if (t.direction == Direction.STAFF_TO_POSITION) return "按谱面音高，点击亮起位置。"
        if (t.direction in MappingLessons.fixedDirections) return "${t.mappingNote} 对应固定唱名 ${MusicFacts.fixedSolfege[t.mappingNote].orEmpty()}。"
        if (t.direction in MappingLessons.degreeDirections && t.mappingNote != null && t.tonicPitchClass != null)
            return "${MusicFacts.noteNames[t.tonicPitchClass]}大调：${t.mappingNote} 是第${MusicFacts.majorDegree(MusicFacts.noteNames.indexOf(t.mappingNote), t.tonicPitchClass)}级。"
        if (rule?.kind == ConstraintKind.SYMBOL) return "本项选 ${rule.symbol.orEmpty()}。"
        return "对照参照音，点击亮起位置。"
    }

    fun expose(s: LearnerState, a: ActiveTask, now: Long): LearnerState {
        val positions = BoardTeachingPolicy.facts(a, s.introductions).exposedPositions
        val targets = positions.map(AdaptiveEvidence::positionTarget).toMutableSet()
        if (a.task.completion == CompletionKind.SEQUENCE) {
            a.task.targetSkillIds.getOrNull(a.sequenceIndex)?.let { targets += "skill:$it" }
            // The chord overlay displays the complete shape, even during member correction.
            if (a.task.chord != null) targets += a.task.targetSkillIds.map { "skill:$it" }
        } else targets += AdaptiveEvidence.targets(a.task)
        val events = targets.map { KnowledgeExposure(a.task.id, it, now, true) }
        return s.copy(knowledgeExposures = s.knowledgeExposures + events.filterNot { it in s.knowledgeExposures })
    }
}
