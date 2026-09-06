package com.a3322505a.guitarlearning.learning

import android.graphics.Bitmap
import android.content.pm.ActivityInfo
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.SideEffect
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
        val chord = TrainingUiAdapter.training(LearnerState(active = ActiveTask(ChordLessons.make(ChordShapes.get("am-open"), "chord-am", TaskSource.DEMONSTRATION))), false, AudioUiState())
        val shortTab = NotationPrompt(NotationKind.TAB, listOf(59,62,67), listOf(Coordinate(2,0),Coordinate(2,3),Coordinate(1,3)))
        val states=listOf(
            "chord-guided" to chord,
            "chord-error" to chord.copy(wrong = true, message = "按当前指定弦设置位置、空弦或不弹：6弦 X（不弹）；5弦空弦 A2；4弦2品 E3；3弦2品 A3；2弦1品 C4；1弦空弦 E4。"),
            "tab-three-notes" to TrainingUiState("preview", "从左到右读 TAB 短句", board = board.copy(lastFret=4), notation=shortTab, wrong=true, message="再看一次：从左到右，第2弦空弦 → 第2弦3品 → 第1弦3品。线表示弦，数字表示品，0表示空弦；跟着当前指示点，逐项点指定位置。"),
            "note-options" to TrainingUiState("preview", "这里是什么音？", board=board.copy(lastFret=4), options=listOf("C","D","E","F","G","A","B").map { AnswerOptionUi(it) }),
            "middle-teaching" to TrainingUiState("preview","找到 A",board=board,message="第1弦：G（3品）→ A（5品）；相隔两品、一个全音。"),
            "full-independent" to TrainingUiState("preview","找到 E4",board=board.copy(lastFret=12,marks=emptyList())),
            "pilot-tab" to TrainingUiState("preview","短谱练习 · 5/8",board=board.copy(lastFret=4,marks=emptyList()),notation=score.notation(NotationKind.TAB),pilot=PilotControlsUi(PilotMode.SLOW,50,false,true,false,false,false,false)),
            "pilot-staff" to TrainingUiState("preview","短谱练习 · 6/8",notation=score.notation(NotationKind.STAFF),pilot=PilotControlsUi(PilotMode.GUITAR,50,false,true,false,false,false,false))
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            Thread.sleep(800)
            for(theme in listOf("forest","midnight")) for((name,state) in states) {
                scenario.onActivity { activity -> activity.setContent { SideEffect { activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE; activity.setTrainingImmersive(true) }; GuitarLearningTheme(theme) { Surface(Modifier.fillMaxSize()) { TrainingScreen(state){} } } } }
                instrumentation.waitForIdleSync()
                Thread.sleep(1000)
                instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Got it")?.forEach { it.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                instrumentation.waitForIdleSync()
                Thread.sleep(250)
                val bitmap=instrumentation.uiAutomation.takeScreenshot()
                check(bitmap.width > bitmap.height) { "Training preview must be landscape" }
                directory.resolve("$theme-$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                bitmap.recycle()
            }
        }
    }
}
