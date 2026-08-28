package com.a3322505a.guitarlearning

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.a3322505a.guitarlearning.storage.PersistentTrainingStore
import com.a3322505a.guitarlearning.training.QuestionType
import com.a3322505a.guitarlearning.training.TrainingEngine
import com.a3322505a.guitarlearning.training.TrainingSession
import com.a3322505a.guitarlearning.ui.theme.GuitarLearningTheme

class MainActivity : ComponentActivity() {
    private lateinit var noteTrainingSession: TrainingSession
    private lateinit var mappingTrainingSession: TrainingSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val store = PersistentTrainingStore(applicationContext)
        noteTrainingSession = TrainingSession(
            engine = TrainingEngine(
                settings = store.loadSettings(),
                progressProvider = { store.loadProgress() },
                enabledQuestionTypes = listOf(QuestionType.FretToNote),
            ),
            store = store,
        )
        mappingTrainingSession = TrainingSession(
            engine = TrainingEngine(
                settings = store.loadSettings(),
                progressProvider = { store.loadProgress() },
                enabledQuestionTypes = listOf(
                    QuestionType.NoteToSolfege,
                    QuestionType.SolfegeToNote,
                    QuestionType.NoteToDegree,
                    QuestionType.DegreeToNote,
                    QuestionType.SolfegeToDegree,
                    QuestionType.DegreeToSolfege,
                ),
            ),
            store = store,
        )

        setContent {
            GuitarLearningTheme {
                GuitarLearningApp(
                    noteTrainingSession = noteTrainingSession,
                    mappingTrainingSession = mappingTrainingSession,
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
        if (::mappingTrainingSession.isInitialized) mappingTrainingSession.finish()
        super.onStop()
    }
}
