package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.training.AnswerResult
import com.a3322505a.guitarlearning.training.AnswerValue
import com.a3322505a.guitarlearning.training.QuestionFactory
import com.a3322505a.guitarlearning.training.QuestionState
import com.a3322505a.guitarlearning.training.QuestionType
import com.a3322505a.guitarlearning.core.GuitarCore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NoteNameTrainingFeedbackTest {
    @Test
    fun correctFretboardAnswerRemainsVisibleForOneSecond() {
        assertEquals(1_000L, FRETBOARD_CORRECT_FEEDBACK_DURATION_MS)
    }

    @Test
    fun wrongDForTargetEProducesTheCompactDerivationUntilNextQuestion() {
        val target = GuitarCore.getFretPosition(string = 2, fret = 5)
        val wrong = AnswerValue.FretPosition(string = 2, fret = 3)
        val question = QuestionFactory().create(QuestionType.FretToNote, target)
        val result = AnswerResult(
            accepted = true,
            isCorrect = false,
            submittedAnswer = "2弦3品",
            correctAnswer = "2弦5品",
            knowledgeItemId = question.knowledgeItemId,
            submittedValue = wrong,
            correctValue = question.correctAnswerValue,
        )

        assertEquals(
            "D → E",
            noteTrainingDerivation(
                QuestionState.CorrectionRequired(
                    question = question,
                    result = result,
                    wrongPosition = wrong,
                    correctPosition = question.correctAnswerValue as AnswerValue.FretPosition,
                ),
            ),
        )
        assertEquals(
            "D → E",
            noteTrainingDerivation(
                QuestionState.CorrectionConfirmed(
                    question = question,
                    result = result,
                    wrongPosition = wrong,
                    correctPosition = question.correctAnswerValue,
                ),
            ),
        )
        assertNull(noteTrainingDerivation(QuestionState.AwaitingAnswer(question)))
        assertNull(
            noteTrainingDerivation(
                QuestionState.Correct(
                    question,
                    result.copy(isCorrect = true),
                ),
            ),
        )
    }

    @Test
    fun noteTrainingLayoutUsesTheApprovedPhysicalProportionsAndLargerStats() {
        assertEquals(6.8f, NOTE_TRAINING_FRETBOARD_ASPECT_RATIO)
        assertEquals(184, NOTE_TRAINING_STATS_WIDTH_DP)
    }
}
