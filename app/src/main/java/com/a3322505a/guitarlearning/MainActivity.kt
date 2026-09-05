package com.a3322505a.guitarlearning

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.a3322505a.guitarlearning.learning.LearningApp
import com.a3322505a.guitarlearning.learning.TrainingViewModel
import com.a3322505a.guitarlearning.ui.theme.GuitarLearningTheme

class MainActivity : ComponentActivity() {
    private val model: TrainingViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GuitarLearningTheme { LearningApp(model) } }
    }
    override fun onStop() {
        model.stopAudio()
        // Backgrounding pauses the persisted task; only the explicit End action closes a session.
        super.onStop()
    }
}
