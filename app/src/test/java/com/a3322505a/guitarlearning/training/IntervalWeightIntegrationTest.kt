package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.InMemoryTrainingStore
import com.a3322505a.guitarlearning.storage.Progress
import com.a3322505a.guitarlearning.storage.Settings
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IntervalWeightIntegrationTest {
    @Test
    fun weakConcretePairIsSampledMoreOftenAndMasteredPairStillAppears() {
        val highId = "interval:identify:D:F"
        val lowId = "interval:identify:C:E"
        val progress = listOf(
            Progress(highId, weight = 2.0),
            Progress(lowId, weight = 0.4),
        )
        val engine = TrainingEngine(
            settings = Settings(intervalLevelId = IntervalLevel.LV2.name),
            random = Random(91),
            progressProvider = { progress },
            module = IntervalModule(),
        )
        val counts = mutableMapOf(highId to 0, lowId to 0)

        repeat(40_000) {
            val id = engine.generateQuestion().knowledgeItemId
            if (id in counts) counts[id] = counts.getValue(id) + 1
        }

        assertTrue(counts.getValue(highId) > counts.getValue(lowId) * 3, counts.toString())
        assertTrue(counts.getValue(lowId) > 0, counts.toString())
    }

    @Test
    fun wrongAnswerUpdatesOnlyThatIntervalPairWithTheExistingBoundedRule() {
        val store = InMemoryTrainingStore()
        val session = TrainingSession(
            engine = TrainingEngine(
                settings = Settings(intervalLevelId = IntervalLevel.LV0.name),
                random = Random(4),
                progressProvider = { store.loadProgress() },
                module = IntervalModule(),
            ),
            store = store,
            nowMs = { 1_000L },
            sessionId = "interval-session",
        )
        val question = session.currentQuestion()

        session.submitAnswer(question.choices.first { it != question.correctAnswer })

        val updated = assertNotNull(store.loadProgress(question.knowledgeItemId))
        assertEquals(1.6, updated.weight, absoluteTolerance = 0.000_001)
        assertEquals(1, store.loadProgress().size)
        assertEquals(TrainingModuleIds.INTERVAL, store.findKnowledgeItem(question.knowledgeItemId)?.moduleId)
    }
}
