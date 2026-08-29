package com.a3322505a.guitarlearning.ui.feedback

import com.a3322505a.guitarlearning.core.GuitarCore
import com.a3322505a.guitarlearning.training.AnswerResult
import com.a3322505a.guitarlearning.training.QuestionFactory
import com.a3322505a.guitarlearning.training.QuestionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnswerFeedbackTest {
    private val factory = QuestionFactory()
    private val position = GuitarCore.getFretPosition(6, 5)

    @Test
    fun fretToNoteCorrectFeedbackShowsOnlyTheNote() {
        val question = factory.create(QuestionType.FretToNote, position)
        val result = AnswerResult(
            accepted = true,
            isCorrect = true,
            submittedAnswer = "A",
            correctAnswer = "A",
            knowledgeItemId = question.knowledgeItemId,
        )

        val feedback = answerFeedback(question, result)

        assertEquals("✓", feedback.symbol)
        assertEquals("A", feedback.answerPair)
        assertNull(feedback.correctAnswerText)
    }

    @Test
    fun incorrectFretFeedbackKeepsOnlyTheCorrectAnswer() {
        val question = factory.create(QuestionType.FretToNote, position)
        val result = AnswerResult(
            accepted = true,
            isCorrect = false,
            submittedAnswer = "G",
            correctAnswer = "A",
            knowledgeItemId = question.knowledgeItemId,
        )

        val feedback = answerFeedback(question, result)

        assertEquals("✗", feedback.symbol)
        assertEquals("A", feedback.answerPair)
        assertEquals("正确答案：A", feedback.correctAnswerText)
    }

    @Test
    fun mappingIncorrectFeedbackKeepsOnlyTheCorrectAnswer() {
        val question = factory.createForNote(QuestionType.NoteToSolfege, "A")
        val result = AnswerResult(
            accepted = true,
            isCorrect = false,
            submittedAnswer = "Mi",
            correctAnswer = "La",
            knowledgeItemId = question.knowledgeItemId,
        )

        val feedback = answerFeedback(question, result)

        assertEquals("A / La", feedback.answerPair)
        assertEquals("正确答案：La", feedback.correctAnswerText)
    }
}
