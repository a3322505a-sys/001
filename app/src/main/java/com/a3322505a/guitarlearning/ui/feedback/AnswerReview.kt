package com.a3322505a.guitarlearning.ui.feedback

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.a3322505a.guitarlearning.training.AnswerResult
import com.a3322505a.guitarlearning.training.Question
import com.a3322505a.guitarlearning.ui.components.PixelFeedbackPanel

/** Stable, explicit review block shown until the question advances. */
@Composable
fun AnswerReview(
    question: Question,
    result: AnswerResult,
    modifier: Modifier = Modifier,
) {
    val feedback = answerFeedback(question, result)
    PixelFeedbackPanel(
        success = result.isCorrect,
        text = if (result.isCorrect) {
            feedback.answerPair
        } else {
            feedback.correctAnswerText.orEmpty()
        },
        modifier = modifier,
    )
}
