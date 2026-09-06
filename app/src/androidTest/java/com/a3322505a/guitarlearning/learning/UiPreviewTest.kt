package com.a3322505a.guitarlearning.learning

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.a3322505a.guitarlearning.MainActivity
import com.a3322505a.guitarlearning.ui.theme.GuitarLearningTheme
import org.junit.Test

/** Manual-review images from fixed display contracts on the existing upgrade emulator. */
class UiPreviewTest {
    @Test fun captureContracts() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val directory=instrumentation.targetContext.getExternalFilesDir(null)!!.resolve("previews").apply{mkdirs()}
        val board=FretboardUiState("preview",lastFret=8,marks=listOf(BoardMark(Coordinate(1,3),MarkRole.REFERENCE,"G"),BoardMark(Coordinate(1,5),MarkRole.TARGET,"A")))
        val score=ShortScorePilot.score(4,listOf(Coordinate(1,0),Coordinate(1,1),Coordinate(1,3)))
        val states=listOf(
            "middle-teaching" to TrainingUiState("preview","找到 A",board=board,message="第1弦：G（3品）→ A（5品）；相隔两品、一个全音。"),
            "full-independent" to TrainingUiState("preview","找到 E4",board=board.copy(lastFret=12,marks=emptyList())),
            "pilot-tab" to TrainingUiState("preview","短谱练习 · 5/8",board=board.copy(lastFret=4,marks=emptyList()),notation=score.notation(NotationKind.TAB),pilot=PilotControlsUi(PilotMode.SLOW,50,false,true,false,false,false,false)),
            "pilot-staff" to TrainingUiState("preview","短谱练习 · 6/8",notation=score.notation(NotationKind.STAFF),pilot=PilotControlsUi(PilotMode.GUITAR,50,false,true,false,false,false,false))
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for(theme in listOf("clear","midnight")) for((name,state) in states) {
                scenario.onActivity { activity -> activity.setContent { GuitarLearningTheme(theme) { Surface(Modifier.fillMaxSize()) { TrainingScreen(state){} } } } }
                instrumentation.waitForIdleSync()
                Thread.sleep(600)
                val bitmap=instrumentation.uiAutomation.takeScreenshot()
                directory.resolve("$theme-$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                bitmap.recycle()
            }
        }
    }
}
