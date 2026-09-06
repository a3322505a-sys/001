package com.a3322505a.guitarlearning.learning

import kotlin.random.Random

object ChordLessons {
    fun shapes(nodeId: String): List<ChordShape> = when (nodeId) {
        "chord-am" -> listOf(ChordShapes.am)
        "chord-g5" -> listOf(ChordShapes.g5Two, ChordShapes.g5Three)
        "chord-f" -> listOf(ChordShapes.fBarre)
        else -> emptyList()
    }
    fun skill(shape: ChordShape, string: Int) = "chord:${shape.id}:s$string:shape"
    fun next(state: LearnerState, nodeId: String, source: TaskSource, random: Random): LearningTask {
        val shapes = shapes(nodeId)
        val intro = shapes.firstOrNull { "chord:${it.id}:intro" !in state.introductions }
        val shape = intro ?: shapes.minBy { shape -> state.attempts.sumOf { a -> a.members.count { it.independent && it.skillId.startsWith("chord:${shape.id}:") } } }
        return make(shape, nodeId, if (intro != null) TaskSource.DEMONSTRATION else source, random)
            .copy(introductionId = intro?.let { "chord:${it.id}:intro" })
    }

    fun make(shape: ChordShape, nodeId: String, source: TaskSource, random: Random = Random.Default): LearningTask {
        val strings = if (source == TaskSource.DEMONSTRATION) (6 downTo 1).toList() else (1..6).shuffled(random)
        val rules = strings.map { s -> shape.fret(s)?.let { AnswerConstraint(ConstraintKind.COORDINATE, coordinate = Coordinate(s, it)) }
            ?: AnswerConstraint(ConstraintKind.SYMBOL, symbol = "X", string = s) }
        val positions = (6 downTo 1).joinToString("；") { string ->
            val fret = shape.fret(string)
            if (fret == null) "${string}弦 X（不弹）"
            else "${string}弦${LessonExplanations.fret(fret)} ${com.a3322505a.guitarlearning.core.MusicFacts.label(string, fret)}"
        }
        val explanation = "$positions。\n" + when (shape.id) {
            "am-open" -> "食指→2弦1品；中指→4弦2品；无名指→3弦2品。1、5弦空弦，6弦不弹；发出的音属于 A–C–E。"
            "g5-two" -> "G2（根音）→ D3（五音）：差7个半音，是纯五度。其余弦不弹。"
            "g5-three" -> "G2（根音）→ D3（五音）→ G3（根音高八度）。G2→G3差12个半音；加小指按4弦5品，没有加入三音。"
            else -> "食指横按1品；中指→3弦2品，无名指→5弦3品，小指→4弦3品。例如5弦虽被食指覆盖，仍按更高的3品发出 C3。"
        }
        return LearningTask(nodeId = nodeId, skillId = "chord:${shape.id}:sequence", direction = Direction.CHORD_SHAPE,
            prompt = "${shape.title} · 指定形态", explanation = explanation,
            constraint = AnswerConstraint(ConstraintKind.COORDINATE, coordinate = shape.sounding().first()),
            range = PhysicalRange(0, maxOf(4, shape.frets.filterNotNull().max())), source = source,
            completion = CompletionKind.SEQUENCE, sequence = rules, targetSkillIds = strings.map { skill(shape, it) }, chord = shape)
    }

    fun passed(state: LearnerState, nodeId: String): Boolean = shapes(nodeId).isNotEmpty() && shapes(nodeId).all { shape ->
        (1..6).all { s -> MemberEvidencePolicy.skillPassed(state, skill(shape, s)) }
    }
}
