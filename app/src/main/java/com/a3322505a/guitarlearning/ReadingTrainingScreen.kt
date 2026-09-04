package com.a3322505a.guitarlearning

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.audio.PitchPlayer
import com.a3322505a.guitarlearning.audio.handleFretboardTap
import com.a3322505a.guitarlearning.core.FretPosition
import com.a3322505a.guitarlearning.training.TAB_GUIDE_QUESTION_COUNT
import com.a3322505a.guitarlearning.training.TabExercise
import com.a3322505a.guitarlearning.training.TabQuestion
import com.a3322505a.guitarlearning.training.TabTrainingState
import com.a3322505a.guitarlearning.training.TabTrainingStateMachine
import com.a3322505a.guitarlearning.ui.components.PixelButton
import com.a3322505a.guitarlearning.ui.components.PixelButtonStyle
import com.a3322505a.guitarlearning.ui.components.PixelHeader
import com.a3322505a.guitarlearning.ui.components.PixelPanel
import com.a3322505a.guitarlearning.ui.components.PixelOutlinedButton
import com.a3322505a.guitarlearning.ui.fretboard.Fretboard
import com.a3322505a.guitarlearning.ui.fretboard.FretboardInteractionMode
import com.a3322505a.guitarlearning.ui.fretboard.FretboardMarker
import com.a3322505a.guitarlearning.ui.fretboard.FretboardMarkerRole
import com.a3322505a.guitarlearning.ui.theme.PixelBorder
import com.a3322505a.guitarlearning.ui.theme.PixelError
import com.a3322505a.guitarlearning.ui.theme.PixelInkMuted
import com.a3322505a.guitarlearning.ui.theme.PixelSuccess
import kotlinx.coroutines.delay

private const val TAB_CORRECT_FEEDBACK_DURATION_MS = 1_000L

enum class ReadingNotation(
    val title: String,
    val lineCount: Int,
) {
    Tab(title = "TAB 训练", lineCount = 6),
    Staff(title = "五线谱训练", lineCount = 5),
}

@Composable
fun ReadingTrainingMenuScreen(
    onOpenTab: () -> Unit,
    onOpenStaff: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PixelHeader(title = "读谱训练", onBack = onBack)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(0.78f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PixelButton(
                        text = "TAB 训练",
                        onClick = onOpenTab,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PixelButton(
                        text = "五线谱训练",
                        onClick = onOpenStaff,
                        modifier = Modifier.fillMaxWidth(),
                        style = PixelButtonStyle.Secondary,
                    )
                }
            }
        }
    }
}

@Composable
fun ReadingTrainingScreen(
    notation: ReadingNotation,
    pitchPlayer: PitchPlayer,
    tabGuideCompleted: Boolean = true,
    onTabGuideCompleted: () -> Unit = {},
    onBack: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PixelHeader(
                title = notation.title,
                subtitle = "第一把位 · 0–4 品",
                onBack = onBack,
            )
            if (notation == ReadingNotation.Tab) {
                TabReadingBody(
                    pitchPlayer = pitchPlayer,
                    guideCompleted = tabGuideCompleted,
                    onGuideCompleted = onTabGuideCompleted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                ReadingPageSkeleton(
                    notation = notation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ReadingPageSkeleton(
    notation: ReadingNotation,
    modifier: Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PixelPanel(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            NotationPlaceholder(notation)
        }
        PixelPanel(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.35f),
            contentPadding = PaddingValues(8.dp),
        ) {
            Fretboard(
                lastFret = 4,
                showLabels = true,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun TabReadingBody(
    pitchPlayer: PitchPlayer,
    guideCompleted: Boolean,
    onGuideCompleted: () -> Unit,
    modifier: Modifier,
) {
    val stateMachine = remember(guideCompleted) {
        TabTrainingStateMachine(
            guideCompleted = guideCompleted,
            onGuideCompleted = onGuideCompleted,
        )
    }
    var state by remember(stateMachine) { mutableStateOf(stateMachine.state) }

    LaunchedEffect(state) {
        val completed = state as? TabTrainingState.Completed ?: return@LaunchedEffect
        delay(TAB_CORRECT_FEEDBACK_DURATION_MS)
        if (state === completed) state = stateMachine.nextQuestion()
    }

    val markers = tabMarkers(state)
    val interactionMode = when (state) {
        is TabTrainingState.Awaiting -> FretboardInteractionMode.Enabled
        is TabTrainingState.CorrectionRequired -> FretboardInteractionMode.CorrectionOnly
        is TabTrainingState.CorrectionConfirmed,
        is TabTrainingState.Completed -> FretboardInteractionMode.Disabled
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PixelPanel(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (state.question.isGuide) {
                        "新手引导 ${state.question.id}/$TAB_GUIDE_QUESTION_COUNT"
                    } else {
                        "按从左到右的顺序点选"
                    },
                    color = PixelInkMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
                if (!state.question.isGuide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TabExercise.entries.forEach { exercise ->
                            PixelOutlinedButton(
                                text = exercise.label,
                                onClick = { state = stateMachine.selectExercise(exercise) },
                                selected = stateMachine.selectedExercise == exercise,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                TabNotation(
                    question = state.question,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                when (state) {
                    is TabTrainingState.CorrectionRequired -> Text(
                        text = "错了，请先点亮正确位置",
                        color = PixelError,
                        fontWeight = FontWeight.Bold,
                    )
                    is TabTrainingState.CorrectionConfirmed -> PixelButton(
                        text = "下一题",
                        onClick = { state = stateMachine.nextQuestion() },
                        modifier = Modifier.width(160.dp),
                    )
                    is TabTrainingState.Completed -> Text(
                        text = "✓ 正确",
                        color = PixelSuccess,
                        fontWeight = FontWeight.Bold,
                    )
                    is TabTrainingState.Awaiting -> Unit
                }
            }
        }
        PixelPanel(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.35f),
            contentPadding = PaddingValues(8.dp),
        ) {
            Fretboard(
                lastFret = 4,
                showLabels = true,
                markers = markers,
                interactionMode = interactionMode,
                onPositionClick = { position ->
                    handleFretboardTap(position, pitchPlayer) { tapped ->
                        state = stateMachine.submit(tapped)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun tabMarkers(state: TabTrainingState): List<FretboardMarker> = when (state) {
    is TabTrainingState.Awaiting -> state.selected.map {
        FretboardMarker(it, FretboardMarkerRole.CONFIRMED)
    }
    is TabTrainingState.CorrectionRequired ->
        state.selected.map { FretboardMarker(it, FretboardMarkerRole.CONFIRMED) } +
            FretboardMarker(state.wrong, FretboardMarkerRole.INCORRECT) +
            FretboardMarker(state.expected, FretboardMarkerRole.CORRECT)
    is TabTrainingState.CorrectionConfirmed ->
        state.selected.map { FretboardMarker(it, FretboardMarkerRole.CONFIRMED) } +
            FretboardMarker(state.wrong, FretboardMarkerRole.INCORRECT) +
            FretboardMarker(state.expected, FretboardMarkerRole.CONFIRMED)
    is TabTrainingState.Completed -> state.selected.map {
        FretboardMarker(it, FretboardMarkerRole.CORRECT)
    }
}

@Composable
private fun TabNotation(
    question: TabQuestion,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        (1..6).forEach { string ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = string.toString(),
                    color = PixelInkMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(20.dp),
                )
                question.targets.forEach { target ->
                    TabCell(
                        target = target,
                        string = string,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TabCell(
    target: FretPosition,
    string: Int,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawLine(
                color = PixelBorder,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 2.dp.toPx(),
            )
        }
        if (target.string == string) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Text(
                    text = target.fret.toString(),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun NotationPlaceholder(notation: ReadingNotation) {
    Box(modifier = Modifier.fillMaxSize()) {
        val lineColor = PixelBorder
        Canvas(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            val spacing = size.height / (notation.lineCount + 1)
            repeat(notation.lineCount) { index ->
                val y = spacing * (index + 1)
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }
        Text(
            text = if (notation == ReadingNotation.Tab) "TAB 题面" else "五线谱题面",
            color = PixelInkMuted,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
        )
    }
}
