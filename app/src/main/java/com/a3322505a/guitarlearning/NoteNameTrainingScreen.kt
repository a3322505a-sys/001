package com.a3322505a.guitarlearning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.training.CORRECT_FEEDBACK_DURATION_MS
import com.a3322505a.guitarlearning.training.QuestionState
import com.a3322505a.guitarlearning.training.NoteTrainingRange
import com.a3322505a.guitarlearning.training.NoteTrainingRangeGroup
import com.a3322505a.guitarlearning.training.TrainingSession
import com.a3322505a.guitarlearning.training.TrainingStateMachine
import com.a3322505a.guitarlearning.ui.choices.AnswerChoices
import com.a3322505a.guitarlearning.ui.components.PixelButton
import com.a3322505a.guitarlearning.ui.components.PixelHeader
import com.a3322505a.guitarlearning.ui.components.PixelOutlinedButton
import com.a3322505a.guitarlearning.ui.components.PixelPanel
import com.a3322505a.guitarlearning.ui.components.PixelStats
import com.a3322505a.guitarlearning.ui.feedback.AnswerReview
import com.a3322505a.guitarlearning.ui.fretboard.Fretboard
import kotlinx.coroutines.delay

/** The landscape-only trainer for identifying physical fret locations by note name. */
@Composable
fun NoteNameTrainingScreen(
    trainingSession: TrainingSession,
    stateMachine: TrainingStateMachine,
    selectedRange: NoteTrainingRange,
    onBack: (() -> Unit)? = null,
) {
    var state by remember(stateMachine) { mutableStateOf<QuestionState>(stateMachine.state) }
    var questionSequence by remember(stateMachine) { mutableIntStateOf(0) }
    val question = state.question
    val submittedAnswer = when (val current = state) {
        is QuestionState.AwaitingAnswer -> null
        is QuestionState.Correct -> current.result
        is QuestionState.Incorrect -> current.result
    }
    val session = trainingSession.currentSession

    LaunchedEffect(state) {
        val correctState = state as? QuestionState.Correct ?: return@LaunchedEffect
        delay(CORRECT_FEEDBACK_DURATION_MS)
        if (state === correctState) {
            state = stateMachine.nextQuestion()
            questionSequence += 1
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        color = MaterialTheme.colorScheme.background,
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
                PixelHeader(
                    title = "训练范围",
                    subtitle = selectedRange.label,
                    onBack = onBack,
                )

                PixelPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    Fretboard(
                        selectedPosition = question.fretPosition,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(0.30f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PixelStats(
                    correctCount = session.correctCount,
                    errorCount = session.questionCount - session.correctCount,
                )

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
                    }
                    is QuestionState.Incorrect -> {
                        AnswerReview(
                            question = question,
                            result = current.result,
                        )
                        NextQuestionButton {
                            state = stateMachine.nextQuestion()
                            questionSequence += 1
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NextQuestionButton(onClick: () -> Unit) {
    PixelButton(
        text = "下一题",
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
    )
}

@Composable
fun NoteNameRangeScreen(
    selectedRange: NoteTrainingRange,
    onBack: () -> Unit,
    onSelect: (NoteTrainingRange) -> Unit,
) {
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
            PixelHeader(title = "训练范围", onBack = onBack)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.68f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NoteTrainingRangeGroup.entries.forEach { group ->
                        PixelPanel(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = group.label,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                NoteTrainingRange.entries
                                    .filter { it.group == group }
                                    .forEach { option ->
                                        PixelOutlinedButton(
                                            text = option.label,
                                            onClick = { onSelect(option) },
                                            selected = option == selectedRange,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 52.dp),
                                        )
                                    }
                            }
                        }
                    }
                }
            }
        }
    }
}
