package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.audio.*
import org.junit.Test
import org.junit.Assert.*

class TrainingContractsTest {
    private fun position() = LearningTask(id = "b3", nodeId = "p09", skillId = "b3", coordinate = Coordinate(3, 4),
        prompt = "找到 B", explanation = "B3", direction = Direction.NOTE_TO_POSITION,
        constraint = AnswerConstraint(ConstraintKind.NOTE_CLASS, symbol = "B"))
    private fun pitches(spec: TaskAudio?) = spec!!.cues.flatMap { it.pitches }.map { it.noteNumber }

    @Test fun targetAndActualTapRetainOctaveAndEquivalentPositions() {
        val task = position()
        assertEquals(listOf(59), pitches(TaskAudioPolicy.prompt(ActiveTask(task))))
        assertEquals(listOf(59), pitches(TaskAudioPolicy.position(Coordinate(2, 0))))
        assertEquals(listOf(47), pitches(TaskAudioPolicy.position(Coordinate(6, 7))))
        assertEquals(listOf(58), pitches(TaskAudioPolicy.position(Coordinate(3, 3))))
        assertTrue(Coordinate(2, 0) in AnswerEvaluator.validPositions(task))
        assertTrue(Coordinate(3, 4) in AnswerEvaluator.validPositions(task))
    }
    @Test fun independentBoardContainsOnlyPublicMarksAndReverseInputIsAudition() {
        val t = position()
        assertTrue(TrainingUiAdapter.board(ActiveTask(t), FingeringMode.COLORS).marks.isEmpty())
        assertTrue(TrainingUiAdapter.board(ActiveTask(t, hintLevel = 2), FingeringMode.COLORS).marks.any { it.coordinate == Coordinate(3, 4) })
        val reverse = t.copy(direction = Direction.POSITION_TO_NOTE, constraint = AnswerConstraint(ConstraintKind.SYMBOL, symbol = "B"), options = listOf("A", "B"))
        val board = TrainingUiAdapter.board(ActiveTask(reverse), FingeringMode.COLORS)
        assertEquals(BoardInteraction.AUDITION, board.interaction)
        assertEquals(listOf(BoardMark(Coordinate(3, 4), MarkRole.REFERENCE, "?")), board.marks)
        assertEquals(BoardInteraction.AUDITION, TrainingUiAdapter.board(ActiveTask(t), FingeringMode.COLORS, busy = true).interaction)
    }
    @Test fun chordAnswerIsHiddenUntilAssistanceAndMuteProducesNoPitch() {
        val t = ChordLessons.make(ChordShapes.am, "chord-am", TaskSource.MAIN)
        val independent = TrainingUiAdapter.board(ActiveTask(t), FingeringMode.COLORS).chord!!
        assertTrue(independent.fingers.isEmpty() && independent.tones.isEmpty() && independent.openMutedLabels.isEmpty())
        assertNull(TaskAudioPolicy.prompt(ActiveTask(t)))
        val shown = TrainingUiAdapter.board(ActiveTask(t.copy(source = TaskSource.DEMONSTRATION)), FingeringMode.COLORS).chord!!
        assertEquals("X", shown.openMutedLabels[6])
        assertEquals(5, pitches(TaskAudioPolicy.prompt(ActiveTask(t.copy(source = TaskSource.DEMONSTRATION)))).size)
        val barre = ChordLessons.make(ChordShapes.fBarre, "chord-f", TaskSource.DEMONSTRATION)
        assertTrue(TrainingUiAdapter.board(ActiveTask(barre), FingeringMode.NUMBERS).chord!!.fingers.any { it.firstString == 1 && it.lastString == 6 })
    }
    @Test fun independentMultiNoteTasksDoNotAutoplayFullAnswers() {
        val theory = StructureLessons.tasks("scale-major").last()
        assertEquals(theory.relation!!.referencePitches, pitches(TaskAudioPolicy.prompt(ActiveTask(theory))))
        assertEquals(AudioPurpose.PROMPT, TaskAudioPolicy.prompt(ActiveTask(theory))!!.purpose)
        assertEquals(AudioPurpose.FULL_DEMONSTRATION, TaskAudioPolicy.demonstration(ActiveTask(theory))!!.purpose)
        val phrase = ReadingLessons.phrase("tab02", listOf(Coordinate(1, 0), Coordinate(1, 1)), TaskSource.MAIN)
        assertNull(TaskAudioPolicy.prompt(ActiveTask(phrase)))
        assertEquals(listOf(64, 65), pitches(TaskAudioPolicy.prompt(ActiveTask(phrase.copy(source = TaskSource.DEMONSTRATION)))))
        val mapping = position().copy(coordinate = null, mappingNote = "B", constraint = AnswerConstraint(ConstraintKind.SYMBOL, symbol = "Ti"))
        assertNull(TaskAudioPolicy.prompt(ActiveTask(mapping)))
    }
    @Test fun earRequiresFullPlaybackAndBoardCannotProbeOptions() {
        val t = StructureLessons.tasks("ear-intervals").first()
        val a = ActiveTask(t)
        assertEquals(AudioPurpose.EAR, TaskAudioPolicy.prompt(a)!!.purpose)
        assertEquals(t.relation!!.referencePitches + t.relation.targetPitches, pitches(TaskAudioPolicy.prompt(a)))
        val s = LearnerState(active = a, sessionId = "session")
        val ui = TrainingUiAdapter.training(s, false, AudioUiState())
        assertTrue(ui.options.none { it.enabled })
        assertFalse(TrainingUiAdapter.training(s.copy(active = a.copy(audioReady = true)), false, AudioUiState(playing = true)).options.any { it.enabled })
        assertEquals(BoardInteraction.DISABLED, TrainingUiAdapter.board(a, FingeringMode.COLORS).interaction)
        val co = LearningCoordinator()
        assertEquals(s, co.answer(s, symbol = t.constraint.symbol, now = 100))
        val ready = co.playbackCompleted(co.playbackStarted(s, t.id), t.id)
        assertEquals(1, co.answer(ready, symbol = t.constraint.symbol, now = 100).attempts.size)
    }
    @Test fun autoOnceSurvivesRefreshPauseAndSettingChangesAndRejectsStaleRequests() {
        val session = TrainingAudioSession()
        session.bind("a", true, false)
        assertFalse(session.claimAuto("a"))
        session.bind("a", true, true)
        assertTrue(session.claimAuto("a"))
        val first = session.begin()!!
        assertFalse(session.bind("a", true, true))
        assertTrue(session.accepts(first))
        assertFalse(session.claimAuto("a"))
        val second = session.begin()!!
        assertFalse(session.accepts(first))
        assertTrue(session.accepts(second))
        session.bind("a", false, true)
        assertFalse(session.accepts(second))
        session.bind("a", true, true)
        assertFalse(session.claimAuto("a"))
        session.bind("b", true, true)
        assertFalse(session.accepts(second))
        assertTrue(session.claimAuto("b"))
        session.invalidate() // failure/cancel do not schedule retries
        assertFalse(session.claimAuto("b"))
        assertTrue(TrainingAudioSession().apply { bind("b", true, true) }.claimAuto("b"))
    }

    @Test fun expandedBoardKeepsReferencesWithoutExpandingAnswerOrLeakingHiddenTarget() {
        val t = LessonScheduler().makePosition("middle", Coordinate(1, 5), Direction.NOTE_TO_POSITION, TaskSource.MAIN)
        val hidden = TrainingUiAdapter.board(ActiveTask(t), FingeringMode.COLORS)
        assertEquals(0, hidden.firstFret)
        assertEquals(8, hidden.lastFret)
        assertTrue(hidden.marks.isEmpty())
        assertTrue(Coordinate(1, 1) in hidden.interactivePositions)
        assertFalse(Coordinate(1, 1) in hidden.answerPositions)
        val shown = TrainingUiAdapter.board(ActiveTask(t.copy(source = TaskSource.DEMONSTRATION)), FingeringMode.COLORS)
        assertTrue(shown.marks.map { it.coordinate }.containsAll(listOf(Coordinate(1, 1), Coordinate(1, 3), Coordinate(1, 5))))
        val s = LearnerState(active = ActiveTask(position()), introductions = setOf("position:s3:f9"))
        assertEquals(12, TrainingUiAdapter.training(s, false, AudioUiState()).board!!.lastFret)
        val ear = StructureLessons.tasks("ear-intervals").first()
        val state = LearnerState(active = ActiveTask(ear))
        assertEquals(TrainingUiAdapter.training(state, false, AudioUiState()).relation?.lines,
            TrainingUiAdapter.training(state.copy(active = ActiveTask(ear, audioReady = true)), false, AudioUiState()).relation?.lines)
    }
}
