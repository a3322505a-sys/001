package com.a3322505a.guitarlearning.learning

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable data class PositionProtection(
    val unit: String, val original: LearningTask, val since: Long, val signal: String,
    val resolvedAt: Long? = null, val isolated: Boolean = false,
)

/** Protection is a durable target/direction/configuration fact, independent of round and spacing. */
object RegionProtection {
    fun config(t: LearningTask): String = "${t.direction}:${t.range.firstFret}-${t.range.lastFret}:${t.range.strings.sorted()}:${t.options.size}:${t.constraint.kind}:${t.adaptive?.correctRepresentation ?: AnswerRepresentation.NOTE}"
    fun key(t: LearningTask) = "${t.coordinate?.id}:${config(t)}"
    fun active(s: LearnerState): List<PositionProtection> {
        val coordinates = RegionTraining.nodes(s.regionTraining?.regionId ?: FretboardRegion.LOW.name).flatMap { it.positions }.toSet()
        return s.positionProtections.values.filter { it.resolvedAt == null && it.original.coordinate in coordinates }
    }
    fun protect(s: LearnerState, t: LearningTask, now: Long): LearnerState {
        if (t.coordinate == null || t.direction !in AdaptiveEvidence.positionDirections || t.guided) return s
        val inherited = t.adaptive?.protectionKey?.let { s.positionProtections[it] }
        val original = inherited?.original ?: if (t.adaptive?.options?.isNotEmpty() == true)
            LessonScheduler().makePosition(t.nodeId, t.coordinate, t.direction, TaskSource.MAIN) else t
        val key = key(original)
        val old = s.positionProtections[key]
        if (old?.signal == t.id) return s
        val p = PositionProtection(AdaptiveEvidence.positionUnit(t.coordinate, t.direction), original,
            old?.since?.takeIf { old.resolvedAt == null } ?: now, t.id,
            isolated = t.id !in s.longThoughts && s.active?.hintRequested != true && (old?.takeIf { it.resolvedAt == null }?.let { it.isolated && it.signal == t.id } ?:
                Fluency.ready(s.copy(attempts = s.attempts.filterNot { it.task.id == t.id }), AdaptiveEvidence.positionUnit(t.coordinate, t.direction), now)))
        return s.copy(positionProtections = s.positionProtections + (key to p))
    }
    fun transition(s: LearnerState, now: Long): LearnerState {
        var next = s
        val a = s.active
        if (a != null && !a.task.guided && (a.firstCorrect == false || a.hintRequested || a.task.id in s.longThoughts))
            next = protect(next, a.task, s.longThoughts[a.task.id]?.at ?: a.firstAnswerAt ?: now)
        val view = AdaptiveEvidence.View(next, now)
        val updated = next.positionProtections.mapValues { (key, p) ->
            val samples = view.samples.filter { it.unit == p.unit && it.at > p.since && it.task.adaptive?.protectionKey == key && it.task.adaptive.originalProbe }
            val lastSignalAt = next.attempts.firstOrNull { it.task.id == p.signal }?.at ?: next.longThoughts[p.signal]?.at ?: p.since
            if (p.resolvedAt == null && (if (p.isolated) samples.lastOrNull()?.let { it.correct && it.at > lastSignalAt } == true else AdaptiveEvidence.recovered(samples) && samples.takeLast(2).all { it.at > lastSignalAt })) p.copy(resolvedAt = now) else p
        }
        next = next.copy(positionProtections = updated)
        val r = next.regionTraining ?: return next
        val pending = active(next)
        return next.copy(regionTraining = r.copy(adaptive = r.adaptive.copy(diagnosing = pending.isNotEmpty(),
            scaffolding = pending.isNotEmpty(), focus = pending.map { it.unit }.distinct(),
            layer = if (pending.isEmpty()) RecoveryLayer.REGION else RecoveryLayer.LOCAL,
            mixStage = if (pending.isEmpty()) r.adaptive.mixStage else 0,
            trial = pending.isEmpty() && updated.values.any { it.resolvedAt == now },
            reason = if (pending.isEmpty()) null else "先巩固这几个音")))
    }
    fun adopt(s: LearnerState, scheduler: LessonScheduler, now: Long): LearnerState {
        val r = s.regionTraining ?: return s
        if (!r.adaptive.scaffolding || active(s).isNotEmpty()) return s
        val known = RegionTraining.known(s, r.regionId)
        val targets = known.flatMap { (node, c) -> AdaptiveEvidence.positionDirections.map { scheduler.makePosition(node, c, it, TaskSource.REVIEW) } }
            .filter { AdaptiveEvidence.unit(it) in r.adaptive.focus }.ifEmpty {
                known.take(1).flatMap { (node, c) -> AdaptiveEvidence.positionDirections.map { scheduler.makePosition(node, c, it, TaskSource.REVIEW) } }
            }
        return targets.fold(s) { state, t -> protect(state, t, now) }
    }
    fun next(s: LearnerState, random: Random, now: Long): LearningTask? {
        val pending = active(s).sortedBy { it.since }.take(2)
        if (pending.isEmpty()) return null
        // Restrict target set before choosing any slot. Rotate pairs; no full-strength empty fallback.
        val p = pending.minBy { p -> s.attempts.lastOrNull { it.task.adaptive?.protectionKey == key(p.original) }?.ordinal ?: -1 }
        val key = key(p.original)
        val evidence = s.attempts.filter { it.task.adaptive?.protectionKey == key && it.at > p.since && it.completed && !it.task.guided }
        val lastProbe = evidence.lastOrNull { it.task.adaptive?.originalProbe == true }
        val simple = evidence.filter { it.task.adaptive?.scaffolded == true && it.ordinal > (lastProbe?.ordinal ?: -1) }.takeLast(4)
        val before = s.attempts.lastOrNull()
        val ready = p.isolated || simple.size == 4 && simple.count { it.firstCorrect == true && it.hintLevel == 0 } >= 3 && simple.takeLast(2).all { it.firstCorrect == true && it.hintLevel == 0 }
        val baseEligible = AdaptiveEvidence.View(s, now).eligible(p.original)
        val probe = ready && baseEligible && before?.task?.adaptive?.scaffolded == true && before.completed
        if (ready && !probe) {
            // Two distinct taught easy targets supply real spacing before an original-condition probe.
            val known = RegionTraining.known(s, requireNotNull(s.regionTraining).regionId)
            val fillers = known.filter { it.second != p.original.coordinate }
                .sortedBy { kotlin.math.abs(it.second.fret - p.original.coordinate!!.fret) + kotlin.math.abs(it.second.string - p.original.coordinate.string) * 2 }.take(2)
            if (fillers.isNotEmpty()) {
                val (node, c) = fillers.minBy { pair -> s.attempts.lastOrNull { it.task.coordinate == pair.second }?.ordinal ?: -1 }
                val base = LessonScheduler(random).makePosition(node, c, p.original.direction, TaskSource.REVIEW)
                return AdaptiveTraining.simplify(s, base, fillers.map { it.second }, random).copy(
                    adaptive = AdaptiveTask("protected-familiar:$key", PracticePurpose.FAMILIAR, scaffolded = true, unit = AdaptiveEvidence.unit(base)))
            }
        }
        val base = p.original.copy(id = newId(), source = TaskSource.REVIEW, introductionId = null, roundSlot = null)
        val task = if (probe) base else AdaptiveTraining.simplify(s, base,
            pending.mapNotNull { it.original.coordinate }, random)
        val repeatedDifficulty = evidence.takeLast(2).let { it.size == 2 && it.none { a -> a.firstCorrect == true } }
        val assisted = !probe && repeatedDifficulty && before?.task?.guided != true
        return task.copy(source = if (assisted) TaskSource.DEMONSTRATION else task.source, adaptive = AdaptiveTask("protection:$key", if (probe) PracticePurpose.RETEST else PracticePurpose.DIAGNOSIS,
            unit = p.unit, scaffolded = !probe, protectionKey = key, originalProbe = probe))
    }
}
