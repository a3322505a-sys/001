package com.a3322505a.guitarlearning

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.a3322505a.guitarlearning.audio.AndroidPitchPlayer
import com.a3322505a.guitarlearning.audio.PitchPlayer
import com.a3322505a.guitarlearning.storage.PersistentTrainingStore
import com.a3322505a.guitarlearning.training.FirstFretboardModule
import com.a3322505a.guitarlearning.training.NoteTrainingRange
import com.a3322505a.guitarlearning.training.TrainingEngine
import com.a3322505a.guitarlearning.training.TrainingSession
import com.a3322505a.guitarlearning.ui.theme.GuitarLearningTheme

class MainActivity : ComponentActivity() {
    private lateinit var noteTrainingSession: TrainingSession
    private lateinit var pitchPlayer: PitchPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        pitchPlayer = AndroidPitchPlayer()

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
        setContent {
            GuitarLearningTheme {
                GuitarLearningApp(
                    noteTrainingSession = noteTrainingSession,
                    pitchPlayer = pitchPlayer,
                    onDestinationChanged = { destination ->
                        setNoteNameImmersive(usesImmersiveSystemBars(destination))
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

    private fun setNoteNameImmersive(enabled: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, !enabled)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            if (enabled) {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            } else {
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    override fun onStop() {
        if (::pitchPlayer.isInitialized) pitchPlayer.stop()
        if (::noteTrainingSession.isInitialized) noteTrainingSession.finish()
        super.onStop()
    }

    override fun onDestroy() {
        if (::pitchPlayer.isInitialized) pitchPlayer.release()
        super.onDestroy()
    }
}
