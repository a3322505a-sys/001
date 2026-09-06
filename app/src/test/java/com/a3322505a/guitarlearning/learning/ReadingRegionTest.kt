package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.core.MusicFacts
import kotlin.random.Random
import kotlin.test.*

class ReadingRegionTest {
    @Test fun regionalNodesCoverAllFortyEightNaturalPositionsWithLocalEvidence() {
        val middle = FretboardRegion.MIDDLE.nodes.flatMap { it.positions }
        val high = FretboardRegion.FULL.nodes.flatMap { it.positions }
        assertEquals(16, middle.distinct().size)
        assertEquals(14, high.distinct().size)
        val actual = (FretboardRegion.LOW.nodes.flatMap { it.positions } + middle + high).toSet()
        assertEquals(48, actual.size)
        assertEquals(PhysicalRange(0, 12).positions().filter { MusicFacts.note(it.string, it.fret).length == 1 }.toSet(), actual)
        val scheduler = LessonScheduler(Random(8))
        (FretboardRegion.MIDDLE.nodes + FretboardRegion.FULL.nodes).forEach { node ->
            node.positions.forEach { c ->
                val t = scheduler.makePosition(node.id, c, Direction.NOTE_TO_POSITION, TaskSource.MAIN)
                assertEquals(listOf(c), AnswerEvaluator.validPositions(t))
                val demo = scheduler.makePosition(node.id, c, Direction.NOTE_TO_POSITION, TaskSource.DEMONSTRATION)
                if (c.fret == 12) {
                    assertEquals(listOf(Coordinate(c.string, 0)), demo.referenceCoordinates)
                    assertEquals(listOf(c), AnswerEvaluator.validPositions(demo))
                    assertEquals(12, MusicFacts.midi(c.string, c.fret) - MusicFacts.midi(c.string, 0))
                }
            }
        }
        val prior = Curriculum.nodes.takeWhile { it.id != "middle" }.associate { it.id to NodeProgress(masteredAt = 1) }
        val co = LearningCoordinator(scheduler)
        var s = co.start(LearnerState(progress = prior), "middle", 2).copy(reviewMode = true)
        repeat(160) { i -> if (!Curriculum.mastered(s, "middle")) {
            val t = s.active!!.task
            s = co.answer(s, coordinate = if (t.constraint.kind == ConstraintKind.SYMBOL) null else AnswerEvaluator.validPositions(t).first(), symbol = t.constraint.symbol.takeIf { t.constraint.kind == ConstraintKind.SYMBOL }, now = i + 10L)
            if (!Curriculum.mastered(s, "middle")) s = co.next(s, t.id, i + 11L)
        } }
        assertTrue(Curriculum.mastered(s, "middle"))
        assertFalse(Curriculum.mastered(s, "m02"))
        assertEquals("音位熟练度 · 评估中", FretboardRegion.MIDDLE.progressLabel(s))
    }

    @Test fun staffUsesActualPitchAndTabRequiresItsCoordinateAtEveryStep() {
        val same = Coordinate(3, 4) // B3, also 2nd open string
        val tab = ReadingLessons.phrase("tab02", listOf(Coordinate(1, 0), Coordinate(2, 0)), TaskSource.MAIN)
        val staff = ReadingLessons.phrase("staff02", listOf(Coordinate(1, 0), Coordinate(2, 0)), TaskSource.MAIN)
        assertEquals(listOf(76, 71), staff.notation!!.writtenPitches)
        assertEquals(7, staffStep(76))
        assertEquals(2, staffStep(67))
        assertEquals(ClickResult.WRONG, AnswerEvaluator.evaluate(ActiveTask(tab, sequenceIndex = 1), same, null))
        assertEquals(ClickResult.CORRECT, AnswerEvaluator.evaluate(ActiveTask(staff, sequenceIndex = 1), same, null))
        assertTrue(AnswerEvaluator.wrongFeedback(tab, same, 1).contains("2弦空弦"))
        val session = LearningSession(startedAt = 1)
        val co = LearningCoordinator()
        var s = LearnerState(sessionId = session.id, sessions = listOf(session), active = ActiveTask(staff))
        s = co.answer(s, Coordinate(1, 0), now = 2)
        s = LearningCodec.decode(LearningCodec.encode(s))
        assertEquals(1, s.active!!.sequenceIndex)
        assertEquals(1, s.attempts.single().members.size)
        s = co.answer(s, same, now = 3)
        assertEquals(listOf(Coordinate(1, 0), same), s.attempts.single().members.map { it.coordinate })
        assertEquals(s, co.answer(s, same, now = 4))
    }

    @Test fun readingLessonsFinishAcrossReloadsAndDoNotCreditPositionRecall() {
        for (id in ReadingLessons.ids) {
            val co = LearningCoordinator(LessonScheduler(Random(19)))
            var s = co.start(LearnerState(progress = Curriculum.nodes.filter { it.id != id }.associate { it.id to NodeProgress(masteredAt = 1) }), id, 2).copy(reviewMode = true)
            repeat(500) { i -> if (!Curriculum.mastered(s, id)) {
                val a = s.active!!; val t = a.task
                val rule = if (t.completion == CompletionKind.SEQUENCE) t.sequence[a.sequenceIndex] else t.constraint
                s = co.answer(s, coordinate = if (rule.kind == ConstraintKind.SYMBOL) null else AnswerEvaluator.validPositions(t, a.sequenceIndex).first(), symbol = rule.symbol, now = i + 10L)
                if (!Curriculum.mastered(s, id) && s.active!!.phase == Phase.CORRECT) s = co.next(s, t.id, i + 11L)
                s = LearningCodec.decode(LearningCodec.encode(s))
            } }
            assertTrue(Curriculum.mastered(s, id), "$id did not finish")
            assertTrue(MasteryPolicy.positionEvidence(s, Coordinate(1, 0)).isEmpty())
        }
    }
}
