package com.a3322505a.guitarlearning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import com.a3322505a.guitarlearning.core.GuitarCore
import com.a3322505a.guitarlearning.training.AnswerValue
import com.a3322505a.guitarlearning.training.CORRECT_FEEDBACK_DURATION_MS
import com.a3322505a.guitarlearning.training.QuestionState
import com.a3322505a.guitarlearning.training.NoteTrainingRange
import com.a3322505a.guitarlearning.training.NoteTrainingRangeGroup
import com.a3322505a.guitarlearning.training.TrainingSession
import com.a3322505a.guitarlearning.training.TrainingStateMachine
import com.a3322505a.guitarlearning.ui.components.PixelButton
import com.a3322505a.guitarlearning.ui.components.PixelHeader
import com.a3322505a.guitarlearning.ui.components.PixelOutlinedButton
import com.a3322505a.guitarlearning.ui.components.PixelPanel
import com.a3322505a.guitarlearning.ui.components.PixelStats
import com.a3322505a.guitarlearning.ui.fretboard.Fretboard
import com.a3322505a.guitarlearning.ui.fretboard.FretboardInteractionMode
import com.a3322505a.guitarlearning.ui.fretboard.FretboardMarker
import com.a3322505a.guitarlearning.ui.fretboard.FretboardMarkerRole
import com.a3322505a.guitarlearning.ui.theme.PixelError
import com.a3322505a.guitarlearning.ui.theme.PixelSuccess
import kotlinx.coroutines.delay

/** The landscape-only trainer for identifying physical fret locations by note name. */
@Composable
fun NoteNameTrainingScreen(
    trainingSession: TrainingSession,
    stateMachine: TrainingStateMachine,
    onBack: (() -> Unit)? = null,
) {
    var state by remember(stateMachine) { mutableStateOf<QuestionState>(stateMachine.state) }
    val question = state.question
    val session = trainingSession.currentSession
    val markers = when (val current = state) {
        is QuestionState.AwaitingAnswer -> emptyList()
        is QuestionState.Correct -> listOf(
            FretboardMarker(requireNotNull(question.fretPosition), FretboardMarkerRole.CORRECT),
        )
        is QuestionState.CorrectionRequired -> listOf(
            markerFor(current.wrongPosition, FretboardMarkerRole.INCORRECT),
            markerFor(current.correctPosition, FretboardMarkerRole.CORRECT),
        )
        is QuestionState.CorrectionConfirmed -> listOf(
            markerFor(current.wrongPosition, FretboardMarkerRole.INCORRECT),
            markerFor(current.correctPosition, FretboardMarkerRole.CONFIRMED),
        )
        is QuestionState.Incorrect -> emptyList()
    }
    val interactionMode = when (state) {
        is QuestionState.AwaitingAnswer -> FretboardInteractionMode.Enabled
        is QuestionState.CorrectionRequired -> FretboardInteractionMode.CorrectionOnly
        else -> FretboardInteractionMode.Disabled
    }

    LaunchedEffect(state) {
        val correctState = state as? QuestionState.Correct ?: return@LaunchedEffect
        delay(CORRECT_FEEDBACK_DURATION_MS)
        if (state === correctState) {
            state = stateMachine.nextQuestion()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (onBack != null) {
                    PixelOutlinedButton(
                        text = "返回",
                        onClick = onBack,
                        modifier = Modifier
                            .width(84.dp)
                            .align(Alignment.CenterStart),
                    )
                }
                Text(
                    text = question.prompt,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                PixelStats(
                    correctCount = session.correctCount,
                    errorCount = session.questionCount - session.correctCount,
                    modifier = Modifier
                        .width(132.dp)
                        .align(Alignment.CenterEnd),
                )
            }

            when (state) {
                is QuestionState.CorrectionRequired -> Text(
                    text = "错了，点一下正确位置",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = PixelError,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                is QuestionState.CorrectionConfirmed -> Text(
                    text = "已纠正",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = PixelSuccess,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                else -> Unit
            }

            PixelPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(8.dp),
            ) {
                Fretboard(
                    markers = markers,
                    interactionMode = interactionMode,
                    onPositionClick = { position ->
                        state = stateMachine.submitAnswer(
                            AnswerValue.FretPosition(position.string, position.fret),
                        )
                    },
                    showLabels = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (state is QuestionState.CorrectionConfirmed) {
                NextQuestionButton {
                    state = stateMachine.nextQuestion()
                }
            }
        }
    }
}

private fun markerFor(
    value: AnswerValue.FretPosition,
    role: FretboardMarkerRole,
): FretboardMarker = FretboardMarker(
    position = GuitarCore.getFretPosition(value.string, value.fret),
    role = role,
)

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
