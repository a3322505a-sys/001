package com.a3322505a.guitarlearning.learning

import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Context stays in the same profile, keyed by session/node and mapping representation.
 * Pausing another activity therefore cannot replace this activity's configuration. */
@Serializable data class FamilyContext(
    val sessionId: String, val nodeId: String, val anchor: Direction,
    val run: AdaptiveRun = AdaptiveRun(mixStage = 1),
)

/** Adapters for existing material; thresholds and evidence are shared with region training. */
object FamilyAdaptation {
    fun supported(t: LearningTask): Boolean = t.notation?.score?.id?.startsWith("pilot-") != true &&
        (t.nodeId in ReadingLessons.ids || t.nodeId == "tab01" || t.nodeId == "mapping" ||
            ChordLessons.shapes(t.nodeId).isNotEmpty() || t.nodeId in StructureLessons.ids)

    fun scope(s: LearnerState, t: LearningTask): String = "family/${t.nodeId}" +
        if (t.nodeId == "mapping") "/${if (t.direction in MappingLessons.fixedDirections) "fixed" else "degree:${t.tonicPitchClass}"}" else ""

    fun onPresented(s: LearnerState, t: LearningTask): LearnerState {
        val key = t.adaptive?.familyScope ?: return s
        if (key in s.familyRuns) return s
        val legacy = s.familyRuns.values.lastOrNull { it.nodeId == t.nodeId && it.run.diagnosing &&
            (t.nodeId != "mapping" || (it.anchor in MappingLessons.fixedDirections) == (t.direction in MappingLessons.fixedDirections)) }
        if (legacy != null) return s.copy(familyRuns = s.familyRuns + (key to legacy))
        return s.copy(familyRuns = s.familyRuns + (key to FamilyContext(requireNotNull(s.sessionId), t.nodeId, t.direction)))
    }

    private fun ordinal(s: LearnerState) = (s.attempts.maxOfOrNull { it.ordinal } ?: 0) + 1
    private val targeted = setOf(PracticePurpose.WEAK, PracticePurpose.DIAGNOSIS, PracticePurpose.RETEST)

    internal fun readingPositions(s: LearnerState, id: String): List<Coordinate> {
        if (Curriculum.mastered(s, id)) return ReadingLessons.positions
        val exposed = s.knowledgeExposures.map { it.target }.toSet() + s.attempts.filter { it.task.nodeId == id }.flatMap { a ->
            if (a.task.guided) AdaptiveEvidence.targets(a.task) else a.members.map { "skill:${it.skillId}" } + "skill:${a.task.skillId}"
        }
        return ReadingLessons.positions.filter { "skill:${ReadingLessons.skill(id, it)}" in exposed }
    }

    // Eight tasks means eight tasks, never eight members of one long phrase.
    internal fun taskWindow(s: LearnerState, view: AdaptiveEvidence.View, scope: String, run: AdaptiveRun): List<AssessmentSample> =
        view.responses.filter { it.task.adaptive?.familyScope == scope && it.task.adaptive.config == run.config && it.ordinal >= run.sinceOrdinal }
            .groupBy { it.taskId }.mapNotNull { (id, members) ->
                val a = s.attempts.first { it.task.id == id }
                members.firstOrNull { !it.correct } ?: members.lastOrNull()?.takeIf {
                    a.completed && (a.task.completion == CompletionKind.SINGLE || members.size == a.task.sequence.size)
                }
            }.sortedBy { it.ordinal }

    fun transition(s: LearnerState, now: Long): LearnerState {
        if (s.regionTraining != null || s.pilot != null) return s
        val task = s.active?.task ?: return s
        val key = task.adaptive?.familyScope ?: return s
        val context = s.familyRuns[key] ?: return s
        var run = context.run
        var anchor = context.anchor
        val view = AdaptiveEvidence.View(s, now)
        val window = taskWindow(s, view, key, run)
        val last = window.lastOrNull()
        val failed = last != null && !last.correct && last.taskId != run.handledFailure
        val overloaded = AdaptiveTraining.overloaded(window)
        if (!run.diagnosing && failed && (overloaded || view.weak(requireNotNull(last).unit))) {
            val focus = window.takeLast(8).filter { !it.correct }.map { it.unit }.distinct()
            anchor = last!!.task.direction
            run = run.copy(generation = run.generation + 1, sinceOrdinal = ordinal(s), diagnosisSince = ordinal(s),
                mixStage = 0, diagnosing = true, trial = false, focus = focus, handledFailure = last.taskId,
                reason = "先巩固这几个目标")
        } else if (run.diagnosing) {
            val fresh = view.samples.filter { it.task.adaptive?.familyScope == key && it.ordinal >= run.diagnosisSince }
            val recovered = run.focus.isNotEmpty() && run.focus.all { unit ->
                val point = s.weakPoints[unit]?.takeIf { it.confirmedAt != null && it.resolvedAt == null }
                if (point == null) view.ready(unit) && fresh.any { it.unit == unit }
                else AdaptiveEvidence.recovered(fresh.filter { it.unit == unit && it.at > requireNotNull(point.confirmedAt) && it.task.adaptive?.purpose in targeted })
            }
            if (recovered) run = run.copy(generation = run.generation + 1, sinceOrdinal = ordinal(s),
                mixStage = 1, diagnosing = false, trial = true, reason = null)
            else if (failed) {
                // Broader errors stay within the taught material of this type, at its supported minimum.
                // No unrelated open-string fallback and no invented extra difficulty level.
                val basics = window.takeLast(8)
                if (basics.size == 8 && basics.count { !it.correct } >= 4 && basics.filter { !it.correct }.map { it.target }.distinct().size >= 3)
                    run = run.copy(focus = (run.focus + basics.filter { !it.correct }.map { it.unit }).distinct(), handledFailure = last!!.taskId)
            }
        } else if (run.trial && window.size >= 8 && window.takeLast(8).count { it.correct } >= 7) {
            run = run.copy(trial = false)
        }
        return s.copy(familyRuns = s.familyRuns + (key to context.copy(anchor = anchor, run = run)))
    }

    internal fun catalog(s: LearnerState, seed: LearningTask, random: Random): List<LearningTask> {
        val id = seed.nodeId
        val source = if (s.practice != null) TaskSource.PRACTICE else if (s.reviewMode) TaskSource.REVIEW else TaskSource.MAIN
        return when {
            id in ReadingLessons.ids -> if (!ReadingLessons.eligible(s, id)) emptyList() else readingPositions(s, id).let { positions ->
                if (id == "tab02") TabMaterial.catalog(s).filter { p -> p.positions.all { it in positions } }.map { TabMaterial.task(it, source) }
                else if (id == "staff") positions.map { ReadingLessons.single(it, source) }
                else if (positions.size < 3) emptyList() else positions.indices.map { start ->
                    ReadingLessons.phrase(id, List(3) { positions[(start + it) % positions.size] }, source)
                }
            }
            id == "tab01" -> if ("tab01:intro" !in s.introductions) emptyList() else TabMaterial.singlePositions(s)
                .filter { TabMaterial.singleTaught(s, it) }.map { TabMaterial.single(it, source) }
            id == "mapping" -> MappingLessons.notes.filter { note ->
                val t = MappingLessons.make(note, seed.direction, source, seed.tonicPitchClass ?: 0)
                "${MappingLessons.family(t)}:intro" in s.introductions
            }.flatMap { note ->
                (if (seed.direction in MappingLessons.fixedDirections) MappingLessons.fixedDirections else MappingLessons.degreeDirections)
                    .map { MappingLessons.make(note, it, source, seed.tonicPitchClass ?: 0) }
            }
            ChordLessons.shapes(id).isNotEmpty() -> ChordLessons.shapes(id).filter { "chord:${it.id}:intro" in s.introductions }
                .map { ChordLessons.make(it, id, source, random) }
            id in StructureLessons.ids -> StructureLessons.tasks(id).filter { "${it.skillId}:intro" in s.introductions }
                .map { FurtherLessons.adaptOwnWork(s, it).copy(source = source) }
            else -> emptyList()
        }
    }

    /** Only target count changes. Keep notation, spelling and played pitches aligned.
     * Whole-score rhythm and creation conditions retain their complete existing task. */
    internal fun reduce(t: LearningTask): List<LearningTask> {
        if (t.completion == CompletionKind.SEQUENCE && t.targetSkillIds.size == t.sequence.size &&
            t.notation?.score == null && t.auditoryScore == null && t.creationDurations.isEmpty() &&
            t.referenceScore == null && t.chordProgression.isEmpty() && t.nodeId != "rework-key") {
            return t.sequence.indices.map { i ->
                val rule = t.sequence[i]
                val relation = t.relation?.let { r -> r.copy(targetPitches = listOf(r.targetPitches[i]),
                    targetSpellings = r.targetSpellings.getOrNull(i)?.let(::listOf).orEmpty()) }
                val notation = t.notation?.let { n -> n.copy(pitches = listOf(n.pitches[i]),
                    coordinates = n.coordinates.getOrNull(i)?.let(::listOf).orEmpty()) }
                t.copy(constraint = rule, sequence = listOf(rule), targetSkillIds = listOf(t.targetSkillIds[i]),
                    relation = relation, notation = notation, explanationTargets = AdaptiveEvidence.targets(t),
                    prompt = when {
                        t.chord != null -> "${t.chord.title} · 本次设置第${rule.coordinate?.string ?: rule.string}弦"
                        t.relation?.ear == true -> "听完参照和目标音，再找到目标音高"
                        t.notation != null -> if (t.notation.kind == NotationKind.TAB) "读这一个 TAB 音" else "读这一个五线谱音"
                        else -> "${t.prompt} · 本次只答原第${i + 1}项"
                    })
            }
        }
        if (t.relation?.ear == true && t.completion == CompletionKind.SINGLE && t.options.size > 2)
            return listOf(t.copy(options = listOf(requireNotNull(t.constraint.symbol), t.options.first { it != t.constraint.symbol })))
        return listOf(t)
    }

    fun next(s: LearnerState, original: LearningTask, random: Random, now: Long): LearningTask {
        if (!supported(original) || s.sessionId == null || s.regionTraining != null || s.pilot != null) return original
        val pending = s.familyRuns.entries.firstOrNull { (_, c) -> c.nodeId == original.nodeId && c.run.diagnosing }
        val seed = if (original.nodeId == "mapping" && pending != null && !original.guided &&
            s.attempts.count { it.task.nodeId == "mapping" && it.ordinal >= pending.value.run.diagnosisSince } % 3 != 2)
            s.attempts.lastOrNull { it.task.adaptive?.familyScope == pending.key }?.task?.copy(source = original.source, introductionId = null) ?: original
        else original
        val scope = scope(s, seed)
        val context = s.familyRuns[scope] ?: FamilyContext(s.sessionId, seed.nodeId, seed.direction)
        val run = context.run
        fun tag(t: LearningTask, purpose: PracticePurpose, unit: String? = AdaptiveEvidence.units(t).firstOrNull()) =
            t.copy(id = newId(), introductionId = if (t.guided) t.introductionId else null,
                adaptive = AdaptiveTask(run.config, purpose, if (purpose == PracticePurpose.RETEST) 1 else run.mixStage, unit = unit, familyScope = scope))
        val full = catalog(s, seed, random)
        if (seed.nodeId in ReadingLessons.ids && !seed.guided && !run.diagnosing && s.practice == null) {
            val known = readingPositions(s, seed.nodeId).map { "skill:${ReadingLessons.skill(seed.nodeId, it)}" }.toSet()
            if (AdaptiveEvidence.targets(seed).any { it !in known }) return tag(seed.copy(source = TaskSource.DEMONSTRATION), PracticePurpose.NEXT)
        }
        if (full.isEmpty()) return tag(seed.copy(source = TaskSource.DEMONSTRATION), PracticePurpose.NEXT)
        if (seed.guided && !run.diagnosing) return tag(seed, PracticePurpose.NEXT)
        if (seed.nodeId == "tab02" && !run.diagnosing) return tag(seed, PracticePurpose.COVERAGE)
        // Use actual completed tasks for cadence, including assisted tasks and small-pool echoes.
        // A filtered evidence sample must never freeze the scheduler on one slot.
        val issued = s.attempts.filter { it.task.adaptive?.familyScope == scope && it.completed &&
            (!run.diagnosing || it.ordinal >= run.diagnosisSince) }
        val originalRetest = run.diagnosing && issued.size % 3 == 2
        val candidates = if (run.mixStage == 0 && !originalRetest) {
            if (seed.nodeId == "mapping") full.filter { it.direction == context.anchor } else full.flatMap(::reduce)
        } else full
        val view = AdaptiveEvidence.View(s, now)
        val completed = AdaptiveTraining.completedScorable(s, view).filter { it.task.adaptive?.familyScope == scope }
        val slot = issued.size % 6
        val block = issued.size / 6
        val keys = full.flatMap { AdaptiveEvidence.units(it) }.toSet()
        val weak = s.weakPoints.values.filter { it.unit in keys && it.resolvedAt == null }
            .sortedWith(compareBy<WeakPoint> { if (it.confirmedAt != null) 0 else 1 }.thenBy { it.observedAt })
        val selectedInBlock = completed.takeLast(slot).filter { it.task.adaptive?.purpose in targeted }
            .mapNotNull { it.task.adaptive?.unit }.distinct()
        val focus = (selectedInBlock + weak.map { it.unit } + run.focus).distinct().take(2)
        var purpose = if (originalRetest) PracticePurpose.RETEST else when (slot) {
            0, 2, 4 -> if (run.diagnosing) PracticePurpose.DIAGNOSIS else PracticePurpose.WEAK
            1, 3 -> PracticePurpose.FAMILIAR
            else -> listOf(PracticePurpose.COVERAGE, PracticePurpose.RETEST, PracticePurpose.NEXT)[block % 3]
        }
        val spaced = candidates.filter { view.eligible(it) }
        val eligible = spaced.ifEmpty { candidates }
        val local = eligible.filter { t -> AdaptiveEvidence.units(t).any { it in focus } }
        val familiar = eligible.filter { t -> AdaptiveEvidence.units(t).all { view.ready(it) } }
        val pool = when (purpose) {
            PracticePurpose.WEAK, PracticePurpose.DIAGNOSIS, PracticePurpose.RETEST -> local.ifEmpty { eligible }
            PracticePurpose.FAMILIAR -> familiar.ifEmpty { eligible.filter { t -> AdaptiveEvidence.units(t).none { key -> weak.any { it.unit == key && it.confirmedAt != null } } }.ifEmpty { eligible } }
            else -> eligible
        }
        if (purpose in targeted && local.isEmpty()) purpose = PracticePurpose.COVERAGE
        val chosen = pool.shuffled(random).minBy { t ->
            val units = AdaptiveEvidence.units(t).map { view.unit(it) }
            val evidence = units.flatten()
            // Small pools still rotate actual exposures; an unscored response must not pin a slot.
            // Compare coverage per member, so long phrases cannot lose every slot to single items.
            if (spaced.isEmpty()) AdaptiveEvidence.targets(t).maxOfOrNull { view.lastExposure(it) ?: 0L } ?: 0L
            else if (purpose in listOf(PracticePurpose.COVERAGE, PracticePurpose.NEXT)) (units.minOfOrNull { it.size } ?: 0).toLong() * AdaptiveEvidence.HOLD_MS + (evidence.maxOfOrNull { it.at } ?: 0L)
            else evidence.maxOfOrNull { it.at } ?: 0L
        }
        val unit = AdaptiveEvidence.units(chosen).firstOrNull { it in focus } ?: AdaptiveEvidence.units(chosen).firstOrNull()
        val point = s.weakPoints[unit]
        val teaching = purpose != PracticePurpose.RETEST && purpose in targeted && point?.confirmedAt != null && point.resolvedAt == null && point.taughtAt == null
        return tag(chosen.copy(options = chosen.options.shuffled(random), source = if (teaching) TaskSource.DEMONSTRATION else chosen.source), purpose, unit)
    }
}
