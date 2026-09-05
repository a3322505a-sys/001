package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.core.MusicFacts

/** Musical truth is independent of which locations the course has introduced. */
object AnswerEvaluator {
    fun matches(c: Coordinate, rule: AnswerConstraint): Boolean = when (rule.kind) {
        ConstraintKind.NOTE_CLASS -> MusicFacts.note(c.string, c.fret) == rule.symbol
        ConstraintKind.PITCH -> MusicFacts.midi(c.string, c.fret) == rule.midi
        ConstraintKind.COORDINATE -> c == rule.coordinate
        ConstraintKind.STRING -> c.string == rule.string
        ConstraintKind.FRET -> c.fret == rule.fret
        ConstraintKind.SYMBOL -> false
    }

    fun validPositions(task: LearningTask, index: Int = 0): List<Coordinate> {
        val rule = if (task.completion == CompletionKind.SEQUENCE) task.sequence.getOrNull(index) ?: return emptyList() else task.constraint
        return task.range.positions().filter { matches(it, rule) }
    }

    fun evaluate(active: ActiveTask, coordinate: Coordinate?, symbol: String?): ClickResult {
        val task = active.task
        if (coordinate != null && !task.range.contains(coordinate)) return ClickResult.OUTSIDE
        if (coordinate != null && task.completion == CompletionKind.SET && coordinate in active.confirmed) return ClickResult.REPEATED
        val rule = if (task.completion == CompletionKind.SEQUENCE) task.sequence.getOrNull(active.sequenceIndex) ?: return ClickResult.REPEATED else task.constraint
        val correct = if (coordinate != null) matches(coordinate, rule) else rule.kind == ConstraintKind.SYMBOL && symbol == rule.symbol
        if (!correct) return ClickResult.WRONG
        if (active.phase == Phase.CORRECTING) return ClickResult.CORRECTION
        if (task.completion == CompletionKind.SET && coordinate !in task.requiredTargets) return ClickResult.EXTRA_CORRECT
        if (task.completion == CompletionKind.SET && (active.confirmed + listOfNotNull(coordinate)).toSet().containsAll(task.requiredTargets).not()) return ClickResult.PARTIAL
        if (task.completion == CompletionKind.SEQUENCE && active.sequenceIndex < task.sequence.lastIndex) return ClickResult.PARTIAL
        return ClickResult.CORRECT
    }

    fun wrongFeedback(task: LearningTask, coordinate: Coordinate?): String {
        if (coordinate != null && task.constraint.kind == ConstraintKind.COORDINATE) {
            val target = task.constraint.coordinate
            if (target != null && MusicFacts.midi(coordinate.string, coordinate.fret) == MusicFacts.midi(target.string, target.fret)) {
                return "音高相同，这道 TAB 要求的是${target.label}。请点亮起的位置。"
            }
        }
        return "再看一次：${task.explanation}"
    }
}
