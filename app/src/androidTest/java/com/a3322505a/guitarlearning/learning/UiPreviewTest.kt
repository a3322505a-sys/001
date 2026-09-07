package com.a3322505a.guitarlearning.learning

import android.graphics.Bitmap
import android.content.pm.ActivityInfo
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
            "chord-error" to chord.copy(wrong = true, message = "按亮起位置设置本弦。"),
            "tab-three-notes" to TrainingUiState("preview", "从左到右读 TAB 短句", board = board.copy(lastFret=4), notation=shortTab, wrong=true, message="按谱线找弦，按数字找品。"),
            "note-options" to TrainingUiState("preview", "", accessibilityPrompt="亮起的位置是什么音名？", board=board.copy(lastFret=4,marks=listOf(BoardMark(Coordinate(1,3),MarkRole.TARGET,"?"))), options=listOf("C","D","E","F","G","A","B").map { AnswerOptionUi(it) }),
            "recovery-recognition" to TrainingUiState("preview", "", accessibilityPrompt="亮起的位置是什么音名？", board=board.copy(lastFret=4,marks=listOf(BoardMark(Coordinate(4,2),MarkRole.REFERENCE,"?"))), options=listOf("D","E").map { AnswerOptionUi(it) }),
            "correction-b3" to TrainingUiAdapter.training(LearnerState(introductions=setOf("position:s3:f0", "position:s3:f2"), active=ActiveTask(LessonScheduler().makePosition("p09",Coordinate(3,4),Direction.POSITION_TO_NOTE,TaskSource.MAIN),phase=Phase.CORRECTING,firstCorrect=false)),false,AudioUiState()),
            "recovery-find" to TrainingUiState("preview", "在第4弦的2–3品内找到 E", board=board.copy(lastFret=4,marks=emptyList(),answerPositions=setOf(Coordinate(4,2),Coordinate(4,3)))),
            "mixed-options" to TrainingUiState("preview", "C 大调", board=board.copy(lastFret=4,marks=listOf(BoardMark(Coordinate(6,0),MarkRole.TARGET,"?"))), options=listOf("1","re","E","4","sol","6","B").map { AnswerOptionUi(it) }),
            "mixed-error" to TrainingUiState("preview", "C 大调", board=board.copy(lastFret=4,marks=listOf(BoardMark(Coordinate(6,0),MarkRole.TARGET,"E"))), options=listOf("1","re","E","4","sol","6","B").map { AnswerOptionUi(it, role=if(it=="sol") MarkRole.WRONG else if(it=="E") MarkRole.TARGET else MarkRole.REFERENCE) }, wrong=true, message="第6弦空弦是 E；先巩固这几个音。"),
            "middle-teaching" to TrainingUiState("preview","找到 A",board=board,message="第1弦：G（3品）→ A（5品）；相隔两品、一个全音。"),
            "full-independent" to TrainingUiState("preview","找到 E4",board=board.copy(lastFret=12,marks=emptyList())),
            "pilot-tab" to TrainingUiState("preview","短谱练习 · 5/8",board=board.copy(lastFret=4,marks=emptyList()),notation=score.notation(NotationKind.TAB),pilot=PilotControlsUi(PilotMode.SLOW,50,false,true,false,false,false,false)),
            "pilot-staff" to TrainingUiState("preview","短谱练习 · 6/8",notation=score.notation(NotationKind.STAFF),pilot=PilotControlsUi(PilotMode.GUITAR,50,false,true,false,false,false,false))
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            Thread.sleep(800)
            for(fontScale in listOf(1f, 1.3f)) for(theme in listOf("forest","midnight")) for((name,state) in states) {
                if (fontScale > 1f && (theme != "forest" || name !in listOf("chord-error", "tab-three-notes", "pilot-tab", "note-options", "mixed-options", "mixed-error", "recovery-recognition", "recovery-find", "correction-b3"))) continue
                scenario.onActivity { activity -> activity.setContent { SideEffect { activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE; activity.setTrainingImmersive(true) }; CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) { GuitarLearningTheme(theme) { Surface(Modifier.fillMaxSize()) { TrainingScreen(state){} } } } } }
                instrumentation.waitForIdleSync()
                Thread.sleep(1000)
                instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Got it")?.forEach { it.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                instrumentation.waitForIdleSync()
                Thread.sleep(250)
                // The emulator launcher occasionally ANRs while the app is healthy.
                // Dismiss only that named system dialog, never an ANR belonging to this app.
                val window = instrumentation.uiAutomation.rootInActiveWindow
                if (window?.findAccessibilityNodeInfosByText("Pixel Launcher isn't responding")?.isNotEmpty() == true) {
                    window.findAccessibilityNodeInfosByText("Close app").forEach { it.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                    instrumentation.waitForIdleSync()
                    Thread.sleep(300)
                }
                val bitmap=instrumentation.uiAutomation.takeScreenshot()
                check(bitmap.width > bitmap.height) { "Training preview must be landscape" }
                directory.resolve("$theme-$name${if (fontScale > 1f) "-large" else ""}.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                bitmap.recycle()
            }
        }
    }
}
