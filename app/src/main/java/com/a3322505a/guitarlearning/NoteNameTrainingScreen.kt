package com.a3322505a.guitarlearning

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.audio.PitchPlayer
import com.a3322505a.guitarlearning.audio.handleFretboardTap
import com.a3322505a.guitarlearning.core.GuitarCore
import com.a3322505a.guitarlearning.training.AnswerValue
import com.a3322505a.guitarlearning.training.NoteTrainingRange
import com.a3322505a.guitarlearning.training.QuestionState
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
import com.a3322505a.guitarlearning.ui.theme.PixelInkMuted
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
    pitchPlayer: PitchPlayer,
    onBack: (() -> Unit)? = null,
) {
    var state by remember(stateMachine) { mutableStateOf<QuestionState>(stateMachine.state) }
    val markerScale = remember { Animatable(1f) }
    val question = state.question
    val introductionText = remember(question.knowledgeItemId) {
        trainingSession.consumeIntroduction(question)
    }
    val session = trainingSession.currentSession
    val unlockedLevel = trainingSession.currentSettings().unlockedFretboardLevel
    var seenUnlockedLevel by remember { mutableIntStateOf(unlockedLevel) }
    var overlayText by remember { mutableStateOf<String?>(null) }
    var overlayIsError by remember { mutableStateOf(false) }
    val anchorMarker = question.anchorPosition?.let {
        FretboardMarker(it, FretboardMarkerRole.ANCHOR)
    }
    fun withAnchor(vararg markers: FretboardMarker): List<FretboardMarker> =
        listOfNotNull(anchorMarker) + markers
    fun sequenceMarkers(
        positions: List<AnswerValue.FretPosition>,
        role: FretboardMarkerRole,
    ): List<FretboardMarker> = positions.map { markerFor(it, role) }
    fun setMarkers(
        positions: Set<AnswerValue.FretPosition>,
        role: FretboardMarkerRole,
    ): List<FretboardMarker> = positions.map { markerFor(it, role) }
    fun expectedSetPositions(): Set<AnswerValue.FretPosition> =
        (question.correctAnswerValue as? AnswerValue.FretSet)?.positions.orEmpty()
    val markers = when (val current = state) {
        is QuestionState.AwaitingAnswer -> withAnchor()
        is QuestionState.AwaitingSequenceAnswer -> emptyList()
        is QuestionState.AwaitingSetAnswer -> emptyList()
        is QuestionState.SetProgress ->
            setMarkers(
                current.selectedPositions + current.extraCorrectPositions,
                FretboardMarkerRole.CONFIRMED,
            )
        is QuestionState.SetCompleted ->
            setMarkers(
                current.selectedPositions + current.extraCorrectPositions,
                FretboardMarkerRole.CORRECT,
            )
        is QuestionState.SetCorrectionRequired ->
            setMarkers(current.confirmedPositions, FretboardMarkerRole.CONFIRMED) +
                markerFor(current.wrongPosition, FretboardMarkerRole.INCORRECT) +
                setMarkers(
                    expectedSetPositions() - current.confirmedPositions,
                    FretboardMarkerRole.CORRECT,
                )
        is QuestionState.SetCorrectionProgress ->
            setMarkers(current.confirmedPositions, FretboardMarkerRole.CONFIRMED) +
                markerFor(current.wrongPosition, FretboardMarkerRole.INCORRECT) +
                setMarkers(
                    expectedSetPositions() - current.confirmedPositions,
                    FretboardMarkerRole.CORRECT,
                )
        is QuestionState.SetCorrectionConfirmed ->
            setMarkers(current.confirmedPositions, FretboardMarkerRole.CONFIRMED) +
                markerFor(current.wrongPosition, FretboardMarkerRole.INCORRECT)
        is QuestionState.SequenceProgress ->
            sequenceMarkers(current.selectedPositions, FretboardMarkerRole.CONFIRMED)
        is QuestionState.SequenceCompleted ->
            sequenceMarkers(current.selectedPositions, FretboardMarkerRole.CORRECT)
        is QuestionState.Correct -> withAnchor(
            FretboardMarker(requireNotNull(question.fretPosition), FretboardMarkerRole.CORRECT),
        )
        is QuestionState.CorrectionRequired ->
            withAnchor(
                *(
                    sequenceMarkers(
                        current.confirmedPositions,
                        FretboardMarkerRole.CONFIRMED,
                    ) + markerFor(current.wrongPosition, FretboardMarkerRole.INCORRECT) +
                        markerFor(current.correctPosition, FretboardMarkerRole.CORRECT)
                ).toTypedArray(),
            )
        is QuestionState.CorrectionConfirmed ->
            withAnchor(
                *(
                    sequenceMarkers(
                        current.confirmedPositions + current.correctPosition,
                        FretboardMarkerRole.CONFIRMED,
                    ) + markerFor(current.wrongPosition, FretboardMarkerRole.INCORRECT)
                ).toTypedArray(),
            )
        is QuestionState.Incorrect -> emptyList()
    }
    val interactionMode = when (state) {
        is QuestionState.AwaitingAnswer,
        is QuestionState.AwaitingSequenceAnswer,
        is QuestionState.AwaitingSetAnswer,
        is QuestionState.SetProgress,
        is QuestionState.SequenceProgress -> FretboardInteractionMode.Enabled
        is QuestionState.CorrectionRequired,
        is QuestionState.SetCorrectionRequired,
        is QuestionState.SetCorrectionProgress -> FretboardInteractionMode.CorrectionOnly
        else -> FretboardInteractionMode.Disabled
    }

    LaunchedEffect(state) {
        val completedState = state.takeIf {
            it is QuestionState.Correct ||
                it is QuestionState.SequenceCompleted ||
                it is QuestionState.SetCompleted
        }
        if (completedState == null) {
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
        if (state === completedState) {
            state = stateMachine.nextQuestion()
        }
    }

    LaunchedEffect(state, unlockedLevel) {
        val feedback = when {
            unlockedLevel > seenUnlockedLevel -> "已解锁 Lv.$unlockedLevel" to false
            state is QuestionState.Correct ||
                state is QuestionState.SequenceCompleted ||
                state is QuestionState.SetCompleted ->
                "✓ 正确" to false
            state is QuestionState.CorrectionRequired ||
                state is QuestionState.SetCorrectionRequired -> "错了" to true
            state is QuestionState.CorrectionConfirmed ||
                state is QuestionState.SetCorrectionConfirmed -> "已纠正" to false
            else -> null
        }
        seenUnlockedLevel = maxOf(seenUnlockedLevel, unlockedLevel)
        overlayText = feedback?.first
        overlayIsError = feedback?.second == true
        if (feedback != null) {
            delay(FRETBOARD_CORRECT_FEEDBACK_DURATION_MS)
            if (overlayText == feedback.first) overlayText = null
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = question.prompt,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        introductionText?.let { introduction ->
                            Text(
                                text = introduction,
                                color = PixelInkMuted,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PixelStats(
                            correctCount = session.correctCount,
                            errorCount = session.questionCount - session.correctCount,
                            modifier = Modifier.width(132.dp),
                        )
                        if (
                            state is QuestionState.CorrectionConfirmed ||
                            state is QuestionState.SetCorrectionConfirmed
                        ) {
                            NextQuestionButton(
                                onClick = { state = stateMachine.nextQuestion() },
                                modifier = Modifier.width(112.dp),
                            )
                        }
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
                            handleFretboardTap(position, pitchPlayer) { tapped ->
                                state = stateMachine.submitAnswer(
                                    AnswerValue.FretPosition(tapped.string, tapped.fret),
                                )
                            }
                        },
                        showLabels = false,
                        lastFret = if (question.kind.startsWith("c_major_scale_")) 4 else 12,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            overlayText?.let { message ->
                Text(
                    text = message,
                    color = if (overlayIsError) PixelError else PixelSuccess,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 64.dp),
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
                    PixelPanel(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            NoteTrainingRange.entries.forEach { option ->
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
