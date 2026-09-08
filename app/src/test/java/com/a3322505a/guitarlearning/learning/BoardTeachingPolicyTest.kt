package com.a3322505a.guitarlearning.learning

import org.junit.Assert.*
import org.junit.Test

class BoardTeachingPolicyTest {
    private val target = Coordinate(3, 4)
    private val anchor = Coordinate(3, 2)
    private fun task(direction: Direction = Direction.POSITION_TO_NOTE) = LessonScheduler().makePosition(
        "p09", target, direction, TaskSource.MAIN)

    @Test fun independentQuestionDoesNotExposeItsNoteOrHiddenAnswers() {
        val recognition = BoardTeachingPolicy.facts(ActiveTask(task()))
        assertEquals(PositionKnowledge.QUESTION, recognition.positions.single().knowledge)
        assertTrue(recognition.exposedPositions.isEmpty())
        val find = BoardTeachingPolicy.facts(ActiveTask(task(Direction.NOTE_TO_POSITION)))
        assertTrue(find.positions.isEmpty())
        assertTrue(find.exposedPositions.isEmpty())
        assertEquals((0..4).map { Coordinate(3, it) }.toSet(), BoardTeachingPolicy.input(ActiveTask(task())).answerPositions)
    }

    @Test fun correctionExposesOnlyTheTargetAndNearestTaughtAnchor() {
        val a = ActiveTask(task(), phase = Phase.CORRECTING, firstCorrect = false)
        val taught = setOf("position:s3:f0", "position:s3:f2")
        assertEquals(setOf(target, anchor), BoardTeachingPolicy.facts(a, taught).exposedPositions)
        assertEquals(setOf(target), BoardTeachingPolicy.facts(a).exposedPositions)
        val s = LearnerState(active = a, introductions = taught)
        val exposure = CorrectionPresentation.expose(s, a, 100).knowledgeExposures
        // Theme, finger labels and orientation cannot change which knowledge was shown.
        for (mode in FingeringMode.entries) {
            val variant = s.copy(themeId = "midnight", fingeringMode = mode.id, chordVertical = true)
            assertEquals(exposure, CorrectionPresentation.expose(variant, a, 100).knowledgeExposures)
        }
        assertEquals(exposure, CorrectionPresentation.expose(s.copy(knowledgeExposures = exposure), a, 100).knowledgeExposures)
    }

    @Test fun mistakenAnchorDoesNotCountAsAnExposedReference() {
        val a = ActiveTask(task(), phase = Phase.CORRECTING, firstCorrect = false,
            inputs = listOf(InputRecord(1, coordinate = anchor, result = ClickResult.WRONG)))
        val facts = BoardTeachingPolicy.facts(a, setOf("position:s3:f2"))
        assertEquals(setOf(target), facts.exposedPositions)
        assertEquals(PositionKnowledge.MISTAKE, facts.positions.single { it.coordinate == anchor }.knowledge)
    }

    @Test fun chordCorrectionExposesTheShapeButTabCorrectionDoesNotExposeFutureMembers() {
        val chord = ChordLessons.make(ChordShapes.am, "chord-am", TaskSource.MAIN)
        assertTrue(BoardTeachingPolicy.facts(ActiveTask(chord)).chordPositions.isEmpty())
        val correctedChord = ActiveTask(chord, phase = Phase.CORRECTING, firstCorrect = false)
        assertEquals(ChordShapes.am.sounding().filter { it.fret > 0 }.toSet(),
            BoardTeachingPolicy.facts(correctedChord).chordPositions)
        val exposed = CorrectionPresentation.expose(LearnerState(), correctedChord, 100).knowledgeExposures.map { it.target }
        assertTrue(exposed.containsAll(chord.targetSkillIds.map { "skill:$it" }))
        val phrase = ReadingLessons.phrase("tab02", listOf(Coordinate(1, 0), Coordinate(1, 1)), TaskSource.MAIN)
        val a = ActiveTask(phrase, phase = Phase.CORRECTING, firstCorrect = false)
        val tab = CorrectionPresentation.expose(LearnerState(), a, 100).knowledgeExposures.map { it.target }
        assertTrue("skill:${phrase.targetSkillIds[0]}" in tab)
        assertFalse("skill:${phrase.targetSkillIds[1]}" in tab)
    }

    @Test fun interactionKeepsAuditionSeparateFromAnswerAndListeningGates() {
        val middle = LessonScheduler().makePosition("middle", Coordinate(1, 5), Direction.NOTE_TO_POSITION, TaskSource.MAIN)
        val active = ActiveTask(middle)
        val input = BoardTeachingPolicy.input(active)
        assertEquals(PositionInputMode.ANSWER, input.mode)
        assertTrue(Coordinate(1, 1) in input.interactivePositions)
        assertFalse(Coordinate(1, 1) in input.answerPositions)
        assertEquals(PositionInputMode.AUDITION, BoardTeachingPolicy.input(active, busy = true).mode)
        assertEquals(PositionInputMode.AUDITION, BoardTeachingPolicy.input(ActiveTask(task())).mode)
        val ear = ActiveTask(StructureLessons.tasks("ear-intervals").first())
        assertEquals(PositionInputMode.DISABLED, BoardTeachingPolicy.input(ear).mode)
        assertEquals(PositionInputMode.DISABLED, BoardTeachingPolicy.input(ear.copy(audioReady = true)).mode)
        val pilot = ReadingLessons.phrase("tab02", listOf(Coordinate(1, 0), Coordinate(1, 1)), TaskSource.MAIN)
            .copy(notation = ShortScorePilot.score(4, listOf(Coordinate(1, 0), Coordinate(1, 1), Coordinate(1, 3))).notation(NotationKind.TAB))
        assertEquals(PositionInputMode.DISABLED, BoardTeachingPolicy.input(ActiveTask(pilot, phase = Phase.CORRECT)).mode)
    }
}
