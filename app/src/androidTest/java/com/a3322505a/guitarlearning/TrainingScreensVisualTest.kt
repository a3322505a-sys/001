package com.a3322505a.guitarlearning

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test

class TrainingScreensVisualTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun noteNameScreenShowsCompleteSixStringLayout() {
        composeRule.onNodeWithText("音名训练").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            val bounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
            bounds.width > bounds.height
        }
        composeRule.waitForIdle()
        saveScreenshot("note-name-training.png")

        (1..6).forEach { string ->
            composeRule.onNodeWithText("${string}弦").assertIsDisplayed()
        }
        composeRule.onNodeWithText("正确 0").assertIsDisplayed()
        composeRule.onNodeWithText("错误 0").assertIsDisplayed()
        composeRule.onNodeWithText("C").assertIsDisplayed()
        composeRule.onNodeWithText("B").assertIsDisplayed()
        composeRule.onNodeWithText("平均反应时间", substring = true).assertDoesNotExist()
    }

    @Test
    fun solfeggioScreenUsesTheSameStatsThenAnswersStructure() {
        composeRule.onNodeWithText("唱名与音名").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            val bounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
            bounds.height > bounds.width
        }
        composeRule.waitForIdle()
        saveScreenshot("solfeggio-note-mapping.png")

        composeRule.onNodeWithText("正确 0").assertIsDisplayed()
        composeRule.onNodeWithText("错误 0").assertIsDisplayed()
        composeRule.onNodeWithText("平均反应时间", substring = true).assertDoesNotExist()
    }

    private fun saveScreenshot(filename: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(requireNotNull(context.getExternalFilesDir(null)), "screenshots")
        check(directory.exists() || directory.mkdirs())
        FileOutputStream(File(directory, filename)).use { output ->
            composeRule.onRoot().captureToImage().asAndroidBitmap().compress(
                Bitmap.CompressFormat.PNG,
                100,
                output,
            )
        }
    }
}
