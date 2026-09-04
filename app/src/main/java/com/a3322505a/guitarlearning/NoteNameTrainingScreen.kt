package com.a3322505a.guitarlearning

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.a3322505a.guitarlearning.audio.PitchPlayer
import com.a3322505a.guitarlearning.audio.handleFretboardTap
import com.a3322505a.guitarlearning.core.FretPosition
import com.a3322505a.guitarlearning.core.GuitarCore
import com.a3322505a.guitarlearning.training.AnswerValue
import com.a3322505a.guitarlearning.training.NoteTrainingRange
import com.a3322505a.guitarlearning.training.QuestionState
import com.a3322505a.guitarlearning.training.SessionTrainingStats
import com.a3322505a.guitarlearning.training.TrainingSession
import com.a3322505a.guitarlearning.training.TrainingStateMachine
import com.a3322505a.guitarlearning.ui.components.PixelButton
import com.a3322505a.guitarlearning.ui.components.PixelButtonStyle
import com.a3322505a.guitarlearning.ui.components.PixelHeader
import com.a3322505a.guitarlearning.ui.components.PixelOutlinedButton
import com.a3322505a.guitarlearning.ui.components.PixelPanel
import com.a3322505a.guitarlearning.ui.components.PixelStats
import com.a3322505a.guitarlearning.ui.fretboard.Fretboard
import com.a3322505a.guitarlearning.ui.fretboard.FretboardInteractionMode
import com.a3322505a.guitarlearning.ui.fretboard.FretboardMarker
import com.a3322505a.guitarlearning.ui.fretboard.FretboardMarkerRole
import com.a3322505a.guitarlearning.ui.fretboard.FIRST_FRET
import com.a3322505a.guitarlearning.ui.fretboard.LAST_FRET
import com.a3322505a.guitarlearning.ui.theme.PixelError
import com.a3322505a.guitarlearning.ui.theme.PixelInkMuted
import com.a3322505a.guitarlearning.ui.theme.PixelSuccess
import kotlinx.coroutines.delay

internal const val FRETBOARD_CORRECT_FEEDBACK_DURATION_MS = 1_000L
private const val CORRECT_PULSE_HALF_DURATION_MS = 250
private const val CORRECT_PULSE_SCALE = 1.14f
internal const val NOTE_TRAINING_FRETBOARD_ASPECT_RATIO = 6.8f
internal const val NOTE_TRAINING_STATS_WIDTH_DP = 184
internal val NOTE_TRAINING_VISIBLE_FRET_RANGE = FIRST_FRET..LAST_FRET

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
    val settings = trainingSession.currentSettings()
    val unlockedLevel = settings.unlockedFretboardLevel
    val trainingRange = NoteTrainingRange.fromSettings(settings)
    var seenUnlockedLevel by remember { mutableIntStateOf(unlockedLevel) }
    var overlayText by remember { mutableStateOf<String?>(null) }
    var showSessionStats by remember { mutableStateOf(false) }
    val derivationText = noteTrainingDerivation(state)
    fun requestExit() {
        if (trainingSession.currentStats().answerCount == 0) {
            trainingSession.finish()
            onBack?.invoke()
        } else {
            showSessionStats = true
        }
    }
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

    BackHandler(enabled = onBack != null) {
        if (showSessionStats) {
            showSessionStats = false
        } else {
            requestExit()
        }
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
            unlockedLevel > seenUnlockedLevel -> "已解锁 Lv.$unlockedLevel"
            state is QuestionState.Correct ||
                state is QuestionState.SequenceCompleted ||
                state is QuestionState.SetCompleted ->
                "✓ 正确"
            state is QuestionState.CorrectionConfirmed ||
                state is QuestionState.SetCorrectionConfirmed -> "已纠正"
            else -> null
        }
        seenUnlockedLevel = maxOf(seenUnlockedLevel, unlockedLevel)
        overlayText = feedback
        if (feedback != null) {
            delay(FRETBOARD_CORRECT_FEEDBACK_DURATION_MS)
            if (overlayText == feedback) overlayText = null
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
                            onClick = ::requestExit,
                            modifier = Modifier
                                .width(84.dp)
                                .align(Alignment.CenterStart),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 96.dp)
                            .width(156.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        derivationText?.let { derivation ->
                            Text(
                                text = derivation,
                                color = PixelError,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
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
                            modifier = Modifier.width(NOTE_TRAINING_STATS_WIDTH_DP.dp),
                            emphasized = true,
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.90f)
                            .aspectRatio(NOTE_TRAINING_FRETBOARD_ASPECT_RATIO),
                    ) {
                        Fretboard(
                            markers = markers,
                            interactionMode = interactionMode,
                            markerScale = markerScale.value,
                            onPositionClick = { position ->
                                dispatchNoteTrainingTap(position, trainingRange) { allowedPosition ->
                                    handleFretboardTap(allowedPosition, pitchPlayer) { tapped ->
                                        state = stateMachine.submitAnswer(
                                            AnswerValue.FretPosition(tapped.string, tapped.fret),
                                        )
                                    }
                                }
                            },
                            showLabels = false,
                            firstFret = NOTE_TRAINING_VISIBLE_FRET_RANGE.first,
                            lastFret = NOTE_TRAINING_VISIBLE_FRET_RANGE.last,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            overlayText?.let { message ->
                Text(
                    text = message,
                    color = PixelSuccess,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 64.dp),
                )
            }
        }
    }

    if (showSessionStats) {
        SessionStatsDialog(
            stats = trainingSession.currentStats(),
            onContinue = { showSessionStats = false },
            onFinish = {
                trainingSession.finish()
                showSessionStats = false
                onBack?.invoke()
            },
        )
    }
}

internal fun noteTrainingDerivation(state: QuestionState): String? {
    val (wrongPosition, targetNote) = when (state) {
        is QuestionState.CorrectionRequired -> state.wrongPosition to
            GuitarCore.getFretPosition(
                state.correctPosition.string,
                state.correctPosition.fret,
            ).note
        is QuestionState.CorrectionConfirmed -> state.wrongPosition to
            GuitarCore.getFretPosition(
                state.correctPosition.string,
                state.correctPosition.fret,
            ).note
        is QuestionState.SetCorrectionRequired -> state.wrongPosition to state.question.note
        is QuestionState.SetCorrectionProgress -> state.wrongPosition to state.question.note
        is QuestionState.SetCorrectionConfirmed -> state.wrongPosition to state.question.note
        else -> return null
    }
    val wrongNote = GuitarCore.getFretPosition(wrongPosition.string, wrongPosition.fret).note
    return "$wrongNote → $targetNote"
}

@Composable
private fun SessionStatsDialog(
    stats: SessionTrainingStats,
    onContinue: () -> Unit,
    onFinish: () -> Unit,
) {
    Dialog(
        onDismissRequest = onContinue,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            PixelPanel(
                modifier = Modifier
                    .fillMaxWidth(0.56f)
                    .widthIn(max = 620.dp),
                contentPadding = PaddingValues(20.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "本次训练统计",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "本次答题：${stats.answerCount}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    PixelStats(
                        correctCount = stats.correctCount,
                        errorCount = stats.errorCount,
                        modifier = Modifier.width(240.dp),
                        emphasized = true,
                    )
                    Text(
                        text = "正确率：${stats.correctRatePercent}%",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "最易错音：${formatMostMistakenNotes(stats)}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "薄弱位置：${formatWeakestLocations(stats)}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PixelOutlinedButton(
                            text = "继续训练",
                            onClick = onContinue,
                            modifier = Modifier.weight(1f),
                        )
                        PixelButton(
                            text = "结束并返回",
                            onClick = onFinish,
                            style = PixelButtonStyle.Primary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

internal fun formatMostMistakenNotes(stats: SessionTrainingStats): String {
    if (stats.mostMistakenNotes.isEmpty()) return "无"
    val notes = stats.mostMistakenNotes.joinToString(" / ")
    val countText = if (stats.mostMistakenNotes.size == 1) {
        "${stats.mostMistakenNoteErrorCount} 次"
    } else {
        "各 ${stats.mostMistakenNoteErrorCount} 次"
    }
    return "$notes（$countText）"
}

internal fun formatWeakestLocations(stats: SessionTrainingStats): String {
    if (stats.weakestLocations.isEmpty()) return "无"
    val locations = stats.weakestLocations.joinToString(" / ") {
        "${it.string}弦 ${it.fretRange.first}–${it.fretRange.last}品"
    }
    val count = stats.weakestLocations.first().errorCount
    val countText = if (stats.weakestLocations.size == 1) "$count 次" else "各 $count 次"
    return "$locations（$countText）"
}

internal fun dispatchNoteTrainingTap(
    position: FretPosition,
    trainingRange: NoteTrainingRange,
    onAllowedTap: (FretPosition) -> Unit,
): Boolean {
    if (position.fret !in trainingRange.fretRange) return false
    onAllowedTap(position)
    return true
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
