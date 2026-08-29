package com.a3322505a.guitarlearning.ui.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.training.AnswerResult
import com.a3322505a.guitarlearning.training.Question

private val reviewCorrectColor = Color(0xFF2E7D32)

/** Stable, explicit review block shown until the question advances. */
@Composable
fun AnswerReview(
    question: Question,
    result: AnswerResult,
    modifier: Modifier = Modifier,
) {
    val feedback = answerFeedback(question, result)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (result.isCorrect) {
            Text(
                text = feedback.symbol + " " + feedback.answerPair,
                color = reviewCorrectColor,
                style = MaterialTheme.typography.titleMedium,
            )
        } else {
            feedback.correctAnswerText?.let {
                Text(
                    text = it,
                    color = reviewCorrectColor,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
