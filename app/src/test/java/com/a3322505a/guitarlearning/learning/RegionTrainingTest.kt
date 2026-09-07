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
        val next = co.next(a, t.id, 101)
        return if (next.sessionId == null) co.startRegion(next, s.regionTraining!!.regionId, 102) else next
    }
    @Test fun oneClickWarmsUpThenIntroducesNewPointsAcrossFiniteRounds() {
        var s = co.startRegion(profile(), "LOW", 10)
        repeat(3) { assertEquals(0, s.active!!.task.coordinate!!.fret); assertEquals(it + 1, s.active!!.task.roundSlot); s = finish(s) }
        assertEquals(4, s.active!!.task.roundSlot)
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
    @Test fun regionEntryImmediatelyStartsPositionsAndPreservesOldPracticeAndOriginal() {
        var s = co.start(profile(), "p04", 10)
        val original = s.active
        val plan = PracticePlan(listOf("p01"), PracticeKind.POSITION_MIXED)
        s = co.startPractice(s, plan, 20)
        val practice = s.active
        val requested = co.startRegion(s, "LOW", 30)
        assertNull(requested.practice)
        assertEquals("LOW", requested.regionTraining!!.regionId)
        assertNotEquals(practice, requested.active)
        val paused = requireNotNull(requested.pausedTraining)
        assertEquals(practice, paused.active)
        assertEquals(original, paused.suspendedLesson!!.active)
        val restored = co.startPractice(LearningCodec.decode(LearningCodec.encode(requested)), plan, 40)
        assertEquals(practice, restored.active)
        assertEquals(original, co.end(restored, 50).active)
        assertEquals(requested.active, co.startRegion(restored, "LOW", 60).active)
    }
    @Test fun tabAndChordPartialAnswersSurviveRegionRoundTripWithoutForcingAnOldQuestion() {
        val base = profile().copy(progress = Curriculum.nodes.associate { it.id to NodeProgress(1) })
        for (node in listOf("tab02", "chord-am")) {
            val started = co.start(base, node, 10)
            val task = started.active!!.task
            val partial = co.answer(started, coordinate = Coordinate(6, 4), now = 20)
            val region = co.startRegion(partial, "LOW", 30)
            val regionTask = requireNotNull(region.active).task
            assertTrue(regionTask.direction in RegionTraining.directions)
            assertNull(regionTask.notation)
            assertNull(regionTask.chord)
            assertEquals(partial.attempts, region.attempts)
            val resumed = co.start(LearningCodec.decode(LearningCodec.encode(region)), node, 40)
            assertEquals(task.id, resumed.active!!.task.id)
            assertEquals(partial.active, resumed.active)
            assertEquals(partial.sessionId, resumed.sessionId)
            assertEquals(partial.attempts, resumed.attempts)
        }
    }
    @Test fun lowMiddleFullAndButtonLabelsResumeOnlyTheirOwnRegion() {
        val base = profile().copy(progress = Curriculum.nodes.associate { it.id to NodeProgress(1) },
            introductions = FretboardRegion.entries.flatMap { it.nodes }.flatMap { it.positions }.map { "position:${it.id}" }.toSet())
        var s = base
        val tasks = mutableMapOf<String, ActiveTask>()
        for (region in FretboardRegion.entries) {
            s = co.startRegion(s, region.name, 10)
            tasks[region.name] = s.active!!
        }
        s = LearningCodec.decode(LearningCodec.encode(s))
        for (region in FretboardRegion.entries) {
            s = co.startRegion(s, region.name, 20)
            assertEquals(tasks[region.name], s.active)
            assertNull(s.queuedRegion)
        }
        assertEquals(base.attempts, s.attempts)
    }
    @Test fun pausedPilotKeepsMaterialAndElapsedTimeWhenEnteringRegion() {
        val base = profile().copy(progress = profile().progress + ("tab01" to NodeProgress(1)))
        val pilot = ShortScorePilot.begin(base, PilotMode.SLOW, 10).let { it.copy(pilot = it.pilot!!.copy(elapsedMs = 1200)) }
        val region = co.startRegion(pilot, "LOW", 20)
        assertNull(region.pilot)
        assertTrue(LearningPageAdapter.pilot(region).resume)
        assertEquals(PilotMode.SLOW, LearningPageAdapter.pilot(region).resumeMode)
        val resumed = ShortScorePilot.begin(LearningCodec.decode(LearningCodec.encode(region)), PilotMode.SLOW, 30)
        assertEquals(pilot.active, resumed.active)
        assertEquals(pilot.pilot, resumed.pilot)
        assertEquals(pilot.pilotResults, resumed.pilotResults)
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
}
