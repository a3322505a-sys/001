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
import com.a3322505a.guitarlearning.audio.PitchPlayer
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
    ReadingMenu,
    TabReading,
    StaffReading,
}

fun usesLandscapeLayout(destination: AppDestination): Boolean =
    destination == AppDestination.NoteName

fun usesImmersiveSystemBars(destination: AppDestination): Boolean =
    destination == AppDestination.NoteName

fun previousDestination(destination: AppDestination): AppDestination = when (destination) {
    AppDestination.NoteName -> AppDestination.NoteNameRange
    AppDestination.TabReading,
    AppDestination.StaffReading -> AppDestination.ReadingMenu
    AppDestination.NoteNameRange,
    AppDestination.CombinedMapping,
    AppDestination.ReadingMenu -> AppDestination.Home
    AppDestination.Home -> AppDestination.Home
}

@Composable
fun GuitarLearningApp(
    noteTrainingSession: TrainingSession,
    pitchPlayer: PitchPlayer,
    onDestinationChanged: (AppDestination) -> Unit,
) {
    var destinationName by rememberSaveable { mutableStateOf(AppDestination.Home.name) }
    var noteRangeId by rememberSaveable {
        mutableStateOf(NoteTrainingRange.fromSettings(noteTrainingSession.currentSettings()).name)
    }
    val destination = AppDestination.valueOf(destinationName)
    val noteRange = NoteTrainingRange.fromId(noteRangeId)
        ?: NoteTrainingRange.LOW_POSITION
    val noteStateMachine = remember(noteTrainingSession) {
        TrainingStateMachine(noteTrainingSession)
    }

    fun navigateTo(next: AppDestination) {
        pitchPlayer.stop()
        destinationName = next.name
        onDestinationChanged(next)
    }

    LaunchedEffect(destination) {
        onDestinationChanged(destination)
    }

    BackHandler(
        enabled = destination != AppDestination.Home && destination != AppDestination.NoteName,
    ) {
        navigateTo(previousDestination(destination))
    }

    when (destination) {
        AppDestination.Home -> HomeScreen(
            onOpenNoteName = { navigateTo(AppDestination.NoteNameRange) },
            onOpenMapping = { navigateTo(AppDestination.CombinedMapping) },
            onOpenReading = { navigateTo(AppDestination.ReadingMenu) },
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
            pitchPlayer = pitchPlayer,
            onBack = { navigateTo(AppDestination.NoteNameRange) },
        )
        AppDestination.CombinedMapping -> CombinedMappingTrainingScreen(
            pitchPlayer = pitchPlayer,
            onBack = { navigateTo(AppDestination.Home) },
        )
        AppDestination.ReadingMenu -> ReadingTrainingMenuScreen(
            onOpenTab = { navigateTo(AppDestination.TabReading) },
            onOpenStaff = { navigateTo(AppDestination.StaffReading) },
            onBack = { navigateTo(AppDestination.Home) },
        )
        AppDestination.TabReading -> ReadingTrainingScreen(
            notation = ReadingNotation.Tab,
            pitchPlayer = pitchPlayer,
            tabGuideCompleted = noteTrainingSession.currentSettings().tabIntroductionCompleted,
            onTabGuideCompleted = noteTrainingSession::completeTabIntroduction,
            onBack = { navigateTo(AppDestination.ReadingMenu) },
        )
        AppDestination.StaffReading -> ReadingTrainingScreen(
            notation = ReadingNotation.Staff,
            pitchPlayer = pitchPlayer,
            onBack = { navigateTo(AppDestination.ReadingMenu) },
        )
    }
}

@Composable
private fun HomeScreen(
    onOpenNoteName: () -> Unit,
    onOpenMapping: () -> Unit,
    onOpenReading: () -> Unit,
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
                text = "指板训练",
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
                text = "读谱训练",
                onClick = onOpenReading,
                modifier = Modifier.fillMaxWidth(0.78f),
                style = PixelButtonStyle.Secondary,
            )
        }
    }
}
