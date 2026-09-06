package com.a3322505a.guitarlearning.learning

import kotlin.random.Random
import kotlin.test.*

class PracticeLessonsTest {
    private fun student(): LearnerState = LearnerState(
        progress = listOf("g00", "n00").associateWith { NodeProgress(masteredAt = 1) },
        introductions = setOf("position:s1:f0", "position:s1:f1"))

    @Test fun practicePersistsAndEndingReturnsToTheExactLessonTask() {
        val co = LearningCoordinator(LessonScheduler(Random(81)))
        val lesson = co.start(student(), "p01", 100)
        val plan = PracticePlan(listOf("p01"), PracticeKind.POSITION_MIXED)
        var state = co.startPractice(lesson, plan, 101)
        assertEquals(lesson.active, state.suspendedLesson!!.active)
        assertEquals(plan, state.practice)
        assertEquals(TaskSource.PRACTICE, state.active!!.task.source)
        state = LearningCodec.decode(LearningCodec.encode(state))
        assertEquals(state, co.startPractice(state, plan, 102))
        repeat(40) { i ->
            val task = state.active!!.task
            state = if (task.constraint.kind == ConstraintKind.SYMBOL) co.answer(state, symbol = task.constraint.symbol, now = 200L + i)
                else co.answer(state, coordinate = AnswerEvaluator.validPositions(task).first(), now = 200L + i)
            state = co.next(state, task.id, 300L + i)
        }
        assertTrue(Curriculum.mastered(state, "p01"))
        assertEquals("p01", state.currentNode)
        assertEquals(plan, state.practice)
        val restored = co.end(state, 1000)
        assertNull(restored.practice)
        assertNull(restored.suspendedLesson)
        assertEquals(lesson.active, restored.active)
        assertEquals(lesson.sessionId, restored.sessionId)
        assertEquals(lesson.currentNode, restored.currentNode)
        assertTrue(Curriculum.mastered(restored, "p01"))
        assertEquals(40, restored.attempts.size)
        assertEquals(40, restored.attempts.map { it.task.id }.distinct().size)
        assertEquals(1, restored.sessions.count { it.endedAt == null })
    }

    @Test fun unseenAndLockedScopesAreRejectedAndOneDirectionCannotMasterBoth() {
        val co = LearningCoordinator(LessonScheduler(Random(82)))
        assertFails { co.startPractice(LearnerState(), PracticePlan(listOf("p01"), PracticeKind.POSITION_MIXED), 1) }
        assertFails { co.startPractice(student(), PracticePlan(listOf("p04"), PracticeKind.POSITION_MIXED), 1) }
        var state = co.startPractice(student(), PracticePlan(listOf("p01"), PracticeKind.FIND_POSITION), 2)
        repeat(24) { i ->
            val task = state.active!!.task
            assertEquals(Direction.NOTE_TO_POSITION, task.direction)
            assertTrue(task.coordinate in listOf(Coordinate(1, 0), Coordinate(1, 1)))
            state = co.answer(state, coordinate = AnswerEvaluator.validPositions(task).first(), now = 10L + i)
            state = co.next(state, task.id, 100L + i)
        }
        assertFalse(Curriculum.mastered(state, "p01"))
        val task = state.active!!.task
        state = co.answer(co.hint(state), coordinate = AnswerEvaluator.validPositions(task).first(), now = 200)
        assertFalse(state.attempts.last().independent)
    }
}
