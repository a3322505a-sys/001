package com.a3322505a.guitarlearning.learning

import kotlin.random.Random
import kotlin.test.*

class ChordLessonsTest {
    @Test fun shapesUseFinalFrettedPitchAndRespectOpenMutedAndBarredStrings() {
        assertEquals(listOf(45, 52, 57, 60, 64), ChordShapes.am.pitches())
        assertEquals(listOf(43, 50), ChordShapes.g5Two.pitches())
        assertEquals(listOf(43, 50, 55), ChordShapes.g5Three.pitches())
        assertEquals(setOf(7, 2), ChordShapes.g5Three.pitches().map { it % 12 }.toSet())
        assertEquals(listOf(41, 48, 53, 57, 60, 65), ChordShapes.fBarre.pitches())
        assertEquals(1..6, ChordShapes.fBarre.fingers.first().let { it.firstString..it.lastString })
        assertEquals(2, ChordShapes.fBarre.fingerAt(Coordinate(3, 2)))
        val partial = ChordShape("partial", "局部横按", 5, listOf(1, 1, 2, 3, null, null),
            listOf(FingerSpan(1, 1, 1, 2), FingerSpan(2, 2, 3), FingerSpan(3, 3, 4)))
        assertEquals(listOf(53, 57, 60, 65), partial.pitches())
        assertEquals(FingeringMode.COLORS, FingeringMode.fromId("future"))
    }

    @Test fun partialFailureDoesNotOverwriteCorrectMembersOrInventUnansweredMembers() {
        val co = LearningCoordinator()
        val task = ChordLessons.make(ChordShapes.am, "chord-am", TaskSource.DEMONSTRATION).copy(source = TaskSource.MAIN)
        val session = LearningSession(startedAt = 1)
        var state = LearnerState(sessionId = session.id, sessions = listOf(session), active = ActiveTask(task))
        state = co.answer(state, symbol = "X", now = 2)
        assertEquals(1, state.attempts.single().members.size)
        assertTrue(state.attempts.single().members[0].firstCorrect)
        state = co.answer(state, coordinate = Coordinate(5, 1), now = 3)
        assertEquals(2, state.attempts.single().members.size)
        assertTrue(state.attempts.single().members[0].firstCorrect)
        assertFalse(state.attempts.single().members[1].firstCorrect)
        assertEquals(false, state.attempts.single().firstCorrect)
        state = co.answer(state, coordinate = Coordinate(5, 0), now = 4)
        assertFalse(state.attempts.single().members[1].firstCorrect)
        while (state.active!!.phase != Phase.CORRECTED) {
            val rule = task.sequence[state.active!!.sequenceIndex]
            state = co.answer(state, coordinate = rule.coordinate, symbol = rule.symbol, now = 5)
        }
        assertEquals(6, state.attempts.single().members.size)
        assertTrue(state.attempts.single().members.drop(2).none { it.independent })
        assertFalse(state.attempts.single().independent)
        assertEquals(state, co.answer(state, symbol = "X", now = 6))
    }

    @Test fun shapeLearningCompletesAcrossReloadsWithoutPretendingToMasterPitchDirections() {
        for (id in listOf("chord-am", "chord-g5", "chord-f")) {
            val co = LearningCoordinator(LessonScheduler(Random(88)))
            val progress = (listOf("g00", "n00", "chord-am", "chord-g5") + (1..9).map { "p0$it" })
                .filter { it != id }.associateWith { NodeProgress(masteredAt = 1) }
            var state = co.start(LearnerState(progress = progress), id, 2)
            var count = 0
            while (!Curriculum.mastered(state, id) && count < 800) {
                val task = state.active!!.task
                val rule = task.sequence[state.active!!.sequenceIndex]
                state = co.answer(state, coordinate = rule.coordinate, symbol = rule.symbol, now = 10L + count)
                // Presentation precedes the next answer; a future teaching exposure must not
                // make the fixture's subsequent answers travel backwards in time.
                if (!Curriculum.mastered(state, id) && state.active!!.phase == Phase.CORRECT) state = co.next(state, task.id, 11L + count)
                state = LearningCodec.decode(LearningCodec.encode(state))
                if (state.active == null) state = co.start(state, id, 12L + count)
                count++
            }
            assertTrue(Curriculum.mastered(state, id), "$id stalled after $count clicks")
            assertTrue(state.attempts.none { it.independent })
            assertTrue(state.attempts.filter { it.task.guided }.flatMap { it.members }.none { it.independent })
            assertTrue(MasteryPolicy.positionEvidence(state, Coordinate(1, 1)).isEmpty())
        }
    }
}
