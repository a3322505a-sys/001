package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.core.MusicFacts
import kotlin.random.Random

/** Region adaptation shares the coordinator's transaction and the original teaching/prerequisites. */
object AdaptiveTraining {
    private val directions = AdaptiveEvidence.positionDirections
    private fun nextOrdinal(s: LearnerState) = (s.attempts.maxOfOrNull { it.ordinal } ?: 0) + 1
    internal fun completedScorable(s: LearnerState, view: AdaptiveEvidence.View): List<Attempt> {
        val scored = view.allSamples.map { it.taskId }.toSet()
        return s.attempts.filter { it.sessionId == s.sessionId && it.completed && it.task.id in scored && it.task.adaptive != null }
    }
    private fun scoped(s: LearnerState, view: AdaptiveEvidence.View): List<AssessmentSample> {
        val r = s.regionTraining ?: return emptyList()
        val sessionTasks = s.attempts.filter { it.sessionId == s.sessionId }.map { it.task.id }.toSet()
        return view.samples.filter { it.task.adaptive?.config == r.adaptive.config && it.ordinal >= r.adaptive.sinceOrdinal && it.taskId in sessionTasks }
    }
    fun transition(s: LearnerState, now: Long): LearnerState {
        val view = AdaptiveEvidence.View(s, now)
        val points = s.weakPoints.toMutableMap()
        // First-answer identity is fixed; correction and persistence retries cannot create new failures.
        view.samples.filter { it.unit.startsWith("position:") || it.unit.startsWith("mapping:") }.groupBy { it.unit }.forEach { (key, evidence) ->
            val last = evidence.last()
            val old = points[key]
            if (!last.correct && old?.lastFailure != last.taskId) {
                val active = old?.takeIf { it.resolvedAt == null }
                points[key] = (active ?: WeakPoint(key, last.target, last.at)).copy(
                    confirmedAt = active?.confirmedAt ?: last.at.takeIf { view.weak(key) }, lastFailure = last.taskId)
            } else if (old != null && old.resolvedAt == null) {
                val after = evidence.filter { it.at > (old.confirmedAt ?: old.observedAt) }
                if (old.confirmedAt == null && view.ready(key) || old.confirmedAt != null && AdaptiveEvidence.recovered(after) && !view.weak(key) && after.any { it.retention })
                    points[key] = old.copy(resolvedAt = now)
            }
        }
        var result = s.copy(weakPoints = points)
        val region = s.regionTraining ?: return result
        var run = region.adaptive
        val known = RegionTraining.known(s, region.regionId).map { it.second }.distinct()
        if (run.sevenQualifiedAt == null) {
            val low = AdaptiveEvidence.targets(FretboardRegion.LOW).filter { "position:${it.id}" in s.introductions }
            val representatives = MappingLessons.notes.mapNotNull { note -> low.filter { MusicFacts.note(it.string, it.fret) == note }
                .firstOrNull { c -> directions.all { view.ready(AdaptiveEvidence.positionUnit(c, it)) && view.held(AdaptiveEvidence.positionUnit(c, it)) } } }
            val measurement = view.region(FretboardRegion.LOW)
            if (measurement.measured.toDouble() / measurement.total >= 0.6 && representatives.size == 7)
                run = run.copy(sevenQualifiedAt = now, representatives = representatives)
        }
        val window = scoped(s, view)
        val last = window.lastOrNull()
        val failures = window.takeLast(8)
        val overloaded = failures.takeLast(3).let { it.size == 3 && it.none { a -> a.correct } } || failures.let { it.size == 8 && it.count { a -> !a.correct } >= 4 }
        val newFailure = last != null && !last.correct && last.taskId != run.handledFailure
        if (newFailure && !run.diagnosing && (overloaded || view.weak(requireNotNull(last).unit))) {
            val affected = failures.filter { !it.correct }.takeLast(4).flatMap { diagnosticUnits(it.task, s.attempts.first { a -> a.task.id == it.taskId }) }.distinct()
            run = run.copy(generation = run.generation + 1, sinceOrdinal = nextOrdinal(s), diagnosing = true,
                diagnosisSince = nextOrdinal(s), handledFailure = last!!.taskId, focus = affected,
                previousLayer = if (run.layer == RecoveryLayer.REGION) run.layer else run.previousLayer,
                layer = RecoveryLayer.LOCAL, trial = false, reason = "先巩固这几个音")
        } else if (run.diagnosing) {
            val sessionTasks = s.attempts.filter { it.sessionId == s.sessionId }.map { it.task.id }.toSet()
            val diagnosed = view.samples.filter { it.ordinal >= run.diagnosisSince && it.taskId in sessionTasks && (it.unit.startsWith("position:") || it.unit.startsWith("mapping:")) }
            val basics = diagnosed.filter { it.unit.startsWith("position:") }.takeLast(8)
            val broad = basics.size == 8 && basics.count { !it.correct } >= 4 && basics.filter { !it.correct }.map { it.target }.distinct().size >= 3
            val relevant = run.focus.mapNotNull { points[it] }.filter { it.confirmedAt != null && it.resolvedAt == null }
            val mappings = relevant.filter { it.unit.startsWith("mapping:") }
            val foundationReady = run.focus.filter { it.startsWith("position:") }.all { key -> view.ready(key) && diagnosed.any { it.unit == key } }
            val recoveryCoordinates = when (run.layer) {
                RecoveryLayer.OPEN -> known.filter { it.fret == 0 && (run.focus.isEmpty() || run.focus.any { key -> key.contains("s${it.string}:") }) }
                RecoveryLayer.NATURAL -> run.representatives
                else -> emptyList()
            }
            val recoveryKeys = recoveryCoordinates.flatMap { c -> directions.map { AdaptiveEvidence.positionUnit(c, it) } }
            val recoveryChecks = diagnosed.filter { it.unit in recoveryKeys }.takeLast(8)
            if (mappings.isNotEmpty() && foundationReady) {
                val excluded = mappings.map { if (it.unit.startsWith("mapping:fixed:")) AnswerRepresentation.FIXED else AnswerRepresentation.DEGREE }.toSet()
                run = run.copy(generation = run.generation + 1, sinceOrdinal = nextOrdinal(s), diagnosing = false,
                    layer = run.previousLayer, excluded = run.excluded + excluded, reason = "先巩固这几个音")
            } else if (recoveryKeys.isNotEmpty() && recoveryKeys.all { view.ready(it) } && recoveryChecks.size >= 8 && recoveryChecks.takeLast(8).count { it.correct } >= 7) {
                // Stable simpler foundations lead back to the original local targets before the old mix.
                run = run.copy(layer = RecoveryLayer.LOCAL, generation = run.generation + 1,
                    sinceOrdinal = nextOrdinal(s), diagnosisSince = nextOrdinal(s))
            } else if (broad || run.layer == RecoveryLayer.NATURAL && basics.lastOrNull()?.let { !it.correct && view.weak(it.unit) && it.taskId != run.handledFailure } == true) {
                val layer = if (run.layer == RecoveryLayer.NATURAL || run.sevenQualifiedAt == null) RecoveryLayer.OPEN else RecoveryLayer.NATURAL
                if (layer != run.layer) run = run.copy(layer = layer, generation = run.generation + 1,
                    sinceOrdinal = nextOrdinal(s), diagnosisSince = nextOrdinal(s), handledFailure = basics.last().taskId)
            } else if (run.focus.isNotEmpty() && run.focus.all { key ->
                val weak = points[key]?.takeIf { it.confirmedAt != null && it.resolvedAt == null }
                if (weak == null) view.ready(key) && diagnosed.any { it.unit == key } else targetedRecovery(view, weak)
            }) {
                run = run.copy(generation = run.generation + 1, sinceOrdinal = nextOrdinal(s), diagnosing = false,
                    layer = run.previousLayer, trial = true, reason = null,
                    mixStage = if (relevant.isEmpty() && run.focus.any { it.startsWith("mapping:") }) (run.mixStage - 1).coerceAtLeast(0) else run.mixStage)
            }
        } else if (run.trial && window.size >= 8 && window.takeLast(8).count { it.correct } >= 7) {
            run = run.copy(trial = false, focus = run.focus.filter { points[it]?.resolvedAt == null })
        }
        val keys = known.flatMap { c -> directions.map { AdaptiveEvidence.positionUnit(c, it) } }
        val readyCount = keys.count { view.ready(it) }
        val unresolved = points.values.any { it.unit in keys && it.confirmedAt != null && it.resolvedAt == null }
        if (!run.diagnosing && !run.trial && !run.wide && region.regionId == FretboardRegion.FULL.name &&
            window.size >= 8 && window.takeLast(8).count { it.correct } >= 7 && keys.isNotEmpty() && readyCount.toDouble() / keys.size >= 0.8 && !unresolved)
            run = run.copy(wide = true, generation = run.generation + 1, sinceOrdinal = nextOrdinal(s))
        result = result.copy(regionTraining = region.copy(adaptive = run))
        return result
    }
    private fun targetedRecovery(view: AdaptiveEvidence.View, point: WeakPoint): Boolean = AdaptiveEvidence.recovered(
        view.samples.filter { it.unit == point.unit && it.at > requireNotNull(point.confirmedAt) && it.task.adaptive?.purpose in listOf(PracticePurpose.WEAK, PracticePurpose.DIAGNOSIS, PracticePurpose.RETEST, PracticePurpose.MAPPING) })
    private fun diagnosticUnits(t: LearningTask, attempt: Attempt): List<String> {
        if (t.adaptive?.options?.isNotEmpty() != true) return listOfNotNull(AdaptiveEvidence.unit(t))
        val c = t.coordinate ?: return emptyList()
        val wrong = AdaptiveEvidence.firstInput(attempt)?.symbol
        val reps = listOfNotNull(t.adaptive.correctRepresentation, t.adaptive.options.firstOrNull { it.label == wrong }?.representation).distinct()
        val note = MusicFacts.note(c.string, c.fret)
        return listOf(AdaptiveEvidence.positionUnit(c, Direction.POSITION_TO_NOTE)) + reps.mapNotNull { representation ->
            when (representation) {
                AnswerRepresentation.FIXED -> "mapping:fixed:$note:${Direction.NOTE_TO_SOLFEGE.name}"
                AnswerRepresentation.DEGREE -> "mapping:major:${t.tonicPitchClass}:$note:${Direction.NOTE_TO_DEGREE.name}"
                else -> null
            }
        }
    }
    fun onPresented(s: LearnerState, task: LearningTask, now: Long): LearnerState {
        val key = task.adaptive?.unit ?: return s
        val point = s.weakPoints[key] ?: return s
        return if (task.guided && point.resolvedAt == null) s.copy(weakPoints = s.weakPoints + (key to point.copy(taughtAt = now))) else s
    }
    fun next(s: LearnerState, scheduler: LessonScheduler, random: Random, now: Long): LearningTask {
        val region = requireNotNull(s.regionTraining)
        val run = region.adaptive
        val view = AdaptiveEvidence.View(s, now)
        val known = RegionTraining.known(s, region.regionId).distinctBy { it.second }
        val history = RegionTraining.history(s)
        val probe = history.count { it.task.regionProbe } < region.probeSize
        val completed = completedScorable(s, view)
        val slot = completed.size % 6
        val block = completed.size / 6
        val pending = RegionTraining.region(region.regionId).nodes.firstOrNull { Curriculum.available(s, it) && !Curriculum.mastered(s, it.id) }
        val unseen = pending?.positions?.firstOrNull { "position:${it.id}" !in s.introductions }
        fun tag(t: LearningTask, purpose: PracticePurpose, key: String? = AdaptiveEvidence.unit(t)) = t.copy(
            regionProbe = probe, adaptive = AdaptiveTask(run.config, purpose, run.mixStage, unit = key))
        fun introduce() = tag(scheduler.makePosition(requireNotNull(pending).id, requireNotNull(unseen), Direction.NOTE_TO_POSITION, TaskSource.DEMONSTRATION)
            .copy(introductionId = "position:${unseen.id}"), PracticePurpose.NEXT)
        if (known.isEmpty()) return introduce()
        val all = known.flatMap { (node, coordinate) -> directions.map { scheduler.makePosition(node, coordinate, it, TaskSource.MAIN) } }
        val waiting = s.weakPoints.values.filter { point ->
            point.confirmedAt != null && point.resolvedAt == null && targetedRecovery(view, point) &&
                view.lastExposure(point.target)?.let { now - it < AdaptiveEvidence.HOLD_MS } == true
        }.map { it.target }.toSet()
        val eligible = all.filter { view.eligible(it) && AdaptiveEvidence.targets(it).none { target -> target in waiting } }
        if (unseen != null && !probe && (eligible.isEmpty() || slot == 5 && block % 3 == 2)) return introduce()
        val candidates = eligible.ifEmpty { all.filter { it.coordinate != s.attempts.lastOrNull()?.task?.coordinate }.ifEmpty { all } }
        val scopedKeys = all.mapNotNull { AdaptiveEvidence.unit(it) }.toSet()
        val weak = s.weakPoints.values.filter { it.resolvedAt == null && it.target !in waiting && (it.unit in scopedKeys || it.unit.startsWith("mapping:") && it.unit in run.focus) }
            .sortedWith(compareBy<WeakPoint> { if (it.confirmedAt != null && targetedRecovery(view, it) && (view.lastExposure(it.target)?.let { at -> now - at < AdaptiveEvidence.HOLD_MS } == true)) 2 else if (it.confirmedAt != null) 0 else 1 }.thenBy { it.observedAt })
        val blockTargets = completed.takeLast(slot).filter { it.task.adaptive?.purpose in listOf(PracticePurpose.WEAK, PracticePurpose.DIAGNOSIS, PracticePurpose.MAPPING) }.mapNotNull { it.task.adaptive?.unit }.distinct()
        val focused = (blockTargets + weak.map { it.unit } + run.focus.filter { key -> waiting.none { key.startsWith("$it:") } }).distinct().take(2)
        var purpose = if (probe) PracticePurpose.COVERAGE else when (slot) {
            0, 2, 4 -> if (run.diagnosing) PracticePurpose.DIAGNOSIS else PracticePurpose.WEAK
            1, 3 -> PracticePurpose.FAMILIAR
            else -> listOf(PracticePurpose.COVERAGE, PracticePurpose.RETEST, PracticePurpose.NEXT)[block % 3]
        }
        val mappingKeys = focused.filter { it.startsWith("mapping:") }
        if (purpose in listOf(PracticePurpose.WEAK, PracticePurpose.DIAGNOSIS) && mappingKeys.isNotEmpty()) {
            val nextBasicAt = candidates.filter { AdaptiveEvidence.unit(it) in focused }.minOfOrNull { view.unit(requireNotNull(AdaptiveEvidence.unit(it))).lastOrNull()?.at ?: 0L } ?: Long.MAX_VALUE
            mappingKeys.mapNotNull { mappingTask(it) }.firstOrNull { view.eligible(it) && (view.unit(requireNotNull(AdaptiveEvidence.unit(it))).lastOrNull()?.at ?: 0L) <= nextBasicAt }?.let { t ->
                val key = requireNotNull(AdaptiveEvidence.unit(t))
                val point = s.weakPoints[key]
                return if (point?.confirmedAt != null && point.taughtAt == null) tag(t.copy(source = TaskSource.DEMONSTRATION), purpose, key) else tag(t, purpose, key)
            }
        }
        val local = candidates.filter { AdaptiveEvidence.unit(it) in focused }
        val recoveryPool = when (run.layer) {
            RecoveryLayer.NATURAL -> candidates.filter { it.coordinate in run.representatives }
            RecoveryLayer.OPEN -> candidates.filter { task -> val c = requireNotNull(task.coordinate); c.fret == 0 && (run.focus.isEmpty() || run.focus.any { key -> key.contains("s${c.string}:") }) }.ifEmpty { candidates.filter { it.coordinate?.fret == 0 } }
            else -> local
        }
        val familiar = candidates.filter { AdaptiveEvidence.unit(it)?.let { key -> view.ready(key) } == true }
        val selectedPool = when (purpose) {
            PracticePurpose.WEAK, PracticePurpose.DIAGNOSIS -> recoveryPool.ifEmpty { local }.ifEmpty { candidates }
            PracticePurpose.FAMILIAR -> familiar.ifEmpty { candidates.filter { t -> weak.none { it.unit == AdaptiveEvidence.unit(t) && it.confirmedAt != null } } }.ifEmpty { candidates }
            else -> candidates
        }
        if (purpose == PracticePurpose.WEAK && selectedPool.none { AdaptiveEvidence.unit(it) in focused }) purpose = PracticePurpose.COVERAGE
        val pick = selectedPool.shuffled(random).minBy { t ->
            val evidence = view.unit(requireNotNull(AdaptiveEvidence.unit(t)))
            when (purpose) {
                PracticePurpose.COVERAGE, PracticePurpose.NEXT -> evidence.size.toLong() * AdaptiveEvidence.HOLD_MS + (view.lastExposure(AdaptiveEvidence.targets(t).first()) ?: 0L)
                else -> evidence.lastOrNull()?.at ?: 0L
            }
        }
        val key = requireNotNull(AdaptiveEvidence.unit(pick))
        val point = s.weakPoints[key]
        if (!probe && purpose in listOf(PracticePurpose.WEAK, PracticePurpose.DIAGNOSIS) && point?.confirmedAt != null && point.resolvedAt == null && point.taughtAt == null)
            return tag(pick.copy(source = TaskSource.DEMONSTRATION), purpose, key)
        val widened = if (run.wide && run.layer == RecoveryLayer.REGION && view.ready(key) && pick.direction == Direction.NOTE_TO_POSITION) pick.copy(
            range = PhysicalRange(0, 12, setOf(requireNotNull(pick.coordinate).string)),
            constraint = AnswerConstraint(ConstraintKind.PITCH, midi = MusicFacts.midi(pick.coordinate.string, pick.coordinate.fret))) else pick
        return tag(widened, purpose, key)
    }
    fun mappingTask(unit: String): LearningTask? {
        if (!unit.startsWith("mapping:")) return null
        val parts = unit.split(":")
        if (parts.size !in 4..5 || parts[1] !in listOf("fixed", "major")) return null
        val direction = Direction.entries.firstOrNull { it.name == parts.last() } ?: return null
        val note = parts[parts.lastIndex - 1]
        if (note !in MappingLessons.notes) return null
        if (parts[1] == "fixed" && (parts.size != 4 || direction !in MappingLessons.fixedDirections)) return null
        if (parts[1] == "major" && (parts.size != 5 || direction !in MappingLessons.degreeDirections)) return null
        val tonic = if (parts[1] == "major") parts[2].toIntOrNull()?.takeIf { it in 0..11 } ?: return null else 0
        if (direction in MappingLessons.degreeDirections && MusicFacts.majorDegree(MusicFacts.noteNames.indexOf(note), tonic) == null) return null
        return MappingLessons.make(note, direction, TaskSource.MAIN, tonic)
    }
}
