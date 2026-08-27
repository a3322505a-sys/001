package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.MasteryStatus
import com.a3322505a.guitarlearning.storage.Progress
import com.a3322505a.guitarlearning.storage.Settings
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class WeightedSamplingTest {
    @Test
    fun weakItemAppearsMoreOftenThanOrdinaryAndStableItems() {
        val weakId = "FretToNote:s6:f0"
        val ordinaryId = "FretToNote:s6:f1"
        val stableId = "FretToNote:s6:f3"
        val progress = listOf(
            Progress(
                knowledgeItemId = weakId,
                mastery = MasteryStatus.LEARNING,
                lastResponseMs = 2_501L,
                recentResults = listOf(false),
            ),
            Progress(
                knowledgeItemId = ordinaryId,
                mastery = MasteryStatus.BASIC_MASTERY,
                lastResponseMs = 500L,
                recentResults = listOf(true),
            ),
            Progress(
                knowledgeItemId = stableId,
                mastery = MasteryStatus.STABLE_MASTERY,
                lastResponseMs = 500L,
                recentResults = listOf(true),
                seenDays = setOf("2026-08-26", "2026-08-27"),
            ),
        )
        val engine = TrainingEngine(
            settings = Settings(selectedStrings = setOf(6), fretStart = 0, fretEnd = 3),
            random = Random(41),
            progressProvider = { progress },
        )
        val counts = mutableMapOf(weakId to 0, ordinaryId to 0, stableId to 0)

        repeat(5_000) {
            val question = engine.generateQuestion(QuestionType.FretToNote)
            counts[question.knowledgeItemId] = counts.getValue(question.knowledgeItemId) + 1
        }

        assertTrue(counts.getValue(weakId) > counts.getValue(ordinaryId), counts.toString())
        assertTrue(counts.getValue(ordinaryId) > counts.getValue(stableId), counts.toString())
    }
}
