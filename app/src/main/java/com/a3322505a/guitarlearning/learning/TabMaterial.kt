package com.a3322505a.guitarlearning.learning

import kotlin.random.Random

/** Small original reading set. No rhythm claims and no reuse of the short-score pilot's retests. */
object TabMaterial {
    data class Phrase(val id: String, val positions: List<Coordinate>, val crossing: Boolean, val transfer: Boolean = false)
    private fun c(string: Int, fret: Int) = Coordinate(string, fret)
    val phrases = listOf(
        Phrase("same-1-up", listOf(c(1,0),c(1,1),c(1,3)), false),
        Phrase("same-1-return", listOf(c(1,0),c(1,3),c(1,0)), false),
        Phrase("same-2-down", listOf(c(2,3),c(2,1),c(2,0)), false),
        Phrase("same-2-repeat", listOf(c(2,1),c(2,1),c(2,3)), false),
        Phrase("same-1-transfer", listOf(c(1,1),c(1,0),c(1,1)), false, true),
        Phrase("same-2-transfer", listOf(c(2,0),c(2,3),c(2,1)), false, true),
        Phrase("cross-open", listOf(c(1,0),c(2,0),c(1,0)), true),
        Phrase("cross-step", listOf(c(2,3),c(1,0),c(1,1)), true),
        Phrase("cross-return", listOf(c(1,3),c(2,1),c(1,3)), true),
        Phrase("cross-repeat", listOf(c(2,1),c(1,1),c(1,1)), true),
        Phrase("cross-transfer-a", listOf(c(2,0),c(1,1),c(2,1)), true, true),
        Phrase("cross-transfer-b", listOf(c(1,0),c(2,3),c(2,0)), true, true),
    )
    fun singlePositions(s: LearnerState): List<Coordinate> =
        (listOf(c(1,0), c(1,1)) + Curriculum.nodes.flatMap { n ->
            n.positions.filter { it.fret <= 4 && ("position:${it.id}" in s.introductions || Curriculum.mastered(s, n.id)) }
        }).distinct()
    fun singleTaught(s: LearnerState, c: Coordinate) =
        "tab01:${c.id}:intro" in s.introductions || s.attempts.any { it.task.nodeId == "tab01" && it.task.coordinate == c && it.completed }
    fun single(c: Coordinate, source: TaskSource) = LearningTask(nodeId = "tab01", skillId = "${c.id}:tab_to_position", coordinate = c,
        direction = Direction.TAB_TO_POSITION, prompt = "按 TAB 找到位置", explanation = LessonExplanations.tab(c),
        constraint = AnswerConstraint(ConstraintKind.COORDINATE, coordinate = c), showTab = true, source = source)
    private fun completed(s: LearnerState) = s.attempts.filter { it.task.nodeId == "tab02" && !it.task.guided && it.completed }
    fun catalog(s: LearnerState): List<Phrase> {
        val attempts = completed(s)
        val cross = attempts.count { it.task.notation?.coordinates?.map { c -> c.string }?.distinct()?.size == 1 } >= 6
        return phrases.filter { !it.crossing || cross }.filter { phrase ->
            !phrase.transfer || attempts.count { it.task.skillId.startsWith("reading:tab02:material:") &&
                it.task.notation?.coordinates?.map { c -> c.string }?.distinct()?.size.let { size -> (size != null && size > 1) == phrase.crossing } } >= 4
        }
    }
    fun task(p: Phrase, source: TaskSource) = ReadingLessons.phrase("tab02", p.positions, source)
        .copy(skillId = "reading:tab02:material:${p.id}")
    fun next(s: LearnerState, source: TaskSource, random: Random): LearningTask {
        val candidates = catalog(s)
        val previous = s.attempts.lastOrNull()?.task?.skillId
        val p = candidates.filter { "reading:tab02:material:${it.id}" != previous }.ifEmpty { candidates }.shuffled(random).minBy { phrase ->
            s.attempts.count { it.task.skillId == "reading:tab02:material:${phrase.id}" }
        }
        return task(p, source)
    }
}
