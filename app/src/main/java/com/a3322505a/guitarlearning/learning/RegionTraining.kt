package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.core.MusicFacts
import kotlin.random.Random

/** Region selection changes scheduling only. Existing task and mastery evidence stay authoritative. */
object RegionTraining {
    fun region(id: String) = FretboardRegion.valueOf(id)
    fun owner(node: String) = FretboardRegion.entries.firstOrNull { node in it.nodeIds }
    fun available(s: LearnerState, id: String) = region(id).nodes.any { Curriculum.available(s, it) }
    fun nodes(id: String): List<CurriculumNode> = FretboardRegion.entries
        .take(region(id).ordinal + 1).flatMap { it.nodes }
    fun known(s: LearnerState, id: String) = nodes(id).filter { Curriculum.available(s, it) }
        .flatMap { n -> PracticeLessons.introducedPositions(s, n).map { n.id to it } }
    fun history(s: LearnerState): List<Attempt> {
        val run = s.regionTraining ?: return emptyList()
        return s.attempts.filter { it.ordinal >= run.startOrdinal && it.completed && it.task.direction in directions }
    }
    val directions = listOf(Direction.NOTE_TO_POSITION, Direction.POSITION_TO_NOTE)

    /** Only independent first answers adjust strength. Small probes never grant mastery. */
    fun strength(s: LearnerState): Int {
        val recent = history(s).filter { it.independent }.takeLast(10)
        if (recent.size < 3) return 0
        if (recent.takeLast(2).all { it.firstCorrect == false }) return 0
        val ratio = recent.count { it.firstCorrect == true }.toDouble() / recent.size
        val both = directions.all { d -> recent.any { it.task.direction == d && it.firstCorrect == true } }
        return if (ratio >= 0.8 && both) if (recent.size >= 10) 2 else 1 else 0
    }

    fun next(s: LearnerState, scheduler: LessonScheduler, random: Random): LearningTask {
        val run = requireNotNull(s.regionTraining)
        val region = region(run.regionId)
        val known = known(s, run.regionId)
        val history = history(s)
        val probe = run.probeSize > history.count { it.task.regionProbe }
        val pending = region.nodes.firstOrNull { Curriculum.available(s, it) && !Curriculum.mastered(s, it.id) }
        val unseen = pending?.positions?.firstOrNull { "position:${it.id}" !in s.introductions }
        val sinceDemo = history.asReversed().takeWhile { !it.task.guided }.size
        if (unseen != null && (known.isEmpty() || !probe && sinceDemo >= 3)) {
            return scheduler.makePosition(pending.id, unseen, Direction.NOTE_TO_POSITION, TaskSource.DEMONSTRATION)
                .copy(introductionId = "position:${unseen.id}")
        }
        // New profiles with no known pool begin the existing two-point teaching sequence.
        if (known.isEmpty()) {
            requireNotNull(unseen) { "区域尚未解锁" }
            return scheduler.makePosition(requireNotNull(pending).id, unseen, Direction.NOTE_TO_POSITION, TaskSource.DEMONSTRATION)
                .copy(introductionId = "position:${unseen.id}")
        }
        val level = strength(s)
        val primary = known.filter { it.first in region.nodeIds }
        val pool = if (probe || level > 0 || primary.isEmpty()) known else primary
        val choices = pool.flatMap { (id, c) -> directions.map { d -> scheduler.makePosition(id, c, d, TaskSource.MAIN) } }
        val last = s.attempts.lastOrNull()?.task
        val varied = choices.filter { it.coordinate != last?.coordinate }.ifEmpty { choices.filter { it.skillId != last?.skillId }.ifEmpty { choices } }
        val spaced = varied.filter { t ->
            val at = s.attempts.lastOrNull { it.task.skillId == t.skillId || it.task.coordinate == t.coordinate && (it.task.guided || it.hintLevel > 0) }?.ordinal
            at == null || (s.attempts.maxOfOrNull { it.ordinal } ?: 0) + 1 - at >= 3
        }.ifEmpty { varied }
        val recentProbes = history.filter { it.task.regionProbe }
        val pick = spaced.shuffled(random).minBy { t ->
            val evidence = MasteryPolicy.positionEvidence(s, requireNotNull(t.coordinate)).takeLast(6)
            val directionCount = history.takeLast(10).count { it.task.direction == t.direction }
            val probeCount = recentProbes.count { it.task.coordinate == t.coordinate }
            val mastered = if (MasteryPolicy.positionPassed(s, t.coordinate)) 3 else 0
            val weak = evidence.count { it.firstCorrect == false }
            val mainBonus = if (t.nodeId in region.nodeIds) -2 else 0
            if (probe) probeCount * 5 + directionCount else mastered - weak * 2 + directionCount + mainBonus + random.nextInt(4)
        }
        // Repeat weakness as a spaced demonstration, then assess again after intervening items.
        val weak = history.filter { it.task.coordinate == pick.coordinate && it.independent }.takeLast(2)
        if (!probe && level == 0 && sinceDemo >= 3 && weak.size == 2 && weak.all { it.firstCorrect == false })
            return pick.copy(source = TaskSource.DEMONSTRATION)
        val c = requireNotNull(pick.coordinate)
        return if (region == FretboardRegion.FULL && !probe && level >= 2) pick.copy(
            regionProbe = false, range = PhysicalRange(0, 12, setOf(c.string)),
            prompt = if (pick.direction == Direction.NOTE_TO_POSITION) "在第${c.string}弦找到 ${MusicFacts.label(c.string, c.fret)}" else pick.prompt,
            constraint = if (pick.direction == Direction.NOTE_TO_POSITION) AnswerConstraint(ConstraintKind.PITCH, midi = MusicFacts.midi(c.string, c.fret)) else pick.constraint)
        else pick.copy(regionProbe = probe)
    }
}
