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
        val explanation = when (shape.id) {
            "am-open" -> "Am 用食指按2弦1品，中指按4弦2品，无名指按3弦2品；1、5弦开放，6弦不弹。"
            "g5-two" -> "G5 两音形态：6弦3品根音G，5弦5品五音D；其余弦不弹。"
            "g5-three" -> "再用小指加4弦5品的G高八度；仍只有根音和五音，不是大小三和弦。"
            else -> "食指在1品覆盖六根弦，其他手指按更高品；每根弦按最高的按弦品位发声。"
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
