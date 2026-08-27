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
    private lateinit var trainingSession: TrainingSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        val store = PersistentTrainingStore(applicationContext)
        trainingSession = TrainingSession(
            engine = TrainingEngine(
                settings = store.loadSettings(),
                progressProvider = { store.loadProgress() },
                enabledQuestionTypes = listOf(QuestionType.FretToNote),
            ),
            store = store,
        )
        setContent {
            GuitarLearningTheme {
                NoteNameTrainingScreen(trainingSession)
            }
        }
    }

    override fun onStop() {
        if (::trainingSession.isInitialized) trainingSession.finish()
        super.onStop()
    }
}
