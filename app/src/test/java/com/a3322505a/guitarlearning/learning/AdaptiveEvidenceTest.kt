package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.core.MusicFacts
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class AdaptiveEvidenceTest {
    private val coordinator = LearningCoordinator(LessonScheduler(Random(42)))
    private fun profile() = LearnerState(currentNode = "p01", sessionId = "s", sessions = listOf(LearningSession("s", 1)),
        progress = Curriculum.nodes.associate { it.id to NodeProgress(1) },
        introductions = Curriculum.nodes.flatMap { it.positions }.map { "position:${it.id}" }.toSet() +
            MappingLessons.notes.flatMap { listOf("mapping:fixed:$it:intro", "mapping:major:0:$it:intro") })
    private fun position(c: Coordinate, direction: Direction) = LessonScheduler(Random(1)).makePosition(
        Curriculum.nodes.first { c in it.positions }.id, c, direction, TaskSource.MAIN)
    private fun answer(s: LearnerState, task: LearningTask, good: Boolean, at: Long): LearnerState {
        val t = task.copy(id = "answer-${s.attempts.size}", evidenceVersion = AdaptiveEvidence.VERSION)
        val started = s.copy(active = ActiveTask(t))
        val result = if (t.constraint.kind == ConstraintKind.SYMBOL) coordinator.answer(started,
            symbol = if (good) t.constraint.symbol else t.options.first { it != t.constraint.symbol }, now = at)
        else coordinator.answer(started, coordinate = t.range.positions().first { AnswerEvaluator.matches(it, t.constraint) == good }, now = at)
        if (good) return result
        return if (t.constraint.kind == ConstraintKind.SYMBOL) coordinator.answer(result, symbol = t.constraint.symbol, now = at + 1)
            else coordinator.answer(result, coordinate = AnswerEvaluator.validPositions(t).first(), now = at + 1)
    }
    @Test fun regionScoresUseDistinctTargetsBothDirectionsAndSeparateHistory() {
        for ((region, total) in listOf(FretboardRegion.LOW to 18, FretboardRegion.MIDDLE to 16, FretboardRegion.FULL to 48)) {
            var s = profile()
            var now = 1000L
            val positions = AdaptiveEvidence.targets(region)
            assertEquals(total, positions.size)
            repeat(4) { for (direction in AdaptiveEvidence.positionDirections) for (c in positions) {
                s = answer(s, position(c, direction), true, now); now += 100
            } }
            val withoutHold = AdaptiveEvidence.View(s, now).region(region)
            assertEquals(total, withoutHold.measured)
            assertEquals(80.0, withoutHold.raw, 0.0001)
            now += AdaptiveEvidence.HOLD_MS
            for (direction in AdaptiveEvidence.positionDirections) for (c in positions) {
                s = answer(s, position(c, direction), true, now); now += 100
            }
            assertEquals(100.0, AdaptiveEvidence.View(s, now).region(region).raw, 0.0001)
            assertEquals("音位熟练度 · 待复测", AdaptiveEvidence.View(s, now + AdaptiveEvidence.WINDOW_MS + 1).region(region).label)
            assertEquals(profile().progress.keys, s.progress.keys)
        }
    }
    @Test fun oneDirectionAndFiveOfEighteenAreNotWholeRegionMastery() {
        var s = profile(); var now = 1000L
        repeat(4) { for (direction in AdaptiveEvidence.positionDirections) for (c in AdaptiveEvidence.targets(FretboardRegion.LOW).take(5)) {
            s = answer(s, position(c, direction), true, now); now += 100
        } }
        assertEquals("音位熟练度 · 评估中", AdaptiveEvidence.View(s, now).region(FretboardRegion.LOW).label)
        val onlyOneDirection = s.copy(attempts = s.attempts.filter { it.task.direction == Direction.POSITION_TO_NOTE })
        assertEquals(0, AdaptiveEvidence.View(onlyOneDirection, now).region(FretboardRegion.LOW).measured)
    }
    @Test fun recentEightFailuresReplaceOneHundredOldSuccesses() {
        val c = Coordinate(1, 0); val task = position(c, Direction.POSITION_TO_NOTE)
        var s = profile(); var now = 1000L
        repeat(108) { i -> s = answer(s, task, i < 100, now); now += AdaptiveEvidence.HOLD_MS + 2 }
        val window = AdaptiveEvidence.View(s, now).unit(AdaptiveEvidence.positionUnit(c, task.direction))
        assertEquals(8, window.size); assertTrue(window.none { it.correct })
    }
    @Test fun exposureCannotBeEvadedByDirectionChangeAndNeedsTwoOtherTargets() {
        val c = Coordinate(1, 0)
        var s = answer(profile(), position(c, Direction.POSITION_TO_NOTE), true, 100)
        s = answer(s, position(c, Direction.NOTE_TO_POSITION), true, 200)
        assertEquals(1, AdaptiveEvidence.View(s, 201).samples.size)
        s = answer(s, position(Coordinate(1, 1), Direction.POSITION_TO_NOTE), true, 300)
        s = answer(s, position(Coordinate(1, 3), Direction.POSITION_TO_NOTE), true, 400)
        s = answer(s, position(c, Direction.NOTE_TO_POSITION), true, 500)
        assertEquals(1, AdaptiveEvidence.View(s, 501).unit(AdaptiveEvidence.positionUnit(c, Direction.NOTE_TO_POSITION)).size)
        assertFalse(AdaptiveEvidence.View(s, 501).held(AdaptiveEvidence.positionUnit(c, Direction.NOTE_TO_POSITION)))
    }
    @Test fun assistanceAndCorrectionNeverEraseOrManufactureFirstAnswerEvidence() {
        val task = position(Coordinate(1, 0), Direction.POSITION_TO_NOTE).copy(evidenceVersion = 1)
        var s = profile().copy(active = ActiveTask(task))
        s = coordinator.hint(s, 100)
        s = coordinator.answer(s, symbol = task.constraint.symbol, now = 200)
        assertTrue(AdaptiveEvidence.View(s, 201).samples.isEmpty())
        var wrong = answer(profile(), task, false, 300)
        assertEquals(listOf(false), AdaptiveEvidence.View(wrong, 302).samples.map { it.correct })
        wrong = coordinator.answer(wrong, symbol = task.constraint.symbol, now = 400)
        assertEquals(1, AdaptiveEvidence.View(wrong, 401).samples.size)
        val old = wrong.copy(attempts = wrong.attempts.map { it.copy(task = it.task.copy(evidenceVersion = 0), firstUnassisted = null) })
        assertTrue(AdaptiveEvidence.View(old, 401).samples.isEmpty())
        assertEquals(wrong.attempts.size, old.attempts.size)
    }
    @Test fun cumulativeFiveCorrectDoesNotResolveWeaknessButSpacedRecoveryDoes() {
        val c = Coordinate(1, 0); val key = AdaptiveEvidence.positionUnit(c, Direction.POSITION_TO_NOTE)
        val t = position(c, Direction.POSITION_TO_NOTE).copy(adaptive = AdaptiveTask("fixture", PracticePurpose.WEAK, unit = key))
        var s = profile(); var now = 1000L
        repeat(15) { i -> s = answer(s, t, i % 3 == 2, now); now += AdaptiveEvidence.HOLD_MS + 2 }
        assertNotNull(s.weakPoints[key]?.confirmedAt); assertNull(s.weakPoints[key]?.resolvedAt)
        repeat(6) { s = answer(s, t, true, now); now += AdaptiveEvidence.HOLD_MS + 2 }
        assertNotNull(s.weakPoints[key]?.resolvedAt)
        repeat(2) { s = answer(s, t, false, now); now += AdaptiveEvidence.HOLD_MS + 2 }
        assertNull(s.weakPoints[key]?.resolvedAt)
        assertEquals(s, LearningCodec.decode(LearningCodec.encode(s)))
    }
    @Test fun mixedOptionsAreUniqueSemanticAnswersAndDoNotDoubleCreditFoundations() {
        var s = profile(); var now = 1000L
        val coordinates = listOf(Coordinate(1, 0), Coordinate(1, 1), Coordinate(1, 3))
        repeat(4) {
            for (c in coordinates) { s = answer(s, position(c, Direction.POSITION_TO_NOTE), true, now); now += 100 }
            for (direction in MappingLessons.directions) for (note in listOf("E", "F", "G")) {
                s = answer(s, MappingLessons.make(note, direction, TaskSource.MAIN), true, now); now += 100
            }
        }
        s = s.copy(regionTraining = RegionRun("LOW", 1, 0, AdaptiveRun(mixStage = 3)))
        val base = LessonScheduler().makePosition("p03", coordinates.first(), Direction.POSITION_TO_NOTE, TaskSource.MAIN)
            .copy(adaptive = AdaptiveTask(s.regionTraining!!.adaptive.config, PracticePurpose.NORMAL, 3))
        now += AdaptiveEvidence.HOLD_MS
        val types = mutableSetOf<AnswerRepresentation>()
        repeat(50) { seed ->
            val mixed = AdaptiveMix.apply(s, base, Random(seed), now)
            val options = requireNotNull(mixed.adaptive).options
            assertTrue(options.size >= 2)
            assertEquals(options.size, options.map { it.pitchClass }.distinct().size)
            assertTrue(options.map { it.representation }.distinct().size >= 2)
            val correct = options.filter { AnswerEvaluator.evaluate(ActiveTask(mixed), null, it.label) == ClickResult.CORRECT }
            assertEquals(1, correct.size)
            assertEquals(MusicFacts.midi(1, 0) % 12, correct.single().pitchClass)
            types += correct.single().representation
            val paused = s.copy(active = ActiveTask(mixed))
            assertEquals(mixed, LearningCodec.decode(LearningCodec.encode(paused)).active!!.task)
        }
        assertTrue(types.size >= 2)
        val mixed = AdaptiveMix.apply(s, base, Random(9), now)
        val before = AdaptiveEvidence.View(s, now).samples.filterNot { it.unit.startsWith("application:") }
        val saved = answer(s, mixed, false, now)
        assertEquals(before.map { it.taskId }, AdaptiveEvidence.View(saved, now + 2).samples.filterNot { it.unit.startsWith("application:") }.map { it.taskId })
        assertEquals(1, AdaptiveEvidence.View(saved, now + 2).samples.count { it.unit.startsWith("application:") })
    }
    @Test fun localWeaknessRestoresAsTrialAndCanImmediatelyRegressAgain() {
        val c = Coordinate(1, 0)
        val key = AdaptiveEvidence.positionUnit(c, Direction.POSITION_TO_NOTE)
        var s = profile().copy(regionTraining = RegionRun("LOW", 1, 0))
        var now = 1000L
        fun respond(good: Boolean) {
            val task = position(c, Direction.POSITION_TO_NOTE).copy(adaptive = AdaptiveTask(s.regionTraining!!.adaptive.config,
                if (s.regionTraining!!.adaptive.diagnosing) PracticePurpose.DIAGNOSIS else PracticePurpose.NORMAL, unit = key))
            s = answer(s, task, good, now); now += AdaptiveEvidence.HOLD_MS + 2
        }
        respond(false)
        assertFalse(s.regionTraining!!.adaptive.diagnosing)
        respond(false)
        assertTrue(s.regionTraining!!.adaptive.diagnosing)
        assertEquals(listOf(key), s.regionTraining!!.adaptive.focus)
        assertEquals(RecoveryLayer.LOCAL, s.regionTraining!!.adaptive.layer)
        repeat(6) { respond(true) }
        assertEquals(RecoveryLayer.REGION, s.regionTraining!!.adaptive.layer)
        assertTrue(s.regionTraining!!.adaptive.trial)
        respond(false); respond(false)
        assertTrue(s.regionTraining!!.adaptive.diagnosing)
        assertEquals(profile().progress.keys, s.progress.keys)
    }
    @Test fun sixtyPercentCoverageWithoutFDoesNotRaiseTheSevenNoteBaseline() {
        var s = profile(); var now = 1000L
        val selected = AdaptiveEvidence.targets(FretboardRegion.LOW).filter { MusicFacts.note(it.string, it.fret) != "F" }.take(11)
        repeat(5) { round ->
            if (round == 4) now += AdaptiveEvidence.HOLD_MS
            for (direction in AdaptiveEvidence.positionDirections) for (c in selected) {
                s = answer(s, position(c, direction), true, now); now += 100
            }
        }
        assertEquals(11, AdaptiveEvidence.View(s, now).region(FretboardRegion.LOW).measured)
        val active = s.copy(regionTraining = RegionRun("LOW", 1, 0))
        assertNull(AdaptiveTraining.transition(active, now).regionTraining!!.adaptive.sevenQualifiedAt)
    }
}
