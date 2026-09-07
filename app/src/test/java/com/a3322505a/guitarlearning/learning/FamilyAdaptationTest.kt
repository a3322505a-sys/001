package com.a3322505a.guitarlearning.learning

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class FamilyAdaptationTest {
    private val scheduler = LessonScheduler(Random(12))
    private val co = LearningCoordinator(scheduler)
    private val gap = AdaptiveEvidence.HOLD_MS + 100
    private fun profile(node: String) = LearnerState(currentNode = node, sessionId = "s", sessions = listOf(LearningSession("s", 1)), reviewMode = true,
        progress = Curriculum.nodes.associate { it.id to NodeProgress(1) },
        introductions = Curriculum.nodes.flatMap { it.positions }.map { "position:${it.id}" }.toSet() + ReadingLessons.ids.map { "reading:$it:intro" }.toSet() + setOf("tab01:intro", "reading:staff:clef", "reading:staff:octave") +
            ChordShapes.all.map { "chord:${it.id}:intro" } + StructureLessons.ids.flatMap { StructureLessons.tasks(it).map { t -> "${t.skillId}:intro" } } +
            MappingLessons.notes.flatMap { listOf("mapping:fixed:$it:intro", "mapping:major:0:$it:intro") })
    private fun present(s: LearnerState, t: LearningTask, at: Long, purpose: PracticePurpose = PracticePurpose.DIAGNOSIS): LearnerState {
        val scope = FamilyAdaptation.scope(s, t)
        val run = s.familyRuns[scope]?.run ?: AdaptiveRun(mixStage = 1)
        return AdaptiveEvidence.present(s, t.copy(id = newId(), source = TaskSource.MAIN,
            adaptive = AdaptiveTask(run.config, purpose, run.mixStage, unit = AdaptiveEvidence.units(t).first(), familyScope = scope)), at)
    }
    private fun respond(s: LearnerState, good: Boolean, now: Long): LearnerState {
        val t = s.active!!.task
        val index = s.active!!.sequenceIndex
        val rule = if (t.completion == CompletionKind.SEQUENCE) t.sequence[index] else t.constraint
        return if (rule.kind == ConstraintKind.SYMBOL) co.answer(s, symbol = if (good) rule.symbol else t.options.firstOrNull { it != rule.symbol } ?: "错误", now = now)
        else co.answer(s, coordinate = t.range.positions().first { AnswerEvaluator.matches(it, rule) == good }, now = now)
    }
    private fun finish(s: LearnerState, t: LearningTask, good: Boolean, at: Long): LearnerState {
        var state = present(s, t, at)
        if (t.relation?.ear == true) state = co.playbackCompleted(state, state.active!!.task.id)
        var time = at + 1
        if (!good) state = respond(state, false, time++)
        while (state.active!!.phase !in listOf(Phase.CORRECT, Phase.CORRECTED)) state = respond(state, true, time++)
        return state
    }
    private fun cases(): List<LearningTask> = listOf(
        ReadingLessons.phrase("tab02", ReadingLessons.positions.take(3), TaskSource.MAIN),
        ReadingLessons.single(ReadingLessons.positions.first(), TaskSource.MAIN),
        ReadingLessons.phrase("staff02", ReadingLessons.positions.take(3), TaskSource.MAIN),
        ChordLessons.make(ChordShapes.am, "chord-am", TaskSource.MAIN, Random(4)),
        StructureLessons.tasks("intervals").first(),
        StructureLessons.tasks("scale-major").first { it.completion == CompletionKind.SEQUENCE },
        StructureLessons.tasks("ear-intervals").first(),
        StructureLessons.tasks("ear-memory").first { it.completion == CompletionKind.SEQUENCE },
        MappingLessons.make("C", Direction.NOTE_TO_SOLFEGE, TaskSource.MAIN),
        MappingLessons.make("C", Direction.NOTE_TO_DEGREE, TaskSource.MAIN),
    )

    @Test fun eachFamilyDiagnosesTeachesRecoversAndCanFallBackAgain() {
        for (task in cases()) {
            var s = finish(profile(task.nodeId), task, false, gap)
            assertFalse(s.familyRuns.values.single().run.diagnosing)
            s = finish(s, task, false, gap * 2)
            assertTrue(task.nodeId, s.familyRuns.values.single().run.diagnosing)
            val next = scheduler.next(s.copy(active = null), gap * 3)
            assertEquals(task.nodeId, next.nodeId)
            assertTrue(task.nodeId, next.guided)
            assertEquals(0, next.adaptive!!.stage)
            if (task.completion == CompletionKind.SEQUENCE) assertEquals(1, next.sequence.size)
            if (task.relation?.ear == true && task.completion == CompletionKind.SINGLE) assertEquals(2, next.options.size)
            assertEquals(task.tonicPitchClass, next.tonicPitchClass)
            val scope = FamilyAdaptation.scope(s, task)
            val focus = s.familyRuns.getValue(scope).run.focus.first()
            val reduced = FamilyAdaptation.reduce(task).first { focus in AdaptiveEvidence.units(it) }
            repeat(5) { i -> s = finish(s, reduced, true, gap * (i + 4)) }
            assertTrue(task.nodeId, s.familyRuns.getValue(scope).run.diagnosing)
            s = finish(s, reduced, true, gap * 9)
            assertTrue(task.nodeId, s.familyRuns.getValue(scope).run.trial)
            assertEquals(1, s.familyRuns.getValue(scope).run.mixStage)
            s = finish(s, task, false, gap * 10)
            s = finish(s, task, false, gap * 11)
            assertTrue(task.nodeId, s.familyRuns.getValue(scope).run.diagnosing)
            assertNull(s.regionTraining)
        }
    }

    @Test fun onlyAttemptedMemberIsWrongAndLaterRevealedMembersAreNotIndependent() {
        val task = ReadingLessons.phrase("tab02", ReadingLessons.positions.take(3), TaskSource.MAIN)
        var s = present(profile("tab02"), task, 10)
        s = respond(s, true, 20)
        s = respond(s, false, 30)
        assertEquals(listOf(true, false), s.attempts.single().members.map { it.firstCorrect })
        assertEquals(listOf(true, false), AdaptiveEvidence.View(s, 31).samples.map { it.correct })
        assertEquals(1, s.weakPoints.size)
        s = respond(s, true, 40)
        s = respond(s, true, 50)
        assertEquals(listOf(true, false, true), s.attempts.single().members.map { it.firstCorrect })
        assertEquals(listOf(true, true, false), s.attempts.single().members.map { it.firstUnassisted })
        assertEquals(listOf(true, false), AdaptiveEvidence.View(s, 51).samples.map { it.correct })
        assertEquals(listOf(true, false), AdaptiveEvidence.View(s, 51).responses.map { it.correct })
        assertEquals(s, LearningCodec.decode(LearningCodec.encode(s)))
    }

    @Test fun aLongSequenceIsOneConfigurationTaskAndOneQuotaCompletion() {
        val task = ReadingLessons.phrase("tab02", ReadingLessons.positions, TaskSource.MAIN)
        val s = finish(profile("tab02"), task, true, gap)
        val view = AdaptiveEvidence.View(s, gap + 20)
        val scope = FamilyAdaptation.scope(s, task)
        assertEquals(6, view.samples.size)
        assertEquals(1, FamilyAdaptation.taskWindow(s, view, scope, s.familyRuns.getValue(scope).run).size)
        assertEquals(1, AdaptiveTraining.completedScorable(s, view).size)
        val partial = present(s, task, gap * 2).let { respond(it, true, gap * 2 + 1) }
        assertEquals(1, FamilyAdaptation.taskWindow(partial, AdaptiveEvidence.View(partial, gap * 2 + 2), scope, partial.familyRuns.getValue(scope).run).size)
    }

    @Test fun memberSpacingRequiresDifferentTargetsAndExplanationRevealsOriginalPhrase() {
        val task = ReadingLessons.phrase("tab02", ReadingLessons.positions.take(3), TaskSource.MAIN)
        val reduced = FamilyAdaptation.reduce(task)
        var s = finish(profile("tab02"), reduced[0], true, 10)
        s = finish(s, reduced[0], true, 20)
        assertEquals(1, AdaptiveEvidence.View(s, 25).samples.size)
        s = finish(s, reduced[1], true, 30)
        s = finish(s, reduced[2], true, 40)
        s = finish(s, reduced[0], true, 50)
        assertEquals(2, AdaptiveEvidence.View(s, 60).unit(AdaptiveEvidence.units(reduced[0]).single()).size)
        s = co.hint(present(s, reduced[0], 70), 71)
        assertTrue(AdaptiveEvidence.targets(task).all { AdaptiveEvidence.View(s, 72).lastExposure(it) == 71L })
    }

    @Test fun audioCompletionGateAndReferenceSurviveReduction() {
        for (task in listOf(StructureLessons.tasks("ear-intervals").first(), StructureLessons.tasks("ear-memory").first())) {
            val reduced = FamilyAdaptation.reduce(task).first()
            assertEquals(task.relation!!.referencePitches, reduced.relation!!.referencePitches)
            var s = present(profile(task.nodeId), reduced, 10)
            assertEquals(s, respond(s, false, 20))
            s = co.playbackStarted(s, s.active!!.task.id, 25)
            assertEquals(s, respond(s, false, 30))
            s = co.playbackCompleted(s, s.active!!.task.id)
            s = respond(s, false, 40)
            assertEquals(listOf(false), AdaptiveEvidence.View(s, 41).samples.map { it.correct })
            assertEquals(AudioPurpose.EAR, TaskAudioPolicy.prompt(ActiveTask(reduced))!!.purpose)
        }
    }

    @Test fun originalAudioRhythmAndCreationConditionsAreNeverTruncated() {
        val tasks = listOf("ear-memory", "pulse-basics", "eighth-basics", "compose-eight", "rework-key", "progressions").flatMap(StructureLessons::tasks)
        tasks.filter { it.auditoryScore != null || it.notation?.score != null || it.creationDurations.isNotEmpty() || it.nodeId == "rework-key" || it.chordProgression.isNotEmpty() }.forEach { t ->
            val r = FamilyAdaptation.reduce(t).single()
            assertEquals(t.sequence, r.sequence)
            assertEquals(t.auditoryScore, r.auditoryScore)
            assertEquals(t.notation, r.notation)
            assertEquals(t.creationDurations, r.creationDurations)
            assertEquals(t.chordProgression, r.chordProgression)
        }
    }

    @Test fun taughtCatalogVariantsRemainValidAndDoNotInventPrerequisiteFacts() {
        val tasks = StructureLessons.ids.flatMap(StructureLessons::tasks) + cases()
        for (t in tasks) for (r in FamilyAdaptation.reduce(t)) {
            val state = present(profile(t.nodeId), r, gap)
            assertEquals(state, LearningCodec.decode(LearningCodec.encode(state)))
            assertTrue(AdaptiveEvidence.units(r).all { it in AdaptiveEvidence.units(t) })
            assertEquals(t.relation?.referencePitches, r.relation?.referencePitches)
            assertEquals(t.range, r.range)
        }
        val task = StructureLessons.tasks("intervals").first()
        val noKnowledge = profile(task.nodeId).copy(introductions = emptySet())
        assertTrue(FamilyAdaptation.catalog(noKnowledge, task, Random(1)).isEmpty())
        val onlyOne = noKnowledge.copy(introductions = setOf("${task.skillId}:intro"))
        assertEquals(setOf(task.skillId), FamilyAdaptation.catalog(onlyOne, task, Random(1)).map { it.skillId }.toSet())
    }

    @Test fun mappingDirectionIsIsolatedFromRepresentationKeyAndPositionEvidence() {
        val task = MappingLessons.make("C", Direction.DEGREE_TO_NOTE, TaskSource.MAIN)
        var s = finish(profile("mapping"), task, false, gap)
        s = finish(s, task, false, gap * 2)
        repeat(4) {
            val next = scheduler.next(s.copy(active = null), gap * 3)
            assertEquals(Direction.DEGREE_TO_NOTE, next.direction)
            assertEquals(0, next.tonicPitchClass)
            assertEquals("major", next.tonalMode)
        }
        assertTrue(s.weakPoints.keys.all { it.startsWith("mapping:major:0:") })
        assertEquals(0, AdaptiveEvidence.View(s, gap * 3).region(FretboardRegion.LOW).measured)
    }

    @Test fun pausePracticeRegionAndReloadKeepTheExactTaskAndContext() {
        val task = cases().first()
        var s = finish(profile(task.nodeId), task, false, gap)
        s = finish(s, task, false, gap * 2)
        s = AdaptiveEvidence.present(s.copy(active = null), scheduler.next(s.copy(active = null), gap * 3), gap * 3)
        val active = s.active
        val contexts = s.familyRuns
        val practice = co.startPractice(s, PracticePlan(listOf("staff02"), PracticeKind.READING), gap * 4)
        val resumed = co.end(LearningCodec.decode(LearningCodec.encode(practice)), gap * 5)
        assertEquals(active, resumed.active)
        assertEquals(contexts, resumed.familyRuns.filterKeys { it in contexts })
        val region = co.startRegion(resumed, "LOW", gap * 6)
        val returned = RegionSessions.leave(LearningCodec.decode(LearningCodec.encode(region)))
        assertEquals(active, returned.active)
        assertEquals(contexts, returned.familyRuns.filterKeys { it in contexts })
    }

    @Test fun legacyMemberConditionsStayUnknownAndCannotBecomeFreshEvidence() {
        val t = cases().first().copy(evidenceVersion = 1)
        val m = TargetEvidence(0, t.targetSkillIds[0], t.direction, t.sequence[0].coordinate, false, true, 10, false)
        val a = Attempt(t, "s", 1, 10, "2026-09-07", false, 0, false, false,
            listOf(InputRecord(10, coordinate = Coordinate(1, 2), result = ClickResult.WRONG, targetIndex = 0)), false, members = listOf(m))
        val state = profile(t.nodeId).copy(attempts = listOf(a))
        val json = LearningCodec.json.parseToJsonElement(LearningCodec.encode(state)).jsonObject
        val legacy = JsonObject(json - "familyRuns").toString()
        val restored = LearningCodec.decode(legacy)
        assertEquals(state, restored)
        assertTrue(AdaptiveEvidence.View(restored, 20).samples.isEmpty())
        assertTrue(AdaptiveEvidence.View(restored, 20).responses.isEmpty())
        assertNull(restored.attempts.single().members.single().firstUnassisted)
    }

    @Test fun regionDowngradeAndPausedFamilyRecoverySurviveOneProfileRoundTrip() {
        val task = cases().first()
        var family = finish(profile(task.nodeId), task, false, gap)
        family = finish(family, task, false, gap * 2)
        family = AdaptiveEvidence.present(family.copy(active = null), scheduler.next(family.copy(active = null), gap * 3), gap * 3)
        val familyTask = family.active
        val familyRuns = family.familyRuns
        val members = family.attempts.flatMap { it.members }
        var region = co.startRegion(family, "LOW", gap * 4)
        val position = scheduler.makePosition("p01", Coordinate(1, 0), Direction.POSITION_TO_NOTE, TaskSource.MAIN)
        repeat(2) { index ->
            val at = gap * 4 + index * 10 + 1
            region = AdaptiveEvidence.present(region, position.copy(id = newId(), adaptive = AdaptiveTask(
                region.regionTraining!!.adaptive.config, PracticePurpose.NORMAL, unit = AdaptiveEvidence.unit(position))), at)
            region = co.answer(region, symbol = "F", now = at + 1)
            region = co.answer(region, symbol = "E", now = at + 2)
        }
        assertTrue(region.regionTraining!!.adaptive.scaffolding)
        region = co.next(region, region.active!!.task.id, gap * 4 + 30)
        assertTrue(region.active!!.task.adaptive!!.scaffolded)
        val regionTask = region.active
        val regionRun = region.regionTraining
        val returned = co.start(LearningCodec.decode(LearningCodec.encode(region)), task.nodeId, gap * 4 + 40)
        assertEquals(familyTask, returned.active)
        assertEquals(familyRuns, returned.familyRuns)
        assertEquals(members, returned.attempts.flatMap { it.members })
        val reentered = co.startRegion(LearningCodec.decode(LearningCodec.encode(returned)), "LOW", gap * 4 + 50)
        assertEquals(regionTask, reentered.active)
        assertEquals(regionRun, reentered.regionTraining)
        assertEquals(familyRuns, reentered.familyRuns)
    }

    @Test fun eitherPreIntegrationFormatRestoresItsOwnAdaptiveState() {
        fun without(element: JsonElement, keys: Set<String>): JsonElement = when (element) {
            is JsonObject -> JsonObject(element.filterKeys { it !in keys }.mapValues { without(it.value, keys) })
            is JsonArray -> JsonArray(element.map { without(it, keys) })
            else -> element
        }
        val task = cases().first()
        val v36 = finish(profile(task.nodeId), task, false, gap)
        val old36 = without(LearningCodec.json.parseToJsonElement(LearningCodec.encode(v36)), setOf("scaffolded", "scaffolding"))
        assertEquals(v36, LearningCodec.decode(old36.toString()))

        val position = scheduler.makePosition("p01", Coordinate(1, 0), Direction.POSITION_TO_NOTE, TaskSource.MAIN)
            .copy(options = listOf("E", "F"), adaptive = AdaptiveTask(AdaptiveRun(scaffolding = true).config,
                PracticePurpose.DIAGNOSIS, scaffolded = true))
        val v37 = profile("p01").copy(active = ActiveTask(position), regionTraining = RegionRun("LOW", 1, 0,
            adaptive = AdaptiveRun(diagnosing = true, scaffolding = true)))
        val old37 = without(LearningCodec.json.parseToJsonElement(LearningCodec.encode(v37)), setOf("familyRuns", "familyScope", "explanationTargets"))
        assertEquals(v37, LearningCodec.decode(old37.toString()))
        assertTrue(LearningCodec.decode(old37.toString()).active!!.task.adaptive!!.scaffolded)
    }

    @Test fun readingDoesNotTreatOneIntroPhraseAsTeachingAllSixNotes() {
        val task = ReadingLessons.phrase("tab02", ReadingLessons.positions.take(3), TaskSource.DEMONSTRATION)
        var s = profile("tab02").copy(progress = emptyMap())
        s = AdaptiveEvidence.expose(s, task, 10, true)
        assertEquals(ReadingLessons.positions.take(3), FamilyAdaptation.readingPositions(s, "tab02"))
        val newPhrase = ReadingLessons.phrase("tab02", ReadingLessons.positions.takeLast(3), TaskSource.MAIN)
        val next = FamilyAdaptation.next(s, newPhrase, Random(1), 20)
        assertTrue(next.guided)
        assertEquals(newPhrase.sequence, next.sequence)
    }
}
