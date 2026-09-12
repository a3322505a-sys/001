package com.a3322505a.guitarlearning.learning

import kotlin.random.Random
import kotlin.test.*

class LessonRoundsAndMaterialTest {
    private fun profile() = LearnerState(progress = Curriculum.nodes.associate { it.id to NodeProgress(1) })
    private fun finish(co: LearningCoordinator, input: LearnerState, at: Long, wrong: Boolean = false): LearnerState {
        var s = input
        if (wrong) s = co.answer(s, symbol = "not-an-answer", now = at)
        while (s.active!!.phase !in listOf(Phase.CORRECT, Phase.CORRECTED)) {
            val a = s.active!!
            val rule = a.task.sequence.getOrNull(a.sequenceIndex) ?: a.task.constraint
            s = if (rule.kind == ConstraintKind.SYMBOL) co.answer(s, symbol = rule.symbol, now = at + 1)
                else co.answer(s, coordinate = AnswerEvaluator.validPositions(a.task, a.sequenceIndex).first(), now = at + 1)
        }
        return s
    }
    @Test fun wrongAndReviewRoundsEndAtTwelveDistinctTasksAndReloadWithoutExtraQuestion() {
        for (wrong in listOf(false, true)) {
            val co = LearningCoordinator(LessonScheduler(Random(4)))
            var s = co.start(profile(), "n00", 10)
            repeat(12) { i ->
                val id = s.active!!.task.id
                s = finish(co, s, 20L + i * 10, wrong)
                s = co.next(s, id, 22L + i * 10)
                s = LearningCodec.decode(LearningCodec.encode(s))
                if (i < 11) assertNotNull(s.active) else assertNull(s.active)
            }
            assertEquals(12, s.attempts.size)
            assertNull(s.sessionId)
            assertEquals("natural", s.sessions.last().endReason)
            assertEquals(s, co.next(s, s.attempts.last().task.id, 200))
            val restarted = co.start(s, "n00", 201)
            assertEquals(s.attempts, restarted.attempts)
            assertEquals(s.familyRuns, restarted.familyRuns)
            assertEquals(1, LessonRounds.issued(restarted).size)
        }
    }
    @Test fun completedCourseSettlesBeforeSchedulingAnotherCourse() {
        val co = LearningCoordinator()
        var s = co.start(LearnerState(progress = mapOf("g00" to NodeProgress(1))), "n00", 1)
        var n = 0
        while (s.active != null && n < 12) {
            val id = s.active!!.task.id
            s = co.next(finish(co, s, 10L + n * 10), id, 12L + n * 10)
            n++
        }
        assertTrue(Curriculum.mastered(s, "n00"))
        assertEquals("n00", s.currentNode)
        assertNull(s.active)
        assertTrue(n < 12)
        assertFalse(Curriculum.mastered(s, "p01"))
    }
    @Test fun newMappingLearnerMeetsDegreesBeforeSevenNoteMasteryAndAllAnswerDirections() {
        val co = LearningCoordinator(LessonScheduler(Random(7)))
        var s = co.start(profile().copy(progress = profile().progress - "mapping"), "mapping", 1)
        repeat(80) { i ->
            if (s.active == null) s = co.start(s, "mapping", i * 100L + 1)
            val task = s.active!!.task
            if (task.direction in MappingLessons.degreeDirections) {
                assertEquals(0, task.tonicPitchClass)
                assertTrue(task.prompt.contains("C 大调"))
            }
            s = finish(co, s, i * 100L + 2)
            s = co.next(s, task.id, i * 100L + 4)
        }
        val firstDegree = s.attempts.indexOfFirst { it.task.direction in MappingLessons.degreeDirections }
        assertTrue(firstDegree in 1..5)
        assertEquals(MappingLessons.directions.toSet(), s.attempts.filter { !it.task.guided }.map { it.task.direction }.toSet())
        assertTrue(s.attempts.filter { it.task.guided }.none { it.independent })
    }
    @Test fun materialHasMatchedTopologyTransferAndUsesOneFactForNotationAndJudging() {
        assertEquals(12, TabMaterial.phrases.size)
        assertEquals(12, TabMaterial.phrases.map { it.positions }.distinct().size)
        for (p in TabMaterial.phrases) {
            assertEquals(p.crossing, p.positions.map { it.string }.distinct().size > 1)
            val t = TabMaterial.task(p, TaskSource.MAIN)
            assertEquals(p.positions, t.notation!!.coordinates)
            assertEquals(p.positions, t.sequence.map { it.coordinate })
            assertEquals(3, t.targetSkillIds.size)
        }
        assertTrue(TabMaterial.catalog(LearnerState()).all { !it.crossing && !it.transfer })
        assertEquals(listOf(Coordinate(1,0), Coordinate(1,1)), TabMaterial.singlePositions(LearnerState()))
        assertTrue(TabMaterial.singlePositions(profile()).size > 6)
    }
    @Test fun wholePhraseCountsAsOneTaskAndPilotRemainsOutsideOrdinaryRoundLimit() {
        val co = LearningCoordinator()
        val task = TabMaterial.task(TabMaterial.phrases.first(), TaskSource.MAIN)
        val s = profile().copy(currentNode = "tab02", sessionId = "s", sessions = listOf(LearningSession("s",1)), active = ActiveTask(task), reviewMode = true)
        val done = finish(co, s, 10)
        assertEquals(1, LessonRounds.issued(done).size)
        assertEquals(3, done.attempts.single().members.size)
        val run = PilotRun(0,PilotMode.SLOW,ShortScorePilot.score(0,ReadingLessons.positions.take(3)),NotationKind.TAB)
        assertFalse(LessonRounds.active(done.copy(pilot = run)))
    }
}
