package com.a3322505a.guitarlearning.ui.choices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pure interaction guard used by the answer-choice UI and by its unit tests. */
class AnswerSubmissionState(private val allowedChoices: List<String>) {
    private var submitted: String? = null

    val submittedAnswer: String?
        get() = submitted

    val canSubmit: Boolean
        get() = submitted == null

    fun submit(answer: String): Boolean {
        if (!canSubmit || answer !in allowedChoices) return false
        submitted = answer
        return true
    }

    fun reset() {
        submitted = null
    }
}

/**
 * Shared, deliberately static answer buttons. Changing questionId resets the one-submit guard.
 */
@Composable
fun AnswerChoices(
    questionId: String,
    choices: List<String>,
    onAnswer: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var submittedAnswer by remember(questionId) { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        choices.chunked(4).forEach { rowChoices ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowChoices.forEach { choice ->
                    Button(
                        onClick = {
                            if (submittedAnswer == null) {
                                submittedAnswer = choice
                                onAnswer(choice)
                            }
                        },
                        enabled = submittedAnswer == null,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Text(text = choice, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
