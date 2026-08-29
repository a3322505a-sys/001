package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.core.GuitarCore
import com.a3322505a.guitarlearning.storage.InMemoryTrainingStore
import com.a3322505a.guitarlearning.training.TrainingEngine
import com.a3322505a.guitarlearning.training.TrainingSession
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Final V0.2.1 regression checks; this test adds no product behavior. */
class V02ReleaseCandidateTest {
    @Test
    fun allSeventyEightCorePositionsRemainValid() {
        val positions = GuitarCore.allPositions()

        assertEquals(6 * 13, positions.size)
        positions.forEach { position ->
            assertEquals(position.note, GuitarCore.getNote(position.string, position.fret))
            assertEquals(GuitarCore.solfegeFor(position.note), position.solfege)
        }
        assertEquals("E", GuitarCore.getNote(6, 0))
        assertEquals("A", GuitarCore.getNote(6, 5))
        assertEquals("A", GuitarCore.getNote(5, 12))
        assertEquals("D", GuitarCore.getNote(3, 7))
        assertEquals("E", GuitarCore.getNote(1, 12))
    }

    @Test
    fun oneHundredPersistedSessionAnswersRemainConsistent() {
        var now = 100_000L
        val store = InMemoryTrainingStore()
        val engine = TrainingEngine(
            random = Random(101),
            progressProvider = { store.loadProgress() },
        )
        val session = TrainingSession(
            engine = engine,
            store = store,
            nowMs = { now },
            sessionId = "release-session",
        )
        var question = session.currentQuestion()

        repeat(100) { index ->
            now += 50L + index
            val answer = if (index % 3 == 0) {
                question.choices.first { it != question.correctAnswer }
            } else {
                question.correctAnswer
            }
            val result = session.submitAnswer(answer)
            val duplicate = session.submitAnswer(question.correctAnswer)

            assertTrue(result.accepted)
            assertFalse(duplicate.accepted)
            assertEquals(question.knowledgeItemId, result.knowledgeItemId)
            assertNotNull(store.loadProgress(question.knowledgeItemId))
            if (index < 99) question = session.nextQuestion()
        }

        val completed = session.finish()
        assertEquals(100, completed.questionCount)
        assertEquals(66, completed.correctCount)
        assertEquals(100, store.loadProgress().sumOf { it.attempts })
        assertEquals(completed, store.loadSessions().single())
    }
}
