package com.a3322505a.guitarlearning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.training.CorrectErrorStats
import com.a3322505a.guitarlearning.training.QuestionState
import com.a3322505a.guitarlearning.training.StringDifficulty
import com.a3322505a.guitarlearning.training.TrainingSession
import com.a3322505a.guitarlearning.training.TrainingStateMachine
import com.a3322505a.guitarlearning.ui.choices.AnswerChoices
import com.a3322505a.guitarlearning.ui.feedback.AnswerReview
import com.a3322505a.guitarlearning.ui.fretboard.Fretboard

private val difficultySelectedColor = Color(0xFF1565C0)

/** The landscape-only trainer for identifying physical fret locations by note name. */
@Composable
fun NoteNameTrainingScreen(
    trainingSession: TrainingSession,
    onBack: (() -> Unit)? = null,
) {
    val stateMachine = remember(trainingSession) { TrainingStateMachine(trainingSession) }
    var state by remember(trainingSession) { mutableStateOf<QuestionState>(stateMachine.state) }
    var questionSequence by remember(trainingSession) { mutableIntStateOf(0) }
    var difficulty by remember(trainingSession) {
        mutableStateOf(StringDifficulty.fromSettings(trainingSession.currentSettings()))
    }
    val question = state.question
    val submittedAnswer = when (val current = state) {
        is QuestionState.AwaitingAnswer -> null
        is QuestionState.Correct -> current.result
        is QuestionState.Incorrect -> current.result
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(0.70f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        TextButton(
                            onClick = onBack,
                            modifier = Modifier.sizeIn(minWidth = 64.dp, minHeight = 48.dp),
                        ) {
                            Text(text = "返回")
                        }
                    }
                }

                Fretboard(
                    selectedPosition = question.fretPosition,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }

            Box(
                modifier = Modifier
                    .weight(0.30f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "训练范围：" + difficulty.label,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    StringDifficulty.entries.chunked(3).forEach { rowDifficulties ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            rowDifficulties.forEach { option ->
                                val selected = option == difficulty
                                OutlinedButton(
                                    onClick = {
                                        if (!selected) {
                                            difficulty = option
                                            state = stateMachine.resetStringDifficulty(option)
                                            questionSequence += 1
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) difficultySelectedColor else
                                            MaterialTheme.colorScheme.outline,
                                    ),
                                ) {
                                    Text(
                                        text = option.label,
                                        color = if (selected) difficultySelectedColor else
                                            MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }

                    CorrectErrorStats(session = trainingSession.currentSession)

                    AnswerChoices(
                        questionId = questionSequence.toString() + ":" + question.knowledgeItemId,
                        choices = question.choices,
                        submittedAnswer = submittedAnswer?.submittedAnswer,
                        correctAnswer = submittedAnswer?.correctAnswer,
                        onAnswer = { answer -> state = stateMachine.submitAnswer(answer) },
                    )

                    when (val current = state) {
                        is QuestionState.AwaitingAnswer -> Unit
                        is QuestionState.Correct -> {
                            AnswerReview(
                                question = question,
                                result = current.result,
                            )
                            NextQuestionButton { state = stateMachine.nextQuestion(); questionSequence += 1 }
                        }
                        is QuestionState.Incorrect -> {
                            AnswerReview(
                                question = question,
                                result = current.result,
                            )
                            NextQuestionButton { state = stateMachine.nextQuestion(); questionSequence += 1 }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NextQuestionButton(onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text(text = "下一题")
    }
}
