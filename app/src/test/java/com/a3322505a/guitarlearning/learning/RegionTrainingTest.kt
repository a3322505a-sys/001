package com.a3322505a.guitarlearning.learning

import org.junit.Test
import org.junit.Assert.*
import kotlin.random.Random

class RegionTrainingTest {
    private val co = LearningCoordinator(LessonScheduler(Random(93)))
    private fun profile() = LearnerState(progress = listOf("g00","n00","p01","p02","p03").associateWith { NodeProgress(1) },
        introductions = FretboardRegion.LOW.nodes.take(3).flatMap { it.positions }.map { "position:${it.id}" }.toSet())
    private fun finish(s: LearnerState): LearnerState {
        val t = s.active!!.task
        val a = if (t.constraint.kind == ConstraintKind.SYMBOL) co.answer(s, symbol = t.constraint.symbol, now = 100)
            else co.answer(s, coordinate = AnswerEvaluator.validPositions(t).first(), now = 100)
        return co.next(a, t.id, 101)
    }
    @Test fun oneClickProbesThenIntroducesNewPointsWithoutGlobalCurriculumJump() {
        var s = co.startRegion(profile(), "LOW", 10)
        repeat(5) { assertTrue(s.active!!.task.regionProbe); s = finish(s) }
        assertEquals(5, RegionTraining.history(s).count { it.task.regionProbe })
        assertFalse(s.active!!.task.regionProbe)
        assertEquals("LOW", s.regionTraining!!.regionId)
        assertEquals(3, s.progress.filterKeys { it.startsWith("p0") }.count { it.value.masteredAt != null })
        repeat(40) {
            assertTrue(s.active!!.task.nodeId in FretboardRegion.LOW.nodeIds)
            s = finish(s)
        }
        assertTrue("new positions must be taught", "position:s3:f0" in s.introductions)
        val restored = LearningCodec.decode(LearningCodec.encode(s))
        assertEquals(s, co.startRegion(restored, "LOW", 200))
    }
    @Test fun oldPracticeAndSuspendedOriginalArePreservedAtUpgradeBoundary() {
        var s = co.start(profile(), "p04", 10)
        val original = s.active
        s = co.startPractice(s, PracticePlan(listOf("p01"), PracticeKind.POSITION_MIXED), 20)
        val practice = s.active
        val requested = co.startRegion(s, "LOW", 30)
        assertEquals(practice, requested.active)
        assertEquals(original, requested.suspendedLesson!!.active)
        val restored = co.end(LearningCodec.decode(LearningCodec.encode(requested)), 40)
        assertEquals(original, restored.active)
        val next = finish(restored)
        assertEquals("LOW", next.regionTraining!!.regionId)
        assertNull(next.practice)
    }
    @Test fun allRegionsUseSameFlowAndRespectPrerequisites() {
        for (region in FretboardRegion.entries) {
            val preceding = FretboardRegion.entries.take(region.ordinal).flatMap { it.nodeIds }
            val base = profile().copy(progress = profile().progress + preceding.associateWith { NodeProgress(1) },
                introductions = (profile().introductions + preceding.flatMap { Curriculum.node(it).positions }.map { "position:${it.id}" }).toSet())
            var s = co.startRegion(base, region.name, 10)
            repeat(15) {
                val t = s.active!!.task
                assertTrue(t.nodeId in RegionTraining.nodes(region.name).map { it.id })
                assertTrue(Curriculum.available(s, Curriculum.node(t.nodeId)))
                if (!t.guided) assertTrue("position:${t.coordinate!!.id}" in s.introductions)
                s = finish(s)
            }
        }
    }
    @Test fun recentWeakAndStableDirectionsHaveDifferentStrength() {
        val task = LessonScheduler().makePosition("p01", Coordinate(1,0), Direction.NOTE_TO_POSITION, TaskSource.MAIN)
        fun state(good: Boolean) = profile().copy(regionTraining = RegionRun("LOW", 1, 5), attempts = (1..10).map { i ->
            Attempt(task.copy(id = "$i", direction = if (i % 2 == 0) Direction.NOTE_TO_POSITION else Direction.POSITION_TO_NOTE), "session", i, i.toLong(), "2026-09-06", good, 0, !good, true, emptyList(), true)
        })
        assertEquals(2, RegionTraining.strength(state(true)))
        assertEquals(0, RegionTraining.strength(state(false)))
    }
}
