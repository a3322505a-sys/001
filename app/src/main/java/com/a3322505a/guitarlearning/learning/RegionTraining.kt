package com.a3322505a.guitarlearning.learning

import kotlin.random.Random

/** Region selection changes scheduling only. Existing task and mastery evidence stay authoritative. */
object RegionTraining {
    fun region(id: String) = FretboardRegion.valueOf(id)
    fun owner(node: String) = FretboardRegion.entries.firstOrNull { node in it.nodeIds }
    fun available(s: LearnerState, id: String) = region(id).nodes.any { RegionProgression.available(s, it) }
    fun nodes(id: String): List<CurriculumNode> = FretboardRegion.entries
        .take(region(id).ordinal + 1).flatMap { it.nodes }
    fun known(s: LearnerState, id: String) = nodes(id).filter { RegionProgression.available(s, it) }
        .flatMap { n -> PracticeLessons.introducedPositions(s, n).map { n.id to it } }
    fun history(s: LearnerState): List<Attempt> {
        val run = s.regionTraining ?: return emptyList()
        return s.attempts.filter { it.sessionId == s.sessionId && it.ordinal >= run.startOrdinal && it.completed && it.task.direction in directions }
    }
    val directions = listOf(Direction.NOTE_TO_POSITION, Direction.POSITION_TO_NOTE)

    fun next(s: LearnerState, scheduler: LessonScheduler, random: Random, now: Long): LearningTask =
        RegionRounds.next(s, scheduler, random, now)
}
