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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import com.a3322505a.guitarlearning.core.GuitarCore
import com.a3322505a.guitarlearning.training.AnswerValue
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

internal const val FRETBOARD_CORRECT_FEEDBACK_DURATION_MS = 1_000L
private const val CORRECT_PULSE_HALF_DURATION_MS = 250
private const val CORRECT_PULSE_SCALE = 1.14f

/** The landscape-only trainer for identifying physical fret locations by note name. */
@Composable
fun NoteNameTrainingScreen(
    trainingSession: TrainingSession,
    stateMachine: TrainingStateMachine,
    onBack: (() -> Unit)? = null,
) {
    var state by remember(stateMachine) { mutableStateOf<QuestionState>(stateMachine.state) }
    val markerScale = remember { Animatable(1f) }
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
        val correctState = state as? QuestionState.Correct
        if (correctState == null) {
            markerScale.snapTo(1f)
            return@LaunchedEffect
        }
        markerScale.snapTo(1f)
        markerScale.animateTo(
            targetValue = CORRECT_PULSE_SCALE,
            animationSpec = tween(CORRECT_PULSE_HALF_DURATION_MS),
        )
        markerScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(CORRECT_PULSE_HALF_DURATION_MS),
        )
        delay(
            FRETBOARD_CORRECT_FEEDBACK_DURATION_MS -
                CORRECT_PULSE_HALF_DURATION_MS * 2L,
        )
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

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                val feedback = when (state) {
                    is QuestionState.Correct -> "✓ 正确" to PixelSuccess
                    is QuestionState.CorrectionRequired ->
                        "错了，点一下正确位置" to PixelError
                    is QuestionState.CorrectionConfirmed -> "已纠正" to PixelSuccess
                    else -> null
                }
                feedback?.let { (text, color) ->
                    Text(
                        text = text,
                        color = color,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (state is QuestionState.CorrectionConfirmed) {
                    NextQuestionButton(
                        onClick = { state = stateMachine.nextQuestion() },
                        modifier = Modifier
                            .width(112.dp)
                            .align(Alignment.CenterEnd),
                    )
                }
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
                    markerScale = markerScale.value,
                    onPositionClick = { position ->
                        state = stateMachine.submitAnswer(
                            AnswerValue.FretPosition(position.string, position.fret),
                        )
                    },
                    showLabels = false,
                    modifier = Modifier.fillMaxSize(),
                )
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
private fun NextQuestionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PixelButton(
        text = "下一题",
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
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
