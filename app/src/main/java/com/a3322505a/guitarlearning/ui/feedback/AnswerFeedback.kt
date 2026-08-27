package com.a3322505a.guitarlearning.ui.feedback

import com.a3322505a.guitarlearning.training.AnswerResult
import com.a3322505a.guitarlearning.training.Question

/** Presentation data for the deliberately static V0.1 feedback block. */
data class AnswerFeedback(
    val symbol: String,
    val answerPair: String,
    val correctAnswerText: String?,
    val correctPositionText: String?,
) {
    val isCorrect: Boolean
        get() = symbol == "✓"
}

fun answerFeedback(question: Question, result: AnswerResult): AnswerFeedback {
    val answerPair = "${question.note} / ${question.solfege}"
    return if (result.isCorrect) {
        AnswerFeedback(
            symbol = "✓",
            answerPair = answerPair,
            correctAnswerText = null,
            correctPositionText = null,
        )
    } else {
        AnswerFeedback(
            symbol = "✗",
            answerPair = answerPair,
            correctAnswerText = "正确答案：$answerPair",
            correctPositionText = question.fretPosition?.let {
                "正确位置：${it.string}弦${it.fret}品"
            },
        )
    }
}
