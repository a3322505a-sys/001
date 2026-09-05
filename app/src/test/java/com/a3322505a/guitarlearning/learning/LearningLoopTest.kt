package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.core.MusicFacts
import kotlin.random.Random
import kotlin.test.*

class LearningLoopTest {
    private val now = 1_788_624_000_000L
    private fun answerCorrect(coordinator: LearningCoordinator, state: LearnerState, at: Long): LearnerState {
        val task = requireNotNull(state.active).task
        return if (task.constraint.kind == ConstraintKind.SYMBOL) coordinator.answer(state, symbol = task.constraint.symbol, now = at)
        else coordinator.answer(state, coordinate = AnswerEvaluator.validPositions(task).first(), now = at)
    }

    @Test fun fullFirstLoopCompletesAcrossSavedStateReloads() {
        repeat(12) { seed ->
            val co = LearningCoordinator(LessonScheduler(Random(seed)))
            var s = co.start(LearnerState(), "g00", now)
            var step = 0
            while (s.sessionId != null && step < 300) {
                val id = s.active!!.task.id
                s = answerCorrect(co, s, now + step * 1000)
                assertEquals(Phase.CORRECT, s.active?.phase)
                s = co.next(s, id, now + step * 1000 + 1)
                s = LearningCodec.decode(LearningCodec.encode(s))
                step++
            }
            assertNull(s.sessionId, "seed=$seed, stuck at ${s.currentNode} after $step tasks")
            listOf("g00", "n00", "p01", "tab01", "p02", "p03").forEach { assertTrue(Curriculum.mastered(s, it), "$seed $it") }
            assertFalse(Curriculum.available(s, Curriculum.node("p04")))
            assertEquals(s.attempts.size, s.attempts.map { it.task.id }.distinct().size)
            s.attempts.windowed(10).forEach { assertTrue(it.count { a -> a.task.source == TaskSource.PREVIEW } <= 1) }
            assertTrue(s.attempts.none { it.task.guided && it.independent })
        }
    }

    @Test fun wrongAnswerRequiresCorrectionAndManualNextWithoutDuplicateEvidence() {
        val co = LearningCoordinator()
        val task = LessonScheduler().makePosition("p01", Coordinate(1, 1), Direction.NOTE_TO_POSITION, TaskSource.MAIN)
        var s = LearnerState(currentNode = "p01", sessionId = "test", sessions = listOf(LearningSession("test", now)), active = ActiveTask(task))
        s = co.answer(s, Coordinate(1, 2), now = now)
        assertEquals(Phase.CORRECTING, s.active?.phase)
        assertEquals(s, co.next(s, task.id, now + 1))
        s = co.answer(s, Coordinate(1, 1), now = now + 2)
        assertEquals(Phase.CORRECTED, s.active?.phase)
        assertFalse(s.attempts.single().firstCorrect!!)
        assertTrue(s.attempts.single().corrected)
        assertEquals(s, co.answer(s, Coordinate(1, 1), now = now + 3))
    }

    @Test fun hintsPreviewAndImmediateEchoCannotCountAsIndependent() {
        val co = LearningCoordinator()
        val task = LessonScheduler().makePosition("p01", Coordinate(1, 1), Direction.NOTE_TO_POSITION, TaskSource.MAIN)
        val base = LearnerState(sessionId = "test", sessions = listOf(LearningSession("test", now)), active = ActiveTask(task))
        val helped = answerCorrect(co, co.hint(base), now)
        assertFalse(helped.attempts.single().independent)
        val echo = helped.copy(active = ActiveTask(task.copy(id = newId())))
        assertFalse(answerCorrect(co, echo, now + 1).attempts.last().independent)
        val preview = base.copy(active = ActiveTask(task.copy(source = TaskSource.PREVIEW)))
        assertFalse(answerCorrect(co, preview, now).attempts.single().independent)
    }

    @Test fun stringAndFretTasksAcceptWholeRegions() {
        val stringRule = AnswerConstraint(ConstraintKind.STRING, string = 6)
        assertTrue((0..15).all { AnswerEvaluator.matches(Coordinate(6, it), stringRule) })
        val fretRule = AnswerConstraint(ConstraintKind.FRET, fret = 12)
        assertTrue((1..6).all { AnswerEvaluator.matches(Coordinate(it, 12), fretRule) })
    }

    @Test fun openingAnswerDetailsPreventsImmediateMasteryEvidence() {
        val task = LessonScheduler().makePosition("p01", Coordinate(1, 1), Direction.NOTE_TO_POSITION, TaskSource.MAIN)
        val state = LearnerState(viewedPositions = mapOf("s1:f1" to 10))
        val active = ActiveTask(task, firstCorrect = true)
        assertFalse(MasteryPolicy.independent(state, active, 11))
        assertFalse(MasteryPolicy.independent(state, active, 12))
        assertTrue(MasteryPolicy.independent(state, active, 13))
    }

    @Test fun pitchesAndTabCoordinatesHaveDifferentAnswerSets() {
        val pitch = AnswerConstraint(ConstraintKind.PITCH, midi = MusicFacts.midi(5, 2) + 12)
        assertTrue(AnswerEvaluator.matches(Coordinate(2, 0), pitch))
        assertTrue(AnswerEvaluator.matches(Coordinate(3, 4), pitch))
        val tab = AnswerConstraint(ConstraintKind.COORDINATE, coordinate = Coordinate(3, 4))
        assertFalse(AnswerEvaluator.matches(Coordinate(2, 0), tab))
        assertTrue(AnswerEvaluator.matches(Coordinate(3, 4), tab))
        assertEquals(1, MusicFacts.majorDegree(62, 2))
        assertEquals(7, MusicFacts.majorDegree(61, 2))
    }

    @Test fun extraCorrectNotesDoNotExpandFrozenRequiredSet() {
        val target = Coordinate(2, 1)
        val task = LearningTask(nodeId = "p03", skillId = "C", prompt = "找到C", explanation = "", constraint = AnswerConstraint(ConstraintKind.NOTE_CLASS, symbol = "C"),
            completion = CompletionKind.SET, requiredTargets = listOf(target))
        assertEquals(ClickResult.EXTRA_CORRECT, AnswerEvaluator.evaluate(ActiveTask(task), Coordinate(5, 3), null))
        assertEquals(listOf(target), task.requiredTargets)
        assertEquals(ClickResult.OUTSIDE, AnswerEvaluator.evaluate(ActiveTask(task), Coordinate(1, 8), null))
        val sequence = task.copy(completion = CompletionKind.SEQUENCE, sequence = listOf(AnswerConstraint(ConstraintKind.NOTE_CLASS, symbol = "C"), AnswerConstraint(ConstraintKind.PITCH, midi = 62)))
        assertEquals(ClickResult.PARTIAL, AnswerEvaluator.evaluate(ActiveTask(sequence), Coordinate(5, 3), null))
        assertEquals(ClickResult.CORRECT, AnswerEvaluator.evaluate(ActiveTask(sequence, sequenceIndex = 1), Coordinate(2, 3), null))
    }

    @Test fun oneStrongPositionNeverCarriesTheOtherThrough() {
        val task = LessonScheduler().makePosition("p01", Coordinate(1, 0), Direction.NOTE_TO_POSITION, TaskSource.MAIN)
        val attempts = (1..12).map { i -> Attempt(task.copy(id = "$i", direction = if (i % 2 == 0) Direction.NOTE_TO_POSITION else Direction.POSITION_TO_NOTE),
            "s", i, now + i, "2026-09-05", true, 0, false, true, emptyList(), true) }
        assertFalse(MasteryPolicy.passed(LearnerState(attempts = attempts), Curriculum.node("p01")))
    }

    @Test fun geometryRoundTripsAndDoesNotRenumberHighViewport() {
        for (range in listOf(0..4, 5..8, 0..12, 9..15)) {
            val g = TeachingGeometry(range.first, range.last)
            (1..6).forEach { s -> range.forEach { f -> assertEquals(Coordinate(s, f), g.at(g.center(f), (s - 0.5f) / 6)) } }
            assertNull(g.at(Float.NaN, 0.5f))
            assertNull(g.at(-0.01f, 0.5f))
            assertEquals(0, g.inlays(6)); assertEquals(2, g.inlays(12)); assertEquals(1, g.inlays(15))
        }
    }

    @Test fun startingAndPausingDoNotCreateExtraSessions() {
        val co = LearningCoordinator()
        val s = co.start(LearnerState(), "g00", now)
        assertEquals(s, co.start(s, "g00", now + 1))
        val restored = LearningCodec.decode(LearningCodec.encode(s))
        assertEquals(s, co.start(restored, "g00", now + 2))
        assertFails { co.start(s, "p03", now + 3) }
        assertNull(co.end(s, now + 4).sessionId)
    }
}
