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
    fun correctFeedbackShowsBothLabelsWithoutExtraState() {
        val question = factory.create(QuestionType.FretToNote, position)
        val result = AnswerResult(
            accepted = true,
            isCorrect = true,
            submittedAnswer = "A",
            correctAnswer = "A",
            responseMs = 250L,
            knowledgeItemId = question.knowledgeItemId,
        )

        val feedback = answerFeedback(question, result)

        assertEquals("✓", feedback.symbol)
        assertEquals("A / La", feedback.answerPair)
        assertNull(feedback.correctAnswerText)
        assertNull(feedback.correctPositionText)
    }

    @Test
    fun incorrectFretFeedbackShowsAnswerAndPosition() {
        val question = factory.create(QuestionType.FretToNote, position)
        val result = AnswerResult(
            accepted = true,
            isCorrect = false,
            submittedAnswer = "G",
            correctAnswer = "A",
            responseMs = 1_250L,
            knowledgeItemId = question.knowledgeItemId,
        )

        val feedback = answerFeedback(question, result)

        assertEquals("✗", feedback.symbol)
        assertEquals("A / La", feedback.answerPair)
        assertEquals("正确答案：A / La", feedback.correctAnswerText)
        assertEquals("正确位置：6弦5品", feedback.correctPositionText)
    }
}
