package com.a3322505a.guitarlearning.ui.feedback

import com.a3322505a.guitarlearning.training.AnswerResult
import com.a3322505a.guitarlearning.training.Question
import com.a3322505a.guitarlearning.training.QuestionType

/** Presentation data for the deliberately static answer feedback block. */
data class AnswerFeedback(
    val symbol: String,
    val answerPair: String,
    val correctAnswerText: String?,
)

fun answerFeedback(question: Question, result: AnswerResult): AnswerFeedback {
    val answerPair = when (question.type) {
        QuestionType.FretToNote -> question.note
        QuestionType.NoteToDegree,
        QuestionType.DegreeToNote,
        QuestionType.SolfegeToDegree,
        QuestionType.DegreeToSolfege ->
            question.note + " / " + question.solfege + " / " + question.degree
        else -> question.note + " / " + question.solfege
    }
    return if (result.isCorrect) {
        AnswerFeedback(
            symbol = "✓",
            answerPair = answerPair,
            correctAnswerText = null,
        )
    } else {
        AnswerFeedback(
            symbol = "✗",
            answerPair = answerPair,
            correctAnswerText = "正确答案：" + result.correctAnswer,
        )
    }
}
