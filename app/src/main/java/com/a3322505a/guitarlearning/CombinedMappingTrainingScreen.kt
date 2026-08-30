package com.a3322505a.guitarlearning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.training.CombinedMappingState
import com.a3322505a.guitarlearning.training.CombinedMappingStateMachine
import com.a3322505a.guitarlearning.training.CORRECT_FEEDBACK_DURATION_MS
import com.a3322505a.guitarlearning.training.CorrectErrorStats
import com.a3322505a.guitarlearning.ui.choices.AnswerChoices
import kotlinx.coroutines.delay

@Composable
fun CombinedMappingTrainingScreen(
    onBack: () -> Unit,
) {
    val stateMachine = remember { CombinedMappingStateMachine() }
    var state by remember { mutableStateOf<CombinedMappingState>(stateMachine.state) }

    LaunchedEffect(state) {
        val correct = state as? CombinedMappingState.Correct ?: return@LaunchedEffect
        delay(CORRECT_FEEDBACK_DURATION_MS)
        if (state === correct) state = stateMachine.advanceAfterCorrect()
    }

    val submittedAnswer = when (val current = state) {
        is CombinedMappingState.AwaitingAnswer -> null
        is CombinedMappingState.Correct -> current.submittedAnswer
        is CombinedMappingState.Incorrect -> current.submittedAnswer
    }
    val shownCorrectAnswer = if (submittedAnswer == null) null else state.correctAnswer

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.sizeIn(minWidth = 64.dp, minHeight = 48.dp),
                ) {
                    Text(text = "返回")
                }
                Text(
                    text = "综合训练",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.width(64.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = state.prompt,
                        style = MaterialTheme.typography.titleLarge,
                    )

                    CorrectErrorStats(
                        correctCount = stateMachine.correctCount,
                        errorCount = stateMachine.errorCount,
                    )

                    AnswerChoices(
                        questionId = state.question.id.toString() + ":" + state.step.name,
                        choices = state.choices,
                        submittedAnswer = submittedAnswer,
                        correctAnswer = shownCorrectAnswer,
                        onAnswer = { answer -> state = stateMachine.submitAnswer(answer) },
                    )

                    if (state is CombinedMappingState.Incorrect) {
                        Button(onClick = { state = stateMachine.nextQuestion() }) {
                            Text(text = "下一题")
                        }
                    }
                }
            }
        }
    }
}
