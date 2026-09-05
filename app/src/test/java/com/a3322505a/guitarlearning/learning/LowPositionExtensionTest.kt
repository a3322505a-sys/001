package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.core.MusicFacts
import kotlin.random.Random
import kotlin.test.*

class LowPositionExtensionTest {
    @Test fun allEighteenNaturalPositionsHaveAnAnswerAndARealSuccessor() {
        val nodes = (1..9).map { Curriculum.node("p0$it") }
        assertEquals(18, nodes.flatMap { it.positions }.distinct().size)
        assertTrue(nodes.all { it.implemented })
        nodes.forEachIndexed { index, node ->
            assertEquals(nodes.getOrNull(index + 1)?.id, Curriculum.positionSuccessor(node.id)?.id)
            node.positions.forEach { c ->
                val task = LessonScheduler(Random(1)).makePosition(node.id, c, Direction.POSITION_TO_NOTE, TaskSource.MAIN)
                assertEquals(1, task.options.count { it == MusicFacts.note(c.string, c.fret) })
                assertEquals(ClickResult.CORRECT, AnswerEvaluator.evaluate(ActiveTask(task), null, task.constraint.symbol))
            }
        }
        assertEquals(setOf("A", "B", "C", "D", "E", "F", "G"), Curriculum.noteOptions("p09").toSet())
    }

    @Test fun aSavedP03GraduateCanContinueWithSeparateIntroductionsForGAndA() {
        val progress = (listOf("g00", "n00", "tab01") + (1..3).map { "p0$it" }).associateWith { NodeProgress(masteredAt = 1L) }
        val original = LearnerState(progress = progress, themeId = "forest")
        val co = LearningCoordinator(LessonScheduler(Random(4)))
        var s = co.start(LearningCodec.decode(LearningCodec.encode(original)), "p04", 10)
        assertEquals(Coordinate(3, 0), s.active!!.task.coordinate)
        assertTrue(s.active!!.task.guided)
        val taskId = s.active!!.task.id
        s = co.answer(s, Coordinate(3, 0), now = 11)
        s = co.next(s, taskId, 12)
        assertEquals(Coordinate(3, 2), s.active!!.task.coordinate)
        assertTrue(s.active!!.task.guided)
        assertEquals(original.learnerId, s.learnerId)
        assertEquals("forest", s.themeId)
        assertFalse(Curriculum.mastered(s, "p04"))
        assertTrue(s.attempts.none { it.independent })
    }
}
