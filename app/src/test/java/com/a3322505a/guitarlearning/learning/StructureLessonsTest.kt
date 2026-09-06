package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.core.MusicFacts
import kotlin.random.Random
import kotlin.test.*

class StructureLessonsTest {
    private fun ancestors(id: String): Set<String> = Curriculum.node(id).prerequisites.flatMap { listOf(it) + ancestors(it) }.toSet()

    @Test fun musicRelationsRespectActualPitchSpellingAndLocalPrerequisites() {
        assertEquals(4, MusicRelations.semitones(64, 60))
        assertEquals("同音高", MusicRelations.pitchRelation(59, 59))
        assertEquals("相差一个八度", MusicRelations.pitchRelation(64, 52))
        assertEquals("同音名，跨多个八度", MusicRelations.pitchRelation(64, 40))
        assertEquals(listOf(2, 2, 1, 2, 2, 2, 1), MusicRelations.major.zipWithNext { a, b -> b - a })
        assertEquals(listOf(2, 1, 2, 2, 1, 2, 2), MusicRelations.naturalMinor.zipWithNext { a, b -> b - a })
        assertEquals(listOf(60, 63, 66), MusicRelations.pitches(60, MusicRelations.triads.getValue("减三和弦")))
        assertEquals(listOf(57, 61, 65), MusicRelations.pitches(57, MusicRelations.triads.getValue("增三和弦")))
        assertEquals(listOf("intervals"), Curriculum.node("ear-intervals").prerequisites)
        val p03 = LearnerState(progress = mapOf("p03" to NodeProgress(masteredAt = 1)))
        assertTrue(Curriculum.available(p03, Curriculum.node("structure")))
        assertFalse(Curriculum.available(p03, Curriculum.node("triads")))
        assertFalse(Curriculum.available(p03, Curriculum.node("scale-major")))
        assertFalse(Curriculum.available(p03, Curriculum.node("cross-position")))
        StructureLessons.tasks("intervals").forEach { task ->
            val relation = task.relation!!
            val distance = MusicRelations.semitones(relation.referencePitches.single(), relation.targetPitches.single())
            assertEquals(MusicRelations.intervals[distance], task.constraint.symbol)
        }
        assertEquals(25, StructureLessons.tasks("intervals").size)
        assertEquals(13, StructureLessons.tasks("ear-intervals").size)
    }

    @Test fun musicalStructureAcceptsEquivalentLocationsButDoesNotCreditUnansweredDegrees() {
        val t = StructureLessons.tasks("scale-major").first { it.completion == CompletionKind.SEQUENCE }.copy(id = newId())
        assertTrue(AnswerEvaluator.validPositions(t, 0).size > 1)
        val session = LearningSession(startedAt = 1)
        val co = LearningCoordinator()
        var s = LearnerState(sessionId = session.id, sessions = listOf(session), active = ActiveTask(t))
        val first = AnswerEvaluator.validPositions(t).last()
        s = co.answer(s, first, now = 2)
        assertEquals(listOf(first), s.attempts.single().members.map { it.coordinate })
        assertEquals(1, s.attempts.single().members.size)
        s = LearningCodec.decode(LearningCodec.encode(s))
        val wrong = t.range.positions().first { !AnswerEvaluator.matches(it, t.sequence[1]) }
        s = co.answer(s, wrong, now = 3)
        assertEquals(2, s.attempts.single().members.size)
        assertFalse(s.attempts.single().members.last().firstCorrect)
        assertTrue(MasteryPolicy.positionEvidence(s, first).isEmpty())
        StructureLessons.tasks("cross-position").forEach { task ->
            assertTrue(AnswerEvaluator.validPositions(task).isNotEmpty())
            assertTrue(AnswerEvaluator.validPositions(task).all { MusicFacts.midi(it.string, it.fret) == task.constraint.midi })
        }
    }

    @Test fun hearingRequiresCompletedPlaybackAndReplayCannotCopyOtherTaskEvidence() {
        val t = StructureLessons.tasks("ear-triads").first().copy(id = newId())
        val session = LearningSession(startedAt = 1)
        val co = LearningCoordinator()
        var s = LearnerState(currentNode = "ear-triads", sessionId = session.id, sessions = listOf(session), active = ActiveTask(t))
        assertEquals(s, co.answer(s, symbol = t.constraint.symbol, now = 2))
        assertEquals(s, co.playbackCompleted(s, "stale-task"))
        s = co.playbackStarted(s, t.id)
        assertTrue(s.attempts.isEmpty())
        s = co.playbackCompleted(s, t.id)
        s = LearningCodec.decode(LearningCodec.encode(s))
        s = co.answer(s, symbol = t.constraint.symbol, now = 3)
        assertTrue(s.attempts.single().audioPlayed)
        assertTrue(s.attempts.single().independent)
        assertEquals(7, s.attempts.single().curriculumVersion)
        val theory = StructureLessons.tasks("triads").first().copy(id = newId())
        var assisted = s.copy(active = ActiveTask(theory))
        assisted = co.playbackStarted(assisted, theory.id)
        assisted = co.playbackCompleted(assisted, theory.id)
        assisted = co.answer(assisted, symbol = theory.constraint.symbol, now = 4)
        assertFalse(assisted.attempts.last().independent)
        val hinted = co.hint(s.copy(active = ActiveTask(t.copy(id = newId()), audioReady = true)))
        assertFalse(co.answer(hinted, symbol = t.constraint.symbol, now = 5).attempts.last().independent)
    }

    @Test fun allTenCoursesFinishWithSeparateEvidenceAcrossReloads() {
        for (id in StructureLessons.ids.filterNot { it in FurtherLessons.ids }) {
            val co = LearningCoordinator(LessonScheduler(Random(73)))
            var s = co.start(LearnerState(progress = ancestors(id).associateWith { NodeProgress(masteredAt = 1) }), id, 2).copy(reviewMode = true)
            var steps = 0
            while (!Curriculum.mastered(s, id) && steps < 900) {
                val a = s.active!!; val task = a.task
                val rule = if (task.completion == CompletionKind.SEQUENCE) task.sequence[a.sequenceIndex] else task.constraint
                if (task.relation?.ear == true) s = co.playbackCompleted(s, task.id)
                s = co.answer(s, coordinate = if (rule.kind == ConstraintKind.SYMBOL) null else AnswerEvaluator.validPositions(task, a.sequenceIndex).first(),
                    symbol = rule.symbol, now = steps + 10L)
                if (!Curriculum.mastered(s, id) && s.active!!.phase == Phase.CORRECT) s = co.next(s, task.id, steps + 11L)
                if (steps % 7 == 0) s = LearningCodec.decode(LearningCodec.encode(s))
                steps++
            }
            assertTrue(Curriculum.mastered(s, id), "$id stalled after $steps answers")
            assertEquals(s.attempts.size, s.attempts.map { it.task.id }.distinct().size)
            assertTrue(s.attempts.filter { it.task.guided }.none { it.independent || it.members.any { m -> m.independent } })
            assertTrue(s.attempts.filter { it.task.relation?.ear == true && it.independent }.all { it.audioPlayed })
            assertTrue(s.attempts.none { it.task.direction in listOf(Direction.NOTE_TO_POSITION, Direction.POSITION_TO_NOTE) })
        }
    }
}
