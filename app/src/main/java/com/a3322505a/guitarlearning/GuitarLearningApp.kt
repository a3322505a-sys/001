package com.a3322505a.guitarlearning

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.training.IntervalLevel
import com.a3322505a.guitarlearning.training.NoteTrainingRange
import com.a3322505a.guitarlearning.training.TrainingSession
import com.a3322505a.guitarlearning.training.TrainingStateMachine
import com.a3322505a.guitarlearning.ui.components.PixelButton
import com.a3322505a.guitarlearning.ui.components.PixelButtonStyle
import com.a3322505a.guitarlearning.ui.components.PixelPanel
import com.a3322505a.guitarlearning.ui.theme.PixelGold
import com.a3322505a.guitarlearning.ui.theme.PixelInkMuted

enum class AppDestination {
    Home,
    NoteNameRange,
    NoteName,
    CombinedMapping,
    BasicTheory,
    IntervalLevels,
    IntervalTraining,
}

fun usesLandscapeLayout(destination: AppDestination): Boolean =
    destination == AppDestination.NoteName

fun previousDestination(destination: AppDestination): AppDestination = when (destination) {
    AppDestination.NoteName -> AppDestination.NoteNameRange
    AppDestination.NoteNameRange,
    AppDestination.CombinedMapping -> AppDestination.Home
    AppDestination.BasicTheory -> AppDestination.Home
    AppDestination.IntervalLevels -> AppDestination.BasicTheory
    AppDestination.IntervalTraining -> AppDestination.IntervalLevels
    AppDestination.Home -> AppDestination.Home
}

@Composable
fun GuitarLearningApp(
    noteTrainingSession: TrainingSession,
    intervalTrainingSession: TrainingSession,
    onDestinationChanged: (AppDestination) -> Unit,
) {
    var destinationName by rememberSaveable { mutableStateOf(AppDestination.Home.name) }
    var noteRangeId by rememberSaveable {
        mutableStateOf(NoteTrainingRange.fromSettings(noteTrainingSession.currentSettings()).name)
    }
    var intervalLevelId by rememberSaveable {
        mutableStateOf(
            IntervalLevel.fromId(intervalTrainingSession.currentSettings().intervalLevelId).name,
        )
    }
    val destination = AppDestination.valueOf(destinationName)
    val noteRange = NoteTrainingRange.fromId(noteRangeId)
        ?: NoteTrainingRange.SINGLE_STRING_1
    val noteStateMachine = remember(noteTrainingSession) {
        TrainingStateMachine(noteTrainingSession)
    }
    val intervalStateMachine = remember(intervalTrainingSession) {
        TrainingStateMachine(intervalTrainingSession)
    }
    val intervalLevel = IntervalLevel.fromId(intervalLevelId)

    fun navigateTo(next: AppDestination) {
        destinationName = next.name
        onDestinationChanged(next)
    }

    LaunchedEffect(destination) {
        onDestinationChanged(destination)
    }

    BackHandler(enabled = destination != AppDestination.Home) {
        navigateTo(previousDestination(destination))
    }

    when (destination) {
        AppDestination.Home -> HomeScreen(
            onOpenNoteName = { navigateTo(AppDestination.NoteNameRange) },
            onOpenMapping = { navigateTo(AppDestination.CombinedMapping) },
            onOpenTheory = { navigateTo(AppDestination.BasicTheory) },
        )
        AppDestination.NoteNameRange -> NoteNameRangeScreen(
            selectedRange = noteRange,
            onBack = { navigateTo(AppDestination.Home) },
            onSelect = { selected ->
                noteRangeId = selected.name
                noteStateMachine.resetNoteTrainingRange(selected)
                navigateTo(AppDestination.NoteName)
            },
        )
        AppDestination.NoteName -> NoteNameTrainingScreen(
            trainingSession = noteTrainingSession,
            stateMachine = noteStateMachine,
            onBack = { navigateTo(AppDestination.NoteNameRange) },
        )
        AppDestination.CombinedMapping -> CombinedMappingTrainingScreen(
            onBack = { navigateTo(AppDestination.Home) },
        )
        AppDestination.BasicTheory -> BasicTheoryScreen(
            onBack = { navigateTo(AppDestination.Home) },
            onOpenIntervals = { navigateTo(AppDestination.IntervalLevels) },
        )
        AppDestination.IntervalLevels -> IntervalLevelScreen(
            selectedLevel = intervalLevel,
            onBack = { navigateTo(AppDestination.BasicTheory) },
            onSelect = { selected ->
                intervalLevelId = selected.name
                intervalStateMachine.resetRound(
                    intervalTrainingSession.currentSettings().copy(
                        intervalLevelId = selected.name,
                    ),
                )
                navigateTo(AppDestination.IntervalTraining)
            },
        )
        AppDestination.IntervalTraining -> IntervalTrainingScreen(
            trainingSession = intervalTrainingSession,
            stateMachine = intervalStateMachine,
            level = intervalLevel,
            onBack = { navigateTo(AppDestination.IntervalLevels) },
        )
    }
}

@Composable
private fun HomeScreen(
    onOpenNoteName: () -> Unit,
    onOpenMapping: () -> Unit,
    onOpenTheory: () -> Unit,
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
            verticalArrangement = Arrangement.Center,
        ) {
            PixelPanel(modifier = Modifier.fillMaxWidth(0.78f)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "ELECTRIC GUITAR",
                        color = PixelInkMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = "电吉他训练",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = "♪  ◆  ♫",
                        color = PixelGold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            PixelButton(
                text = "音名训练",
                onClick = onOpenNoteName,
                modifier = Modifier.fillMaxWidth(0.78f),
            )
            Spacer(modifier = Modifier.height(12.dp))
            PixelButton(
                text = "音名 / 唱名 / 级数",
                onClick = onOpenMapping,
                modifier = Modifier.fillMaxWidth(0.78f),
                style = PixelButtonStyle.Secondary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            PixelButton(
                text = "基础乐理",
                onClick = onOpenTheory,
                modifier = Modifier.fillMaxWidth(0.78f),
                style = PixelButtonStyle.Secondary,
            )
        }
    }
}

