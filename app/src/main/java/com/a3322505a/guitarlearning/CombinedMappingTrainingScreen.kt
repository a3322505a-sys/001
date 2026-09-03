package com.a3322505a.guitarlearning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.training.CombinedMappingState
import com.a3322505a.guitarlearning.training.CombinedMappingStateMachine
import com.a3322505a.guitarlearning.training.CORRECT_FEEDBACK_DURATION_MS
import com.a3322505a.guitarlearning.ui.choices.AnswerChoices
import com.a3322505a.guitarlearning.ui.components.PixelButton
import com.a3322505a.guitarlearning.ui.components.PixelFeedbackPanel
import com.a3322505a.guitarlearning.ui.components.PixelHeader
import com.a3322505a.guitarlearning.ui.components.PixelPanel
import com.a3322505a.guitarlearning.ui.components.PixelStats
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
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PixelHeader(title = "音名 / 唱名 / 级数", onBack = onBack)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                PixelPanel(modifier = Modifier.fillMaxWidth(0.82f)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = state.prompt,
                            style = MaterialTheme.typography.headlineMedium,
                        )

                        PixelStats(
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

                        when (state) {
                            is CombinedMappingState.AwaitingAnswer -> Unit
                            is CombinedMappingState.Correct -> {
                                PixelFeedbackPanel(
                                    success = true,
                                    text = state.correctAnswer,
                                )
                            }
                            is CombinedMappingState.Incorrect -> {
                                PixelFeedbackPanel(
                                    success = false,
                                    text = "正确答案：" + state.correctAnswer,
                                )
                                PixelButton(
                                    text = "下一题",
                                    onClick = { state = stateMachine.nextQuestion() },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
