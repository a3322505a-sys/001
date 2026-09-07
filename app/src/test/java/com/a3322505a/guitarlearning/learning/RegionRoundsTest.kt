package com.a3322505a.guitarlearning.learning

import org.junit.Test
import org.junit.Assert.*
import kotlin.random.Random

class RegionRoundsTest {
    private val co = LearningCoordinator(LessonScheduler(Random(7)))
    private fun profile() = LearnerState(progress = Curriculum.nodes.associate { it.id to NodeProgress(1) },
        introductions = Curriculum.nodes.flatMap { it.positions }.map { "position:${it.id}" }.toSet())
    private fun answer(s: LearnerState, now: Long): LearnerState {
        val t = s.active!!.task
        return if (t.options.isNotEmpty()) co.answer(s, symbol = t.constraint.symbol, now = now)
        else co.answer(s, coordinate = AnswerEvaluator.validPositions(t).first(), now = now)
    }
    @Test fun twelveTasksIncludeWarmupAndCorrectionWithoutAThirteenthTask() {
        var s = co.startRegion(profile(), "LOW", 10)
        for (slot in 1..12) {
            val t = s.active!!.task
            assertEquals(slot, t.roundSlot)
            if (slot <= 3) assertEquals(0, t.coordinate!!.fret)
            if (slot == 12) {
                s = if (t.options.isNotEmpty()) co.answer(s, symbol = t.options.first { it != t.constraint.symbol }, now = slot * 1000L)
                else co.answer(s, coordinate = t.range.positions().first { !AnswerEvaluator.matches(it, t.constraint) }, now = slot * 1000L)
                assertEquals(Phase.CORRECTING, s.active!!.phase)
                assertEquals(12, RegionRounds.issued(s).size)
            }
            s = answer(s, slot * 1000L + 1)
            s = co.next(s, t.id, slot * 1000L + 2)
        }
        assertNull(s.active)
        assertNull(s.sessionId)
        assertEquals(12, s.attempts.size)
        assertEquals("natural", s.sessions.last().endReason)
        assertEquals(s, co.end(s, 20000))
        val restarted = co.startRegion(LearningCodec.decode(LearningCodec.encode(s)), "LOW", 20001)
        assertEquals(1, restarted.active!!.task.roundSlot)
        assertNotEquals(s.sessions.last().id, restarted.sessionId)
    }
    @Test fun endPersistsProtectionWithoutInventingAnUnansweredAttemptOrDroppingPausedRegion() {
        var s = co.startRegion(profile(), "MIDDLE", 10)
        val middle = s.active
        s = co.startRegion(s, "LOW", 20)
        val run = s.regionTraining!!.adaptive.copy(diagnosing = true, scaffolding = true,
            focus = listOf("position:s1:f0:POSITION_TO_NOTE"), generation = 3, sinceOrdinal = 1)
        s = s.copy(regionTraining = s.regionTraining!!.copy(adaptive = run))
        val ended = co.end(s, 30)
        assertTrue(ended.attempts.isEmpty())
        assertEquals(middle, ended.pausedRegions["MIDDLE"]!!.active)
        val resumed = co.startRegion(LearningCodec.decode(LearningCodec.encode(ended)), "LOW", 40)
        assertEquals(run, resumed.regionTraining!!.adaptive)
        assertEquals(1, resumed.active!!.task.roundSlot)
        assertTrue(resumed.active!!.task.adaptive!!.scaffolded)
    }
}
