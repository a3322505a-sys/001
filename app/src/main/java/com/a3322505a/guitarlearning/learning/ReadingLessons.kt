package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.core.MusicFacts
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable enum class NotationKind { TAB, STAFF }
@Serializable data class NotationPrompt(val kind: NotationKind, val pitches: List<Int>, val coordinates: List<Coordinate> = emptyList(), val score: ShortScore? = null) {
    init {
        require(pitches.isNotEmpty() && pitches.all { it in 40..88 })
        require(kind != NotationKind.TAB || coordinates.size == pitches.size && coordinates.map { MusicFacts.midi(it.string, it.fret) } == pitches)
    }
    // Standard guitar notation is written an octave above the sound.
    val writtenPitches: List<Int> get() = pitches.map { it + 12 }
}

object ReadingLessons {
    val ids = listOf("tab02", "staff", "staff02")
    val positions = listOf(Coordinate(1, 0), Coordinate(1, 1), Coordinate(1, 3), Coordinate(2, 0), Coordinate(2, 1), Coordinate(2, 3))
    val pitches = positions.map { MusicFacts.midi(it.string, it.fret) }
    fun skill(id: String, c: Coordinate): String = if (id == "tab02") "reading:$id:${c.id}" else "reading:$id:midi:${MusicFacts.midi(c.string, c.fret)}"
    fun skills(id: String): List<String> = positions.map { skill(id, it) }
    fun eligible(state: LearnerState, id: String): Boolean = "reading:$id:intro" in state.introductions

    fun passed(state: LearnerState, id: String): Boolean = skills(id).all { key ->
        if (id == "staff") state.attempts.filter { it.task.skillId == key && it.independent }.takeLast(3).let { it.size == 3 && it.all { a -> a.firstCorrect == true && a.completed } }
        else MemberEvidencePolicy.skillPassed(state, key)
    }

    fun retained(state: LearnerState, id: String, day: String, since: Long): Boolean = if (id != "staff")
        MemberEvidencePolicy.retained(state, skills(id), day, since)
    else skills(id).all { key -> state.attempts.any { it.task.skillId == key && it.independent && it.firstCorrect == true && it.at > since && it.localDay == day } }

    fun next(state: LearnerState, id: String, source: TaskSource, random: Random): LearningTask {
        require(id in ids)
        if (id == "staff" && "reading:staff:clef" !in state.introductions) return LearningTask(nodeId = id, skillId = "reading:staff:clef",
            prompt = "先认识高音谱号", explanation = "五线从下往上数：第1线 → 第2线（谱面 G4）。\n左侧卷曲符号围绕第2线，它叫高音谱号；本题选“高音谱号”。",
            constraint = AnswerConstraint(ConstraintKind.SYMBOL, symbol = "高音谱号"), options = listOf("高音谱号", "低音谱号"),
            source = TaskSource.DEMONSTRATION, introductionId = "reading:staff:clef", notation = NotationPrompt(NotationKind.STAFF, listOf(55)))
        if (id == "staff" && "reading:staff:octave" !in state.introductions) return LearningTask(nodeId = id, skillId = "reading:staff:octave",
            prompt = "吉他谱上的音与实际发声", explanation = "谱面 E5 → 实际 E4 → 第1弦空弦。\n吉他实际发声比谱面低一个八度，定位时看实际音高。",
            constraint = AnswerConstraint(ConstraintKind.SYMBOL, symbol = "低一个八度"), options = listOf("低一个八度", "完全同高", "高一个八度"),
            source = TaskSource.DEMONSTRATION, introductionId = "reading:staff:octave", notation = NotationPrompt(NotationKind.STAFF, listOf(64)))
        val intro = !eligible(state, id)
        val actualSource = if (intro) TaskSource.DEMONSTRATION else source
        val task = if (id == "staff") {
            val previous = state.attempts.lastOrNull()?.task?.skillId
            val c = positions.filter { skill(id, it) != previous }.shuffled(random).minBy { c ->
                state.attempts.count { it.task.skillId == skill(id, c) && it.independent }
            }
            single(c, actualSource)
        } else {
            // A phrase uses every target once across each two-phrase cycle; priority follows actual member evidence.
            val selected = positions.shuffled(random).sortedBy { MemberEvidencePolicy.evidence(state, skill(id, it)).size }.take(3).shuffled(random)
            phrase(id, selected, actualSource)
        }
        return task.copy(introductionId = if (intro) "reading:$id:intro" else null)
    }

    fun single(c: Coordinate, source: TaskSource): LearningTask {
        val midi = MusicFacts.midi(c.string, c.fret)
        return LearningTask(nodeId = "staff", skillId = skill("staff", c), direction = Direction.STAFF_TO_POSITION,
            prompt = "读五线谱，找到这个音高", explanation = "谱面 ${LessonExplanations.pitch(midi + 12)} → 实际 ${LessonExplanations.pitch(midi)} → 例如第${c.string}弦${LessonExplanations.fret(c.fret)}。\n实际发声比谱面低一个八度；范围内其他同音高位置也可选。",
            constraint = AnswerConstraint(ConstraintKind.PITCH, midi = midi), source = source,
            notation = NotationPrompt(NotationKind.STAFF, listOf(midi)))
    }

    fun phrase(id: String, coordinates: List<Coordinate>, source: TaskSource): LearningTask {
        val tab = id == "tab02"
        val rules = coordinates.map { c -> if (tab) AnswerConstraint(ConstraintKind.COORDINATE, coordinate = c)
            else AnswerConstraint(ConstraintKind.PITCH, midi = MusicFacts.midi(c.string, c.fret)) }
        val values = coordinates.map { MusicFacts.midi(it.string, it.fret) }
        return LearningTask(nodeId = id, skillId = "reading:$id:phrase", direction = if (tab) Direction.TAB_TO_POSITION else Direction.STAFF_TO_POSITION,
            prompt = if (tab) "从左到右读 TAB 短句" else "从左到右读五线谱短句",
            explanation = if (tab) "从左到右：${coordinates.joinToString(" → ") { "第${it.string}弦${LessonExplanations.fret(it.fret)}" }}。\n线表示弦，数字表示品，0表示空弦；跟着当前指示点，逐项点指定位置。"
                else "谱面：${values.joinToString(" → ") { LessonExplanations.pitch(it + 12) }}。\n实际：${values.joinToString(" → ") { LessonExplanations.pitch(it) }}。\n每个音降低一个八度后定位；范围内同音高位置都接受。",
            constraint = rules.first(), sequence = rules, completion = CompletionKind.SEQUENCE, targetSkillIds = coordinates.map { skill(id, it) }, source = source,
            notation = NotationPrompt(if (tab) NotationKind.TAB else NotationKind.STAFF, values, if (tab) coordinates else emptyList()))
    }
}
