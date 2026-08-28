package com.a3322505a.guitarlearning

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.a3322505a.guitarlearning.training.TrainingSession

enum class AppDestination {
    Home,
    NoteName,
    SolfeggioNoteMapping,
}

fun usesLandscapeLayout(destination: AppDestination): Boolean =
    destination == AppDestination.NoteName

@Composable
fun GuitarLearningApp(
    noteTrainingSession: TrainingSession,
    mappingTrainingSession: TrainingSession,
    onDestinationChanged: (AppDestination) -> Unit,
) {
    var destinationName by rememberSaveable { mutableStateOf(AppDestination.Home.name) }
    val destination = AppDestination.valueOf(destinationName)

    fun navigateTo(next: AppDestination) {
        destinationName = next.name
        onDestinationChanged(next)
    }

    LaunchedEffect(destination) {
        onDestinationChanged(destination)
    }

    BackHandler(enabled = destination != AppDestination.Home) {
        navigateTo(AppDestination.Home)
    }

    when (destination) {
        AppDestination.Home -> HomeScreen(
            onOpenNoteName = { navigateTo(AppDestination.NoteName) },
            onOpenMapping = { navigateTo(AppDestination.SolfeggioNoteMapping) },
        )
        AppDestination.NoteName -> NoteNameTrainingScreen(
            trainingSession = noteTrainingSession,
            onBack = { navigateTo(AppDestination.Home) },
        )
        AppDestination.SolfeggioNoteMapping -> SolfeggioNoteMappingScreen(
            trainingSession = mappingTrainingSession,
            onBack = { navigateTo(AppDestination.Home) },
        )
    }
}

@Composable
private fun HomeScreen(
    onOpenNoteName: () -> Unit,
    onOpenMapping: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "电吉他训练",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onOpenNoteName,
                modifier = Modifier.fillMaxWidth(0.78f),
            ) {
                Text(text = "音名训练")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onOpenMapping,
                modifier = Modifier.fillMaxWidth(0.78f),
            ) {
                Text(text = "音名 / 唱名 / 级数")
            }
        }
    }
}
