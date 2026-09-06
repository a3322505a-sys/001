package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.core.MusicFacts
import kotlin.random.Random

object AdaptiveMix {
    private fun mappingKeys(note: String, representation: AnswerRepresentation): List<String> = when (representation) {
        AnswerRepresentation.FIXED -> MappingLessons.fixedDirections.map { "mapping:fixed:$note:${it.name}" }
        AnswerRepresentation.DEGREE -> MappingLessons.degreeDirections.map { "mapping:major:0:$note:${it.name}" }
        else -> emptyList()
    }
    private fun introduced(s: LearnerState, note: String, representation: AnswerRepresentation) = when (representation) {
        AnswerRepresentation.FIXED -> "mapping:fixed:$note:intro" in s.introductions
        AnswerRepresentation.DEGREE -> "mapping:major:0:$note:intro" in s.introductions
        else -> true
    }
    private fun ready(s: LearnerState, view: AdaptiveEvidence.View, note: String, representation: AnswerRepresentation) =
        introduced(s, note, representation) && mappingKeys(note, representation).all { key -> view.ready(key) && !view.weak(key) }
    private fun notes(s: LearnerState, view: AdaptiveEvidence.View): List<String> = s.regionTraining?.let { region ->
        RegionTraining.known(s, region.regionId).filter { view.ready(AdaptiveEvidence.positionUnit(it.second, Direction.POSITION_TO_NOTE)) }
            .map { MusicFacts.note(it.second.string, it.second.fret) }.distinct()
    } ?: emptyList()
    fun transition(s: LearnerState, now: Long): LearnerState {
        val region = s.regionTraining ?: return s
        var run = region.adaptive
        if (run.diagnosing || run.trial || s.active?.task?.adaptive?.config != run.config || !Curriculum.available(s, Curriculum.node("mapping"))) return s
        val view = AdaptiveEvidence.View(s, now)
        val notes = notes(s, view)
        val ordinal = (s.attempts.maxOfOrNull { it.ordinal } ?: 0) + 1
        val sessionTasks = s.attempts.filter { it.sessionId == s.sessionId }.map { it.task.id }.toSet()
        val mixed = view.samples.filter { it.taskId in sessionTasks && it.ordinal >= run.sinceOrdinal && it.task.adaptive?.config == run.config && it.task.adaptive.options.isNotEmpty() }
        fun stable(values: List<AssessmentSample>) = values.takeLast(8).let { it.size == 8 && it.count { a -> a.correct } >= 7 }
        val fixed = notes.filter { ready(s, view, it, AnswerRepresentation.FIXED) }
        val degrees = fixed.filter { ready(s, view, it, AnswerRepresentation.DEGREE) }
        val related = run.focus.mapNotNull { AdaptiveTraining.mappingTask(it)?.mappingNote }.distinct().ifEmpty { notes }
        val reinstated = run.excluded.firstOrNull { representation -> related.isNotEmpty() && notes.containsAll(related) && related.all { ready(s, view, it, representation) } }
        if (reinstated != null) run = run.copy(excluded = run.excluded - reinstated, generation = run.generation + 1, sinceOrdinal = ordinal,
            representationTrials = run.representationTrials + (reinstated to ordinal))
        else if (run.representationTrials.isNotEmpty()) {
            run = run.copy(representationTrials = run.representationTrials.filterNot { (rep, since) -> stable(mixed.filter { it.ordinal >= since && it.task.adaptive?.correctRepresentation == rep }) })
        } else if (run.excluded.isEmpty()) {
            val stage = when {
                run.mixStage == 0 && fixed.size >= 2 -> 1
                run.mixStage == 1 && stable(mixed) && degrees.size >= 2 -> 2
                run.mixStage == 2 && degrees.size >= 2 && s.weakPoints.values.none { it.resolvedAt == null && it.confirmedAt != null && (it.unit in run.focus || it.unit.startsWith("mapping:") && AdaptiveTraining.mappingTask(it.unit)?.mappingNote in degrees) } && AnswerRepresentation.entries.all { rep -> stable(mixed.filter { it.task.adaptive?.correctRepresentation == rep }) } -> 3
                else -> run.mixStage
            }
            if (stage != run.mixStage) run = run.copy(mixStage = stage, generation = run.generation + 1, sinceOrdinal = ordinal)
        }
        return s.copy(regionTraining = region.copy(adaptive = run))
    }
    fun apply(s: LearnerState, t: LearningTask, random: Random, now: Long): LearningTask {
        val region = s.regionTraining ?: return t
        val run = region.adaptive
        if (t.guided || t.regionProbe || run.diagnosing || run.trial) return t
        val view = AdaptiveEvidence.View(s, now)
        val known = notes(s, view)
        // Use the reserved sixth task to teach and test conversion before it can enter options.
        val currentCount = AdaptiveTraining.completedScorable(s, view).size
        if (currentCount % 6 == 5 && Curriculum.available(s, Curriculum.node("mapping")) && known.size >= 2) {
            val representation = if (run.mixStage == 0) AnswerRepresentation.FIXED else AnswerRepresentation.DEGREE
            val candidateKeys = known.flatMap { mappingKeys(it, representation) }.filterNot { view.ready(it) || s.weakPoints[it]?.let { point -> point.confirmedAt != null && point.resolvedAt == null } == true }
            candidateKeys.sortedBy { view.unit(it).lastOrNull()?.at ?: 0L }.mapNotNull { AdaptiveTraining.mappingTask(it) }
                .firstOrNull { view.eligible(it) }?.let { conversion ->
                    val note = requireNotNull(conversion.mappingNote)
                    val intro = if (representation == AnswerRepresentation.FIXED) "mapping:fixed:$note:intro" else "mapping:major:0:$note:intro"
                    val task = if (introduced(s, note, representation)) conversion else conversion.copy(source = TaskSource.DEMONSTRATION, introductionId = intro)
                    return task.copy(adaptive = AdaptiveTask(run.config, PracticePurpose.MAPPING, run.mixStage, unit = AdaptiveEvidence.unit(conversion)))
                }
        }
        if (run.mixStage == 0 || t.direction != Direction.POSITION_TO_NOTE || t.coordinate == null || t.adaptive?.purpose in listOf(PracticePurpose.WEAK, PracticePurpose.DIAGNOSIS, PracticePurpose.RETEST)) return t
        if (!view.ready(AdaptiveEvidence.positionUnit(t.coordinate, t.direction))) return t
        val weights = when (run.mixStage) { 1 -> listOf(70, 30, 0); 2 -> listOf(50, 30, 20); else -> listOf(40, 30, 30) }
        val reps = AnswerRepresentation.entries.filter { weights[it.ordinal] > 0 && it !in run.excluded }
        if (reps.size < 2) return t
        val available = t.options.filter { it in known && reps.all { rep -> ready(s, view, it, rep) } }.distinct()
        val correctNote = MusicFacts.note(t.coordinate.string, t.coordinate.fret)
        if (correctNote !in available || available.size < 2) return t
        var candidates = available.shuffled(random).take(7)
        if (correctNote !in candidates) candidates = (candidates.dropLast(1) + correctNote)
        if (run.mixStage == 3 && random.nextDouble() < 0.2) {
            val confusion = s.attempts.asReversed().filter { it.at >= now - 30L * 24 * 60 * 60 * 1000 && it.task.coordinate?.let { c -> MusicFacts.note(c.string, c.fret) } == correctNote }
                .firstNotNullOfOrNull { attempt ->
                    val input = AdaptiveEvidence.firstInput(attempt)?.takeIf { it.result == ClickResult.WRONG }
                    val pc = attempt.task.adaptive?.options?.firstOrNull { it.label == input?.symbol }?.pitchClass
                        ?: input?.symbol?.let { MusicFacts.noteNames.indexOf(it).takeIf { index -> index >= 0 } }
                    pc?.let { MusicFacts.noteNames[it] }?.takeIf { it in available && it != correctNote }
                }
            if (confusion != null && confusion !in candidates) candidates = candidates.filter { it != correctNote }.dropLast(1) + listOf(correctNote, confusion)
        }
        val total = reps.sumOf { weights[it.ordinal] }
        fun draw(): AnswerRepresentation {
            var roll = random.nextInt(total)
            return reps.first { rep -> roll -= weights[rep.ordinal]; roll < 0 }
        }
        repeat(24) {
            val options = candidates.map { note ->
                val rep = draw()
                val pc = MusicFacts.noteNames.indexOf(note)
                SemanticOption(when (rep) {
                    AnswerRepresentation.NOTE -> note
                    AnswerRepresentation.FIXED -> MusicFacts.fixedSolfege.getValue(note)
                    AnswerRepresentation.DEGREE -> requireNotNull(MusicFacts.majorDegree(pc, 0)).toString()
                }, pc, rep)
            }.shuffled(random)
            if (options.map { it.representation }.distinct().size >= 2 && options.map { it.label }.distinct().size == options.size) {
                val answer = options.single { it.pitchClass == MusicFacts.noteNames.indexOf(correctNote) }
                return t.copy(options = options.map { it.label }, constraint = AnswerConstraint(ConstraintKind.SYMBOL, symbol = answer.label),
                    tonicPitchClass = if (AnswerRepresentation.DEGREE in reps) 0 else null, tonalMode = if (AnswerRepresentation.DEGREE in reps) "major" else null,
                    prompt = if (AnswerRepresentation.DEGREE in reps) "C 大调" else t.prompt,
                    adaptive = requireNotNull(t.adaptive).copy(stage = run.mixStage, options = options, correctRepresentation = answer.representation))
            }
        }
        return t
    }
}
