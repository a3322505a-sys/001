package com.a3322505a.guitarlearning

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.a3322505a.guitarlearning.learning.LearningApp
import com.a3322505a.guitarlearning.learning.TrainingViewModel
import com.a3322505a.guitarlearning.ui.theme.GuitarLearningTheme
import com.a3322505a.guitarlearning.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    private val model: TrainingViewModel by viewModels()
    private var trainingImmersive = false
    private var darkTheme = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applySystemBars()
        setContent {
            val state by model.state.collectAsState()
            val theme = AppTheme.fromId(state?.themeId)
            SideEffect { setDarkTheme(theme.dark) }
            GuitarLearningTheme(theme.id) { LearningApp(model) }
        }
    }
    private fun setDarkTheme(enabled: Boolean) {
        if (darkTheme == enabled) return
        darkTheme = enabled
        applySystemBars()
    }
    fun setTrainingImmersive(enabled: Boolean) {
        trainingImmersive = enabled
        applySystemBars()
    }
    private fun applySystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
            if (trainingImmersive) hide(WindowInsetsCompat.Type.systemBars())
            else show(WindowInsetsCompat.Type.systemBars())
        }
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Re-enter after app switching or a dialog; edge swipes can still reveal bars temporarily.
        if (hasFocus && trainingImmersive) applySystemBars()
    }
    override fun onStart() { super.onStart(); model.foreground(true) }
    override fun onStop() {
        model.foreground(false)
        // Backgrounding pauses the persisted task; only the explicit End action closes a session.
        super.onStop()
    }
}
