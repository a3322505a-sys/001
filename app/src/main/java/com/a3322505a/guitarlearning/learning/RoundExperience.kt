package com.a3322505a.guitarlearning.learning

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable data class RoundEvidence(
    val sessionId: String, val region: String, val endedAt: Long, val complete: Boolean,
    val config: String, val standard: Int, val timely: Int, val protected: Boolean,
    val warmStage: Int, val warmCount: Int, val warmTimely: Int,
) { val valid: Boolean get() = complete && standard >= 5 && !protected
    val rate: Double get() = if (standard == 0) 0.0 else timely.toDouble() / standard }
@Serializable data class RoundLoad(val warmStage: Int = 0, val advance: Boolean = false, val decidedAt: Long = 0)

object RoundExperience {
    fun load(s: LearnerState, region: String) = s.roundLoads[region] ?: RoundLoad()
    fun comparable(t: LearningTask) = "${t.direction}:${t.range.firstFret}-${t.range.lastFret}:${t.range.strings.size}:${t.options.size}:${t.adaptive?.correctRepresentation}"
    fun summarize(s: LearnerState, now: Long, complete: Boolean): RoundEvidence? {
        val region = s.regionTraining?.regionId ?: return null
        val id = s.sessionId ?: return null
        val observations = s.responseObservations.values.filter { it.sessionId == id }
        val standard = observations.filter { it.task.roundSlot in 4..10 && ExperiencePolicy.plain(it.task) }
        val measured = standard.filter { it.quality == TimingQuality.VALID }
        val byId = s.attempts.associateBy { it.task.id }
        fun timely(o: ResponseObservation) = byId[o.task.id]?.let { ResponseTiming.timely(s, it) } == true
        val warm = observations.filter { it.task.roundSlot in 1..3 && ExperiencePolicy.plain(it.task) && it.quality == TimingQuality.VALID }
        val protected = RegionProtection.active(s).isNotEmpty() || observations.any { it.task.adaptive?.protectionKey != null || it.task.adaptive?.scaffolded == true || it.task.id in s.longThoughts }
        // Compare actual task conditions, including replay, rather than stage labels or percentages.
        val config = "$region:" + standard.map { comparable(it.task) + ":replay=${it.replayed}" }.distinct().sorted().joinToString("|")
        return RoundEvidence(id, region, now, complete, config, measured.size, measured.count(::timely), protected,
            load(s, region).warmStage, warm.size, warm.count(::timely))
    }
    fun finish(s: LearnerState, now: Long, complete: Boolean): LearnerState {
        val r = summarize(s, now, complete) ?: return s
        if (s.roundEvidence.any { it.sessionId == r.sessionId }) return s
        val rounds = s.roundEvidence + r
        val old = load(s, r.region)
        val comparable = rounds.filter { it.region == r.region }.asReversed().takeWhile { it.valid && it.config == r.config && it.endedAt > old.decidedAt }.asReversed()
        val window = comparable.takeLast(2).let { if (it.sumOf { a -> a.standard } < 12) comparable.takeLast(3) else it }
        val advance = window.size >= 2 && window.sumOf { it.standard } >= 12 && window.all { it.rate >= .8 } &&
            window.sumOf { it.timely }.toDouble() / window.sumOf { it.standard } >= .9
        val warm = rounds.filter { it.region == r.region }.asReversed().takeWhile { it.complete && !it.protected && it.warmStage == old.warmStage }.take(4)
        val coverage = s.responseObservations.values.filter { o -> o.sessionId in warm.map { it.sessionId } && o.task.roundSlot in 1..3 }
            .mapNotNull { it.task.coordinate?.string }.toSet()
        val requiredStrings = RegionTraining.known(s, r.region).map { it.second.string }.toSet()
        val up = warm.size == 4 && warm.sumOf { it.warmCount } == 12 && warm.sumOf { it.warmTimely } >= 11 && coverage.containsAll(requiredStrings)
        val down = warm.take(2).let { it.size == 2 && it.all { a -> a.warmCount == 3 && a.warmTimely <= 2 } }
        val stage = when { r.protected -> old.warmStage; down -> (old.warmStage - 1).coerceAtLeast(0); up -> (old.warmStage + 1).coerceAtMost(6); else -> old.warmStage }
        var next = s.copy(roundEvidence = rounds, roundLoads = s.roundLoads + (r.region to RoundLoad(stage, advance, if (advance) now else old.decidedAt)))
        if (complete && r.region == "LOW" && next.middleRecommendedAt == null && MiddleReadiness.ready(next, now))
            next = next.copy(middleRecommendedAt = now)
        return next
    }
    fun warmPool(s: LearnerState, known: List<Pair<String, Coordinate>>, slot: Int, now: Long): List<Pair<String, Coordinate>> {
        val stage = load(s, requireNotNull(s.regionTraining).regionId).warmStage
        val direction = AdaptiveEvidence.positionDirections[(slot - 1) % 2]
        val open = known.filter { it.second.fret == 0 }
        val fast = known.filter { Fluency.ready(s, AdaptiveEvidence.positionUnit(it.second, direction), now) }
        val replacement = if (stage <= 3) fast.filter { it.second.fret in 1..4 } else fast
        val replace = slot <= if (stage <= 3) stage else stage - 3
        return if (replace && replacement.isNotEmpty()) replacement else open.ifEmpty { fast }.ifEmpty { known }
    }
    fun main(s: LearnerState, scheduler: LessonScheduler, random: Random, now: Long, slot: Int): LearningTask {
        val region = requireNotNull(s.regionTraining).regionId
        val known = RegionTraining.known(s, region).distinctBy { it.second }
        val nextNode = RegionTraining.nodes(region).firstOrNull { n -> Curriculum.available(s, n) && n.positions.any { AdaptiveEvidence.positionTarget(it) !in s.introductions } }
        val newPoint = nextNode?.positions?.firstOrNull { AdaptiveEvidence.positionTarget(it) !in s.introductions }
        // At most one introduction/probe in seven main slots. Middle begins with its existing two-point lesson.
        if (slot == 7 && newPoint != null && (load(s, region).advance || known.size < 2 || region == "MIDDLE" && known.none { it.second.fret in 5..8 }))
            return scheduler.makePosition(nextNode.id, newPoint, Direction.NOTE_TO_POSITION, TaskSource.DEMONSTRATION)
                .copy(introductionId = AdaptiveEvidence.positionTarget(newPoint), adaptive = AdaptiveTask("round:$region", PracticePurpose.NEXT))
        if (known.isEmpty()) return AdaptiveTraining.next(s, scheduler, random, now)
        val current = s.attempts.filter { it.sessionId == s.sessionId }
        val standardCurrent = current.filter { it.task.roundSlot in 4..10 }
        val d = AdaptiveEvidence.positionDirections.minBy { direction -> standardCurrent.count { it.task.direction == direction } }
        val view = AdaptiveEvidence.View(s, now)
        var pool = known
        if (region == "MIDDLE" && known.count { it.second.fret in 5..8 } <= 2 && slot != 7)
            pool = known.filter { it.second.fret <= 4 }.ifEmpty { known }
        val (node, c) = pool.shuffled(random).minWith(compareBy<Pair<String, Coordinate>> {
            current.count { a -> a.task.coordinate == it.second }
        }.thenBy { view.unit(AdaptiveEvidence.positionUnit(it.second, d)).size }
            .thenBy { view.lastExposure(AdaptiveEvidence.positionTarget(it.second)) ?: 0L })
        return scheduler.makePosition(node, c, d, TaskSource.MAIN).copy(adaptive = AdaptiveTask("round:$region", PracticePurpose.COVERAGE, unit = AdaptiveEvidence.positionUnit(c, d)))
    }
}

/** Current fluency uses a versioned 20-sample / one-error candidate, not a 4/4 or lifetime score. */
object Fluency {
    fun ready(s: LearnerState, unit: String, now: Long): Boolean {
        val protections = s.positionProtections.values.filter { it.unit == unit }
        if (protections.any { it.resolvedAt == null }) return false
        val after = protections.maxOfOrNull { it.since } ?: 0L
        val ids = AdaptiveEvidence.View(s, now).samples.filter { it.unit == unit && it.at > after }.map { it.taskId }.toSet()
        val attempts = s.attempts.filter { it.task.id in ids && ExperiencePolicy.plain(it.task) && s.responseObservations[it.task.id]?.replayed == false }
            .takeLast(ExperiencePolicy.FLUENT_WINDOW)
        if (attempts.size < ExperiencePolicy.FLUENT_WINDOW) return false
        val good = attempts.filter { ResponseTiming.timely(s, it) && (s.responseObservations[it.task.id]?.durationMs ?: Long.MAX_VALUE) <= ExperiencePolicy.fast(it.task.direction) }
        return good.size >= ExperiencePolicy.FLUENT_WINDOW - ExperiencePolicy.FLUENT_ALLOWED_ERRORS && attempts.takeLast(2).all { it in good } &&
            attempts.map { it.sessionId }.distinct().size >= 2 && good.any { MiddleReadiness.retained(s, it) }
    }
}

object MiddleReadiness {
    fun retained(s: LearnerState, a: Attempt): Boolean {
        val c = a.task.coordinate ?: return false
        val target = AdaptiveEvidence.positionTarget(c)
        val previous = s.knowledgeExposures.filter { it.target == target && it.taskId != a.task.id && it.at < a.at }.maxOfOrNull { it.at } ?: return false
        val previousSession = s.attempts.lastOrNull { it.task.coordinate == c && it.at < a.at }?.sessionId
        return previousSession != null && previousSession != a.sessionId && a.at - previous >= ExperiencePolicy.RETENTION_MS
    }
    fun ready(s: LearnerState, now: Long): Boolean {
        if (!Curriculum.mastered(s, "p09")) return false
        val positions = AdaptiveEvidence.targets(FretboardRegion.LOW)
        if (positions.any { AdaptiveEvidence.positionTarget(it) !in s.introductions }) return false
        if (s.positionProtections.values.any { it.resolvedAt == null && it.original.coordinate in positions }) return false
        val rounds = s.roundEvidence.filter { it.region == "LOW" }.takeLast(6)
        if (rounds.size < 6 || rounds.any { !it.valid || it.rate < .8 } || rounds.map { it.config }.distinct().size != 1 ||
            rounds.sumOf { it.standard } < 36 || rounds.sumOf { it.timely }.toDouble() / rounds.sumOf { it.standard } < .9) return false
        val view = AdaptiveEvidence.View(s, now)
        val units = positions.flatMap { c -> AdaptiveEvidence.positionDirections.map { AdaptiveEvidence.positionUnit(c, it) } }
        if (units.any { view.unit(it).size < 2 } || units.count { view.ready(it) } < 29 ||
            units.any { s.weakPoints[it]?.let { p -> p.confirmedAt != null && p.resolvedAt == null } == true }) return false
        val sessionIds = rounds.map { it.sessionId }.toSet()
        val standard = s.attempts.filter { it.sessionId in sessionIds && it.task.roundSlot in 4..10 && ExperiencePolicy.plain(it.task) && it.firstUnassisted == true }
        if (rounds.any { r -> standard.count { it.sessionId == r.sessionId } < 6 }) return false
        for (d in AdaptiveEvidence.positionDirections) {
            val correct = standard.filter { it.task.direction == d && it.firstCorrect == true }
            val timed = correct.mapNotNull { a -> s.responseObservations[a.task.id]?.takeIf { it.quality == TimingQuality.VALID && !it.replayed }?.durationMs }.sorted()
            if (timed.size < 12 || timed.size < correct.size * .7 || timed[timed.size / 2] > ExperiencePolicy.deadline(d)) return false
        }
        val retained = standard.filter { it.task.coordinate in positions && retained(s, it) }
        // Select one actual retention opportunity per string; repeated targets cannot inflate six checks.
        val checks = (1..6).mapNotNull { string -> retained.lastOrNull { it.task.coordinate?.string == string } }
        return checks.size == 6 && checks.mapNotNull { it.task.coordinate }.distinct().size == 6 &&
            checks.count { it.firstCorrect == true } >= 5 && AdaptiveEvidence.positionDirections.all { d -> checks.count { it.task.direction == d } >= 2 }
    }
}
