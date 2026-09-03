package com.a3322505a.guitarlearning

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.a3322505a.guitarlearning.storage.PersistentTrainingStore
import com.a3322505a.guitarlearning.training.IntervalModule
import com.a3322505a.guitarlearning.training.FirstFretboardModule
import com.a3322505a.guitarlearning.training.NoteTrainingRange
import com.a3322505a.guitarlearning.training.TrainingEngine
import com.a3322505a.guitarlearning.training.TrainingSession
import com.a3322505a.guitarlearning.ui.theme.GuitarLearningTheme

class MainActivity : ComponentActivity() {
    private lateinit var noteTrainingSession: TrainingSession
    private lateinit var intervalTrainingSession: TrainingSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val store = PersistentTrainingStore(applicationContext)
        val storedSettings = store.loadSettings()
        val settings = NoteTrainingRange.normalize(storedSettings)
        if (settings != storedSettings) store.saveSettings(settings)
        noteTrainingSession = TrainingSession(
            engine = TrainingEngine(
                settings = settings,
                progressProvider = { store.loadProgress() },
                module = FirstFretboardModule(),
            ),
            store = store,
        )
        intervalTrainingSession = TrainingSession(
            engine = TrainingEngine(
                settings = settings,
                progressProvider = { store.loadProgress() },
                module = IntervalModule(),
            ),
            store = store,
        )

        setContent {
            GuitarLearningTheme {
                GuitarLearningApp(
                    noteTrainingSession = noteTrainingSession,
                    intervalTrainingSession = intervalTrainingSession,
                    onDestinationChanged = { destination ->
                        requestedOrientation = if (usesLandscapeLayout(destination)) {
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                    },
                )
            }
        }
    }

    override fun onStop() {
        if (::noteTrainingSession.isInitialized) noteTrainingSession.finish()
        if (::intervalTrainingSession.isInitialized) intervalTrainingSession.finish()
        super.onStop()
    }
}
