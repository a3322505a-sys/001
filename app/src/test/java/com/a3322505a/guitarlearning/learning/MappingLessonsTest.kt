package com.a3322505a.guitarlearning.learning

import kotlin.random.Random
import kotlin.test.*

class MappingLessonsTest {
    private fun graduate() = LearnerState(progress = listOf("g00", "n00", "p01", "p02", "p03")
        .associateWith { NodeProgress(masteredAt = 1L) })

    @Test fun mappingIsAvailableAfterP03AndCompletesAllFourDirectionsAcrossReloads() {
        val co = LearningCoordinator(LessonScheduler(Random(19)))
        var state = co.start(graduate(), "mapping", 10)
        assertFalse(Curriculum.mastered(state, "p09"))
        var steps = 0
        while (!Curriculum.mastered(state, "mapping") && steps < 600) {
            val task = state.active!!.task
            assertEquals("mapping", task.nodeId)
            assertTrue(task.constraint.symbol in task.options)
            state = co.answer(state, symbol = task.constraint.symbol, now = 100L + steps * 2)
            if (!Curriculum.mastered(state, "mapping")) state = co.next(state, task.id, 101L + steps * 2)
            state = LearningCodec.decode(LearningCodec.encode(state))
            steps++
        }
        assertTrue(Curriculum.mastered(state, "mapping"), "stalled after $steps tasks")
        MappingLessons.notes.forEach { note ->
            assertTrue(MappingLessons.pairPassed(state, note, false))
            assertTrue(MappingLessons.pairPassed(state, note, true))
        }
        assertTrue(state.attempts.none { it.task.guided && it.independent })
        assertTrue("mapping:fixed:A:intro" in state.introductions)
        assertEquals(state.attempts.size, state.attempts.map { it.task.id }.distinct().size)
    }

    @Test fun fixedNamesAndRelativeDegreesAreNotInterchangeable() {
        val cInC = MappingLessons.make("C", Direction.NOTE_TO_DEGREE, TaskSource.MAIN)
        val cInG = MappingLessons.make("C", Direction.NOTE_TO_DEGREE, TaskSource.MAIN, tonic = 7)
        assertEquals("1", cInC.constraint.symbol)
        assertEquals("4", cInG.constraint.symbol)
        assertEquals(7, cInG.tonicPitchClass)
        assertEquals("major", cInG.tonalMode)
        assertTrue(cInG.prompt.contains("G 大调"))
        val fixed = MappingLessons.make("C", Direction.NOTE_TO_SOLFEGE, TaskSource.MAIN)
        val co = LearningCoordinator()
        val session = LearningSession("test", 1)
        val start = graduate().copy(sessionId = session.id, sessions = listOf(session), active = ActiveTask(fixed))
        val answered = co.answer(start, symbol = "Do", now = 2)
        assertEquals(1, MappingLessons.evidence(answered, "C", false).size)
        assertTrue(MappingLessons.evidence(answered, "C", true).isEmpty())
        assertEquals(Direction.NOTE_TO_SOLFEGE, answered.attempts.single().task.direction)
        assertFalse(MappingLessons.pairPassed(answered, "C", false))
    }

    @Test fun hintsAndReverseEchoDoNotGrantEvidenceAndCorrectionKeepsFirstWrong() {
        val co = LearningCoordinator()
        val task = MappingLessons.make("A", Direction.NOTE_TO_SOLFEGE, TaskSource.MAIN)
        val session = LearningSession("test", 1)
        var state = graduate().copy(sessionId = session.id, sessions = listOf(session), active = ActiveTask(task))
        state = co.answer(co.hint(state), symbol = "La", now = 2)
        assertFalse(state.attempts.single().independent)
        val reversed = MappingLessons.make("A", Direction.SOLFEGE_TO_NOTE, TaskSource.MAIN)
        state = state.copy(active = ActiveTask(reversed))
        state = co.answer(state, symbol = "A", now = 3)
        assertFalse(state.attempts.last().independent)
        state = state.copy(active = ActiveTask(task.copy(id = newId())))
        state = co.answer(state, symbol = "Do", now = 4)
        state = co.answer(state, symbol = "La", now = 5)
        assertEquals(Phase.CORRECTED, state.active!!.phase)
        assertEquals(false, state.attempts.last().firstCorrect)
        assertEquals(state, co.answer(state, symbol = "La", now = 6))
    }
}
