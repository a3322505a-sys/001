package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.InMemoryTrainingStore
import com.a3322505a.guitarlearning.storage.Settings
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TrainingStateMachineTest {
    @Test
    fun fretboardErrorRequiresOneCorrectionWithoutRecordingExtraAttempts() {
        val store = InMemoryTrainingStore()
        val session = TrainingSession(
            engine = TrainingEngine(
                random = Random(3),
                enabledQuestionTypes = listOf(QuestionType.FretToNote),
            ),
            store = store,
        )
        val machine = TrainingStateMachine(session)
        val question = machine.state.question
        val correctAnswer = assertIs<AnswerValue.FretPosition>(question.correctAnswerValue)
        val wrongAnswer = AnswerValue.FretPosition(
            correctAnswer.string,
            if (correctAnswer.fret == 12) 11 else correctAnswer.fret + 1,
        )

        val incorrect = assertIs<QuestionState.CorrectionRequired>(
            machine.submitAnswer(wrongAnswer),
        )
        assertEquals(wrongAnswer, incorrect.wrongPosition)
        assertFalse(incorrect.result.isCorrect)
        assertEquals(1, session.currentSession.questionCount)
        val recorded = assertNotNull(store.loadProgress(question.knowledgeItemId))
        assertEquals(1, recorded.attempts)
        assertEquals(1.6, recorded.weight, absoluteTolerance = 0.000_001)

        val unchanged = machine.submitAnswer(wrongAnswer)
        assertSame(incorrect, unchanged)
        assertEquals(1, session.currentSession.questionCount)
        assertEquals(recorded, store.loadProgress(question.knowledgeItemId))

        val confirmed = assertIs<QuestionState.CorrectionConfirmed>(
            machine.submitAnswer(correctAnswer),
        )
        assertEquals(correctAnswer, confirmed.correctPosition)
        assertEquals(1, session.currentSession.questionCount)
        assertEquals(0, session.currentSession.correctCount)
        assertEquals(recorded, store.loadProgress(question.knowledgeItemId))

        val next = assertIs<QuestionState.AwaitingAnswer>(machine.nextQuestion())
        assertEquals(AnswerMode.FRETBOARD, next.question.answerMode)
    }

    @Test
    fun correctAnswerTransitionsToReviewAndCannotAdvanceTwice() {
        val session = TrainingSession(
            engine = TrainingEngine(
                random = Random(4),
                enabledQuestionTypes = listOf(QuestionType.NoteToSolfege),
            ),
            store = InMemoryTrainingStore(),
        )
        val machine = TrainingStateMachine(session)

        val correct = assertIs<QuestionState.Correct>(
            machine.submitAnswer(machine.state.question.correctAnswer),
        )
        assertTrue(correct.result.isCorrect)

        val next = assertIs<QuestionState.AwaitingAnswer>(machine.nextQuestion())
        kotlin.test.assertFailsWith<IllegalStateException> { machine.nextQuestion() }
    }

    @Test
    fun invalidAnswerDoesNotLeaveAnsweringState() {
        val session = TrainingSession(
            engine = TrainingEngine(
                random = Random(5),
                enabledQuestionTypes = listOf(QuestionType.NoteToSolfege),
            ),
            store = InMemoryTrainingStore(),
        )
        val machine = TrainingStateMachine(session)

        val state = assertIs<QuestionState.AwaitingAnswer>(machine.submitAnswer("C#"))
        assertEquals(0, session.currentSession.questionCount)
    }

    @Test
    fun changingNoteTrainingRangeStartsAZeroedRoundWithLegalQuestions() {
        val store = InMemoryTrainingStore()
        val session = TrainingSession(
            engine = TrainingEngine(
                settings = Settings(selectedStrings = (1..6).toSet()),
                random = Random(6),
                enabledQuestionTypes = listOf(QuestionType.FretToNote),
            ),
            store = store,
        )
        val machine = TrainingStateMachine(session)

        machine.submitAnswer(machine.state.question.correctAnswerValue)
        assertEquals(1, session.currentSession.questionCount)

        val range = NoteTrainingRange.LOW_POSITION
        val reset = machine.resetNoteTrainingRange(range)
        val awaiting = assertIs<QuestionState.AwaitingAnswer>(reset)

        assertEquals(0, session.currentSession.questionCount)
        assertEquals(0, session.currentSession.correctCount)
        assertEquals(
            range.selectedStrings,
            session.currentSettings().selectedStrings,
        )
        assertEquals(
            range.selectedStrings,
            store.loadSettings().selectedStrings,
        )
        assertEquals(range.fretRange.first, store.loadSettings().fretStart)
        assertEquals(range.fretRange.last, store.loadSettings().fretEnd)
        assertEquals(range.name, store.loadSettings().noteTrainingRangeId)
        assertTrue(
            awaiting.question.fretPosition?.string?.let {
                it in range.selectedStrings
            } == true,
        )
        assertTrue(awaiting.question.fretPosition?.fret?.let { it in range.fretRange } == true)
    }

    @Test
    fun everySupportedRangeKeepsAllGeneratedQuestionsInsideItsCoordinates() {
        val session = TrainingSession(
            engine = TrainingEngine(
                random = Random(7),
                enabledQuestionTypes = listOf(QuestionType.FretToNote),
            ),
            store = InMemoryTrainingStore(),
        )
        val machine = TrainingStateMachine(session)

        NoteTrainingRange.entries.forEach { range ->
            var current = machine.resetNoteTrainingRange(range)
            repeat(60) {
                val awaiting = assertIs<QuestionState.AwaitingAnswer>(current)
                assertTrue(
                    awaiting.question.fretPosition?.string?.let {
                        it in range.selectedStrings
                    } == true,
                )
                assertTrue(
                    awaiting.question.fretPosition?.fret?.let { it in range.fretRange } == true,
                )
                assertIs<QuestionState.Correct>(
                    machine.submitAnswer(awaiting.question.correctAnswerValue),
                )
                current = machine.nextQuestion()
            }
        }
    }
}
