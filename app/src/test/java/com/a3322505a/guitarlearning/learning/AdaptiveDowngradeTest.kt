package com.a3322505a.guitarlearning.learning

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Exercises the real region entry, answer/correction and next-task path at phone-like intervals. */
class AdaptiveDowngradeTest {
    private class Run(seed: Int = 21) {
        val co = LearningCoordinator(LessonScheduler(Random(seed)))
        var now = 1_000L
        fun answer(s: LearnerState, correct: Boolean): LearnerState {
            val t = requireNotNull(s.active).task
            now += 5_000
            return if (t.constraint.kind == ConstraintKind.SYMBOL) co.answer(s,
                symbol = if (correct) t.constraint.symbol else t.options.first { it != t.constraint.symbol }, now = now)
            else co.answer(s, coordinate = t.range.positions().first { AnswerEvaluator.matches(it, t.constraint) == correct }, now = now)
        }
        fun finish(s: LearnerState, correct: Boolean): LearnerState {
            var result = answer(s, correct)
            if (!correct) result = answer(result, true)
            val next = co.next(result, result.active!!.task.id, ++now)
            return if (next.sessionId == null) co.startRegion(next, s.regionTraining!!.regionId, ++now) else next
        }
        fun position(s: LearnerState, c: Coordinate, d: Direction = Direction.POSITION_TO_NOTE): LearnerState {
            val t = LessonScheduler(Random(7)).makePosition(Curriculum.nodes.first { c in it.positions }.id, c, d, TaskSource.MAIN)
            return AdaptiveEvidence.present(s, t.copy(adaptive = AdaptiveTask(s.regionTraining!!.adaptive.config,
                if (s.regionTraining!!.adaptive.diagnosing) PracticePurpose.DIAGNOSIS else PracticePurpose.NORMAL,
                unit = AdaptiveEvidence.unit(t))), ++now)
        }
    }
    private fun profile() = LearnerState(progress = Curriculum.nodes.associate { it.id to NodeProgress(1) },
        introductions = Curriculum.nodes.flatMap { it.positions }.map { "position:${it.id}" }.toSet())

    @Test fun threeRealFirstErrorsInterruptProbingInEveryRegion() {
        for (region in FretboardRegion.entries) for (seed in 0..4) {
            val r = Run(seed)
            var s = r.co.startRegion(profile(), region.name, r.now)
            var errors = 0
            while (!s.regionTraining!!.adaptive.diagnosing && errors < 3) {
                assertFalse(s.active!!.task.guided)
                s = r.finish(s, false)
                errors++
            }
            assertTrue("$region seed $seed must simplify by the third first error", s.regionTraining!!.adaptive.diagnosing)
            assertEquals(RecoveryLayer.LOCAL, s.regionTraining!!.adaptive.layer)
            assertTrue(s.regionTraining!!.adaptive.scaffolding)
            assertFalse("diagnosis must interrupt the five-question probe", s.active!!.task.regionProbe)
            assertEquals(PracticePurpose.DIAGNOSIS, s.active!!.task.adaptive!!.purpose)
            assertTrue(s.active!!.task.adaptive!!.scaffolded)
            assertEquals(profile().progress.keys, s.progress.keys)
        }
    }

    @Test fun exposureSuppressedErrorsStillTriggerButCorrectionsNeverCountTwice() {
        val r = Run()
        val c = Coordinate(4, 2)
        var s = r.co.startRegion(profile(), "LOW", r.now)
        s = r.position(s, c)
        s = r.answer(s, false)
        assertFalse(s.regionTraining!!.adaptive.diagnosing)
        repeat(4) { s = r.answer(s, false) }
        s = r.answer(s, true)
        assertEquals(1, AdaptiveEvidence.View(s, r.now).responses.size)
        assertFalse(s.regionTraining!!.adaptive.diagnosing)
        s = r.position(s, c)
        s = r.answer(s, false)
        val view = AdaptiveEvidence.View(s, r.now)
        assertEquals(1, view.samples.size)
        assertEquals(2, view.responses.size)
        assertTrue(s.regionTraining!!.adaptive.diagnosing)
        assertEquals(listOf(AdaptiveEvidence.positionUnit(c, Direction.POSITION_TO_NOTE)), s.regionTraining!!.adaptive.focus)
        val run = s.regionTraining
        s = r.answer(s, true)
        assertEquals(run, s.regionTraining)
        assertEquals(2, s.attempts.size)
    }

    @Test fun twoPointBeginnerPoolCannotHideRepeatedFailure() {
        val r = Run()
        val beginner = LearnerState(progress = listOf("g00", "n00").associateWith { NodeProgress(1) },
            introductions = Curriculum.node("p01").positions.map { "position:${it.id}" }.toSet())
        var s = r.co.startRegion(beginner, "LOW", r.now)
        repeat(3) { if (!s.regionTraining!!.adaptive.diagnosing) s = r.finish(s, false) }
        assertTrue(s.regionTraining!!.adaptive.diagnosing)
        assertTrue(s.active!!.task.adaptive!!.scaffolded)
        assertFalse(s.active!!.task.regionProbe)
        assertTrue(AdaptiveEvidence.View(s, r.now).samples.size < AdaptiveEvidence.View(s, r.now).responses.size)
    }

    @Test fun supportIsVisibleStableAndCannotAwardOriginalMastery() {
        for (direction in AdaptiveEvidence.positionDirections) {
            val r = Run()
            val c = Coordinate(4, 2)
            var s = r.co.startRegion(profile(), "LOW", r.now)
            val original = r.position(s, c, direction).active!!.task
            repeat(2) { s = r.position(s, c, direction); s = r.answer(s, false); s = r.answer(s, true) }
            s = r.co.next(s, s.active!!.task.id, ++r.now)
            val supported = s.active!!.task
            assertEquals(c, supported.coordinate)
            assertEquals(direction, supported.direction)
            assertTrue(supported.adaptive!!.scaffolded)
            if (direction == Direction.POSITION_TO_NOTE) {
                assertEquals(2, supported.options.size)
                assertTrue(original.options.size > supported.options.size)
                assertEquals(1, supported.options.count { it == supported.constraint.symbol })
            } else {
                assertTrue(supported.range.positions().size < original.range.positions().size)
                assertTrue(supported.range.positions().size >= 2)
                assertTrue(AnswerEvaluator.validPositions(supported).isNotEmpty())
            }
            assertEquals(s, LearningCodec.decode(LearningCodec.encode(s)))
            val saved = s.active
            s = r.co.startRegion(r.co.startRegion(s, "MIDDLE", ++r.now), "LOW", ++r.now)
            assertEquals(saved, s.active)
            var supportAnswers = 0
            repeat(30) {
                if (s.regionTraining!!.adaptive.scaffolding) {
                    if (s.active!!.task.adaptive?.scaffolded == true && !s.active!!.task.guided) supportAnswers++
                    s = r.finish(s, true)
                }
            }
            assertTrue("support must withdraw after successful practice", supportAnswers >= 4)
            assertFalse(s.regionTraining!!.adaptive.scaffolding)
            assertTrue("withdrawing support is not recovery", s.regionTraining!!.adaptive.diagnosing)
            assertFalse(s.regionTraining!!.adaptive.trial)
            val helpedIds = s.attempts.filter { it.task.adaptive?.scaffolded == true }.map { it.task.id }.toSet()
            assertTrue(s.attempts.filter { it.task.id in helpedIds }.none { it.independent })
            assertTrue(AdaptiveEvidence.View(s, r.now).samples.none { it.taskId in helpedIds })
        }
    }

    @Test fun realInterleavedRetestsRestoreAndNewErrorsCanImmediatelySimplifyAgain() {
        val r = Run()
        val c = Coordinate(4, 2)
        val key = AdaptiveEvidence.positionUnit(c, Direction.POSITION_TO_NOTE)
        var s = r.co.startRegion(profile(), "LOW", r.now)
        repeat(2) { s = r.position(s, c); s = r.answer(s, false); s = r.answer(s, true) }
        s = r.co.next(s, s.active!!.task.id, ++r.now)
        repeat(100) { if (!s.regionTraining!!.adaptive.trial) s = r.finish(s, true) }
        assertTrue("the actual scheduler must reach recovery", s.regionTraining!!.adaptive.trial)
        assertEquals(RecoveryLayer.REGION, s.regionTraining!!.adaptive.layer)
        assertNull("retention must still be checked later", s.weakPoints.getValue(key).resolvedAt)
        repeat(2) { s = r.position(s, c); s = r.answer(s, false); s = r.answer(s, true) }
        assertTrue(s.regionTraining!!.adaptive.diagnosing)
        assertTrue(s.regionTraining!!.adaptive.scaffolding)
    }

    @Test fun slowAnswersHintsAndOutOfRangeTapsDoNotBecomeFailureSignals() {
        val r = Run()
        var s = r.co.startRegion(profile(), "LOW", r.now)
        repeat(4) {
            s = r.position(s, Coordinate(4, 2), Direction.NOTE_TO_POSITION)
            r.now += 60_000
            s = r.co.answer(s, coordinate = Coordinate(5, 12), now = ++r.now)
            s = r.answer(s, true)
        }
        assertTrue(AdaptiveEvidence.View(s, r.now).responses.all { it.correct })
        assertFalse(s.regionTraining!!.adaptive.diagnosing)
        repeat(3) {
            s = r.position(s, Coordinate(4, 2))
            s = r.co.hint(s, ++r.now)
            s = r.answer(s, false)
        }
        assertTrue(AdaptiveEvidence.View(s, r.now).responses.all { it.correct })
        assertFalse(s.regionTraining!!.adaptive.diagnosing)
    }

    @Test fun continuingBroadFailureChecksFoundationsWithoutWaitingForEightSpacedSamples() {
        val r = Run()
        var s = r.co.startRegion(profile(), "LOW", r.now)
        var errors = 0
        repeat(14) {
            if (s.regionTraining!!.adaptive.layer != RecoveryLayer.OPEN) {
                val guided = s.active!!.task.guided
                s = r.finish(s, guided)
                if (!guided) errors++
            }
        }
        assertEquals(RecoveryLayer.OPEN, s.regionTraining!!.adaptive.layer)
        assertTrue(errors <= 8)
        assertEquals(0, s.active!!.task.coordinate!!.fret)
        assertTrue(s.active!!.task.adaptive!!.scaffolded)
    }

    @Test fun oldSnapshotsDefaultSupportFieldsAndNewSnapshotsKeepTheirExactTask() {
        val r = Run()
        val started = r.co.startRegion(profile(), "LOW", r.now)
        val old = LearningCodec.encode(started).replace(",\"scaffolded\":false", "").replace(",\"scaffolding\":false", "")
        assertEquals(started, LearningCodec.decode(old))
        var s = started
        repeat(2) { s = r.position(s, Coordinate(4, 2)); s = r.answer(s, false); s = r.answer(s, true) }
        s = r.co.next(s, s.active!!.task.id, ++r.now)
        assertEquals(s, LearningCodec.decode(LearningCodec.encode(s)))
    }
}
