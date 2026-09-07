package com.a3322505a.guitarlearning.learning

import kotlin.random.Random

/** Task identity counts operations; corrections and evidence qualification never extend a round. */
object RegionRounds {
    const val SIZE = 12
    fun issued(s: LearnerState): List<String> {
        val run = s.regionTraining ?: return emptyList()
        return if (run.roundEnabled) run.issuedTasks else
            (s.attempts.filter { it.sessionId == s.sessionId }.map { it.task.id } + listOfNotNull(s.active?.task?.id)).distinct()
    }
    fun finished(s: LearnerState) = issued(s).size >= SIZE
    fun present(s: LearnerState, t: LearningTask): LearnerState {
        val r = s.regionTraining ?: return s
        if (s.practice != null || s.pilot != null) return s
        val ids = (issued(s) + t.id).distinct()
        return s.copy(regionTraining = r.copy(roundEnabled = true, issuedTasks = ids))
    }
    fun next(s: LearnerState, scheduler: LessonScheduler, random: Random, now: Long): LearningTask {
        val run = requireNotNull(s.regionTraining)
        val slot = issued(s).size + 1
        check(slot <= SIZE) { "本轮已结束。" }
        RegionProgression.next(s, scheduler)?.let { return it.copy(roundSlot = slot) }
        RegionProtection.next(s, random, now)?.let { return it.copy(roundSlot = slot) }
        val known = RegionTraining.known(s, run.regionId).distinctBy { it.second }
        val pool = when {
            slot <= 3 -> RoundExperience.warmPool(s, known, slot, now)
            slot >= 11 -> {
                val practiced = s.attempts.filter { it.sessionId == s.sessionId }.mapNotNull { it.task.coordinate }.toSet()
                known.filter { it.second in practiced }.ifEmpty { known }
            }
            else -> emptyList()
        }
        val task = if (slot <= 3 && pool.isEmpty()) {
            val available = RegionTraining.nodes(run.regionId).filter { RegionProgression.available(s, it) }
            val earliest = available.firstNotNullOfOrNull { n -> n.positions.firstOrNull { it.fret == 0 }?.let { n.id to it } }
            if (earliest == null) AdaptiveTraining.next(s, scheduler, random, now) else
                scheduler.makePosition(earliest.first, earliest.second, Direction.NOTE_TO_POSITION, TaskSource.DEMONSTRATION)
                    .copy(introductionId = AdaptiveEvidence.positionTarget(earliest.second))
        } else if (pool.isNotEmpty()) {
            val recent = s.attempts.filter { it.sessionId == s.sessionId }.mapNotNull { it.task.coordinate }
            val (node, c) = pool.shuffled(random).minBy { (_, coordinate) -> recent.count { it == coordinate } }
            val d = AdaptiveEvidence.positionDirections[(slot - 1) % 2]
            val base = scheduler.makePosition(node, c, d, TaskSource.REVIEW)
            val practice = if (run.adaptive.scaffolding) AdaptiveTraining.simplify(s, base, known.map { it.second }, random) else base
            practice.copy(adaptive = AdaptiveTask(run.adaptive.config, PracticePurpose.FAMILIAR,
                unit = AdaptiveEvidence.unit(base), scaffolded = run.adaptive.scaffolding))
        } else RoundExperience.main(s, scheduler, random, now, slot)
        return task.copy(roundSlot = slot)
    }
}
