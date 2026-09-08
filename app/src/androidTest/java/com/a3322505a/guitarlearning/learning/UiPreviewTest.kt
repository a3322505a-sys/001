package com.a3322505a.guitarlearning.learning

import android.graphics.Bitmap
import android.content.pm.ActivityInfo
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
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
            "audio-error" to TrainingUiState("preview", "听完参照后，判断两个音的关系", relation=RelationUiState(listOf("播放未完成时不能作答。"), "试听参照", true), audio=AudioUiState(message="声音播放失败，请重试。",failed=true)),
            "corrected" to TrainingUiState("preview", "纠正完成", message="已按提示完成，下一题将撤去辅助。", canNext=true),
            "symbol-only" to TrainingUiState("preview", "C 大调中，mi 对应哪个音名？", options=listOf("C", "D", "E", "F", "G", "A", "B").map { AnswerOptionUi(it) }),
            "symbol-feedback" to TrainingUiState("preview", "固定唱名与音名转换：选择对应关系", options=listOf("C 对应 do", "D 对应 re", "E 对应 mi").map { AnswerOptionUi(it) }, wrong=true, message="先看清题目的调性与方向，再选择对应关系。", audio=AudioUiState()),
            "chord-guided" to chord,
            "chord-notes" to TrainingUiAdapter.training(LearnerState(fingeringMode=FingeringMode.NOTES.id, active=ActiveTask(ChordLessons.make(ChordShapes.am,"chord-am",TaskSource.DEMONSTRATION))),false,AudioUiState()),
            "chord-vertical" to chord.copy(board=chord.board!!.copy(chordVertical=true)),
            "barre-horizontal" to TrainingUiAdapter.training(LearnerState(active=ActiveTask(ChordLessons.make(ChordShapes.fBarre,"chord-f",TaskSource.DEMONSTRATION))),false,AudioUiState()),
            "barre-vertical" to TrainingUiAdapter.training(LearnerState(chordVertical=true,active=ActiveTask(ChordLessons.make(ChordShapes.fBarre,"chord-f",TaskSource.DEMONSTRATION))),false,AudioUiState()),
            "chord-error" to chord.copy(wrong = true, message = "按亮起位置设置本弦。"),
            "tab-three-notes" to TrainingUiState("preview", "从左到右读 TAB 短句", board = board.copy(lastFret=4), notation=shortTab, wrong=true, message="按谱线找弦，按数字找品。"),
            "note-options" to TrainingUiState("preview", "", roundProgress="5/12", accessibilityPrompt="亮起的位置是什么音名？", board=board.copy(lastFret=4,marks=listOf(BoardMark(Coordinate(1,3),MarkRole.TARGET,"?"))), options=listOf("C","D","E","F","G","A","B").map { AnswerOptionUi(it) }),
            "recovery-recognition" to TrainingUiState("preview", "", accessibilityPrompt="亮起的位置是什么音名？", board=board.copy(lastFret=4,marks=listOf(BoardMark(Coordinate(4,2),MarkRole.TARGET,"?"))), options=NaturalRecognition.options.map { AnswerOptionUi(it) }),
            "correction-b3" to TrainingUiAdapter.training(LearnerState(introductions=setOf("position:s3:f0", "position:s3:f2"), active=ActiveTask(LessonScheduler().makePosition("p09",Coordinate(3,4),Direction.POSITION_TO_NOTE,TaskSource.MAIN),phase=Phase.CORRECTING,firstCorrect=false)),false,AudioUiState()),
            "recovery-find" to TrainingUiState("preview", "在第4弦的2–3品内找到 E", roundProgress="5/12", board=board.copy(lastFret=4,marks=emptyList(),answerPositions=setOf(Coordinate(4,2),Coordinate(4,3)))),
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
            for (wide in listOf(false, true)) {
            if (wide) {
                instrumentation.uiAutomation.executeShellCommand("wm size 390x840").close()
                Thread.sleep(800)
            }
            for(fontScale in listOf(1f, 1.3f, 2f)) for(theme in listOf("forest","midnight")) for((name,state) in states) {
                if (wide && (theme != "forest" || fontScale > 1f || name !in listOf("note-options", "correction-b3", "chord-guided"))) continue
                if (fontScale > 1f && (theme != "forest" || name !in listOf("symbol-only", "symbol-feedback", "chord-error", "chord-vertical", "barre-vertical", "barre-horizontal", "tab-three-notes", "pilot-tab", "note-options", "mixed-options", "mixed-error", "recovery-recognition", "recovery-find", "correction-b3"))) continue
                scenario.onActivity { activity -> activity.setContent { SideEffect { activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE; activity.setTrainingImmersive(true) }; CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) { GuitarLearningTheme(theme) { Surface(Modifier.fillMaxSize()) { key(name, fontScale, theme, wide) { TrainingScreen(state){} } } } } } }
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
                directory.resolve("$theme-$name${if (wide) "-wide" else ""}${if (fontScale == 2f) "-largest" else if (fontScale > 1f) "-large" else ""}.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                bitmap.recycle()
                if (fontScale == 2f) {
                    fun firstScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                        if (node == null) return null
                        if (node.isScrollable) return node
                        for (i in 0 until node.childCount) firstScrollable(node.getChild(i))?.let { return it }
                        return null
                    }
                    val scroll = firstScrollable(instrumentation.uiAutomation.rootInActiveWindow)
                    if (scroll != null) {
                        repeat(4) { scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD); Thread.sleep(150) }
                        instrumentation.waitForIdleSync()
                        val end = instrumentation.uiAutomation.takeScreenshot()
                        directory.resolve("$theme-$name-largest-scrolled.png").outputStream().use { end.compress(Bitmap.CompressFormat.PNG,100,it) }
                        end.recycle()
                    }
                }
            }
            }
            instrumentation.uiAutomation.executeShellCommand("wm size reset").close()
            val learner = LearnerState()
            val pages: Map<String, @Composable () -> Unit> = mapOf(
                "home" to { HomeContent(LearningPageAdapter.home(learner), {}, {}, {}) },
                "catalog" to { CatalogContent(LearningPageAdapter.catalog(learner, HomeGroup.INTRO.categories, false), {}, {}, {}, {}) },
                "tree" to { TreeContent(LearningPageAdapter.tree(learner), {}, {}) },
                "node-locked" to { NodeContent(LearningPageAdapter.node(learner, Curriculum.node("chord-f")), {}, {}) { _, _ -> } },
                "history-empty" to { HistoryContent(LearningPageAdapter.history(learner), {}) },
                "settings" to { SettingsContent(LearningPageAdapter.settings(learner, false, null), {}, {}, {}, {}, {}) },
                "pilot-menu" to { PilotMenu(LearningPageAdapter.pilot(learner), {}) },
            )
            for (fontScale in listOf(1f, 2f)) for ((name, page) in pages) {
                scenario.onActivity { activity -> activity.setContent {
                    SideEffect { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT; activity.setTrainingImmersive(false) }
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                        GuitarLearningTheme("forest") { Surface(Modifier.fillMaxSize()) {
                            key(name, fontScale) { LearningPageFrame(name, "‹ 返回", false, {}, null) { LearningPageBody { page() } } }
                        } }
                    }
                } }
                instrumentation.waitForIdleSync()
                Thread.sleep(1000)
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                directory.resolve("page-$name-${fontScale}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }
}
