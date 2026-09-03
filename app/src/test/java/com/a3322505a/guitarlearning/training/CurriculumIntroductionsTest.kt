package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.InMemoryTrainingStore
import com.a3322505a.guitarlearning.storage.Settings
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CurriculumIntroductionsTest {
    @Test
    fun integratedTheoryIntroductionIsShortAndConsumedOnlyOnce() {
        val settings = NoteTrainingRange.LOW_POSITION.applyTo(
            Settings(
                unlockedFretboardLevel = 3,
                firstPositionBaselineComplete = true,
                firstPositionActiveKnowledgeIds = FirstPositionCurriculum.expansionPositions
                    .map(FirstPositionCurriculum::id).toSet(),
                firstPositionComplete = true,
            ),
        )
        val store = InMemoryTrainingStore().also { it.saveSettings(settings) }
        val session = TrainingSession(
            engine = TrainingEngine(
                settings = settings,
                random = Random(3),
                progressProvider = { store.loadProgress() },
                module = FirstFretboardModule(),
            ),
            store = store,
        )
        val question = FirstFretboardModule().buildQuestionBank(settings)
            .first { it.kind == "half_step" }

        assertEquals("相邻 1 品 = 半音", session.consumeIntroduction(question))
        assertNull(session.consumeIntroduction(question))
        assertEquals(setOf("half_step"), store.loadSettings().seenIntroductionIds)
    }
}
