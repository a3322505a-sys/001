package com.a3322505a.guitarlearning.learning

import kotlin.math.abs

/** Lesson facts shared by presentation and evidence. No UI labels or rendering types. */
internal enum class PositionKnowledge { NONE, QUESTION, NOTE, GUIDED_SYMBOL, CONFIRMED, MISTAKE }

internal data class PositionTeachingFact(
    val coordinate: Coordinate,
    val target: Boolean,
    val correct: Boolean,
    val wrong: Boolean,
    val reference: Boolean,
    val knowledge: PositionKnowledge,
)

internal data class BoardTeachingFacts(
    val display: PhysicalRange,
    val positions: List<PositionTeachingFact>,
    val chordVisible: Boolean,
    val chordPositions: Set<Coordinate>,
) {
    val exposedPositions: Set<Coordinate> get() = positions.filter {
        it.knowledge == PositionKnowledge.NOTE || it.knowledge == PositionKnowledge.GUIDED_SYMBOL
    }.map { it.coordinate }.toSet() + chordPositions
}

internal enum class PositionInputMode { DISABLED, AUDITION, ANSWER }
internal data class PositionInputPolicy(
    val mode: PositionInputMode,
    val interactivePositions: Set<Coordinate>,
    val answerPositions: Set<Coordinate>,
)

internal object BoardTeachingPolicy {
    fun chordVisible(a: ActiveTask) = a.task.guided || a.hintLevel >= 2 || a.phase != Phase.ANSWERING

    fun correctionReferences(a: ActiveTask, introduced: Set<String>): List<Coordinate> {
        val c = a.task.coordinate ?: return emptyList()
        if (a.task.direction !in AdaptiveEvidence.positionDirections) return emptyList()
        return (0..15).map { Coordinate(c.string, it) }
            .filter { it != c && AdaptiveEvidence.positionTarget(it) in introduced }
            .sortedWith(compareBy<Coordinate> { abs(it.fret - c.fret) }.thenBy { it.fret })
            .take(1)
    }

    fun facts(a: ActiveTask, introduced: Set<String> = emptySet(), displayLastFret: Int = a.task.range.lastFret): BoardTeachingFacts {
        val t = a.task
        val correcting = a.phase in listOf(Phase.CORRECTING, Phase.CORRECTED)
        val reveal = t.guided || a.hintLevel >= 2 || correcting
        val answers = if (reveal) AnswerEvaluator.validPositions(t, a.sequenceIndex).toSet() else emptySet()
        val question = t.coordinate.takeIf { t.direction == Direction.POSITION_TO_NOTE }
        val teachingReferences = if (reveal && t.direction in AdaptiveEvidence.positionDirections && t.coordinate != null) {
            if (correcting) correctionReferences(a, introduced) else LessonExplanations.positionRoute(t.nodeId, t.coordinate)
        } else emptyList()
        val references = (t.referenceCoordinates + teachingReferences).toSet()
        val display = PhysicalRange(0, maxOf(displayLastFret, t.range.lastFret, references.maxOfOrNull { it.fret } ?: 0))
        val mistake = a.inputs.lastOrNull { it.result == ClickResult.WRONG }?.coordinate
        val positions = display.positions().mapNotNull { c ->
            val target = c in answers || c == question || c in references
            val correct = c in a.confirmed
            val wrong = c == mistake && !correct
            if (!target && !correct && !wrong) null else PositionTeachingFact(c, target, correct, wrong,
                c != question && c in references && c !in answers,
                when {
                    wrong -> PositionKnowledge.MISTAKE
                    correcting && (c in answers || c == question) -> PositionKnowledge.NOTE
                    correct -> PositionKnowledge.CONFIRMED
                    c == question -> PositionKnowledge.QUESTION
                    c in references && c !in answers -> PositionKnowledge.NOTE
                    t.guided && t.coordinate == c && !t.constraint.symbol.isNullOrEmpty() -> PositionKnowledge.GUIDED_SYMBOL
                    else -> PositionKnowledge.NONE
                })
        }
        val visible = chordVisible(a)
        val chordPositions = if (visible) t.chord?.sounding().orEmpty().filter { it.fret > 0 }.toSet() else emptySet()
        return BoardTeachingFacts(display, positions, visible, chordPositions)
    }

    fun input(a: ActiveTask, busy: Boolean = false, displayLastFret: Int = a.task.range.lastFret,
              introduced: Set<String> = emptySet()): PositionInputPolicy = input(a, busy, facts(a, introduced, displayLastFret))

    fun input(a: ActiveTask, busy: Boolean, facts: BoardTeachingFacts): PositionInputPolicy {
        val t = a.task
        val mode = when {
            t.notation?.score?.id?.startsWith("pilot-") == true && a.phase in listOf(Phase.CORRECT, Phase.CORRECTED) -> PositionInputMode.DISABLED
            t.relation?.ear == true && (!a.audioReady || t.options.isNotEmpty()) -> PositionInputMode.DISABLED
            t.constraint.kind == ConstraintKind.SYMBOL || busy || a.phase !in listOf(Phase.ANSWERING, Phase.CORRECTING) -> PositionInputMode.AUDITION
            else -> PositionInputMode.ANSWER
        }
        return PositionInputPolicy(mode, facts.display.positions().toSet(), t.range.positions().toSet())
    }

    fun displayLast(s: LearnerState): Int {
        val introduced = Curriculum.nodes.flatMap { it.positions }.filter { "position:${it.id}" in s.introductions }
        val last = maxOf(s.active?.task?.range?.lastFret ?: 4, introduced.maxOfOrNull { it.fret } ?: 4)
        return when { last <= 4 -> 4; last <= 8 -> 8; last <= 12 -> 12; else -> 15 }
    }
}
