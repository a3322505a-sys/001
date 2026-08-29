package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.InMemoryTrainingStore
import com.a3322505a.guitarlearning.storage.Settings
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TrainingStateMachineTest {
    @Test
    fun wrongAnswerStaysInStableReviewUntilNextQuestionIsClicked() {
        val session = TrainingSession(
            engine = TrainingEngine(
                random = Random(3),
                enabledQuestionTypes = listOf(QuestionType.FretToNote),
            ),
            store = InMemoryTrainingStore(),
        )
        val machine = TrainingStateMachine(session)
        val question = machine.state.question
        val wrongAnswer = question.choices.first { it != question.correctAnswer }

        val incorrect = assertIs<QuestionState.Incorrect>(machine.submitAnswer(wrongAnswer))
        assertEquals(wrongAnswer, incorrect.result.submittedAnswer)
        assertFalse(incorrect.result.isCorrect)
        assertEquals(1, session.currentSession.questionCount)

        val unchanged = machine.submitAnswer(question.correctAnswer)
        assertSame(incorrect, unchanged)
        assertEquals(1, session.currentSession.questionCount)

        val next = assertIs<QuestionState.AwaitingAnswer>(machine.nextQuestion())
        assertTrue(next.question.choices.isNotEmpty())
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
    fun changingStringDifficultyStartsAZeroedRoundWithLegalQuestions() {
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

        machine.submitAnswer(machine.state.question.correctAnswer)
        assertEquals(1, session.currentSession.questionCount)

        val reset = machine.resetStringDifficulty(StringDifficulty.TWO)
        val awaiting = assertIs<QuestionState.AwaitingAnswer>(reset)

        assertEquals(0, session.currentSession.questionCount)
        assertEquals(0, session.currentSession.correctCount)
        assertEquals(StringDifficulty.TWO.selectedStrings, session.currentSettings().selectedStrings)
        assertEquals(StringDifficulty.TWO.selectedStrings, store.loadSettings().selectedStrings)
        assertTrue(awaiting.question.fretPosition?.string?.let { it in 1..2 } == true)
    }
}
