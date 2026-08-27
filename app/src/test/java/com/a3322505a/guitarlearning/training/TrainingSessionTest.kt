package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.InMemoryTrainingStore
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrainingSessionTest {
    @Test
    fun trainingAnswerUpdatesItemProgressAndSessionOnce() {
        var now = 1_000L
        val store = InMemoryTrainingStore()
        val session = TrainingSession(
            engine = TrainingEngine(random = Random(9), clockMs = { now }),
            store = store,
            clockMs = { now },
            sessionId = "session-1",
        )

        assertEquals(1, store.loadSessions().size)
        assertNull(store.loadSessions().single().endedAt)
        val question = session.currentQuestion()
        now = 1_250L

        val first = session.submitAnswer(question.correctAnswer)
        val duplicate = session.submitAnswer(question.correctAnswer)
        val progress = store.loadProgress(question.knowledgeItemId)
        val item = store.findKnowledgeItem(question.knowledgeItemId)

        assertTrue(first.accepted)
        assertTrue(first.isCorrect)
        assertFalse(duplicate.accepted)
        assertNotNull(progress)
        assertEquals(1, progress.attempts)
        assertEquals(1, progress.correct)
        assertEquals(1, progress.streak)
        assertEquals(250.0, progress.avgResponseMs)
        assertNotNull(item)
        assertEquals(question.knowledgeItemId, item.id)
        assertEquals(1, store.loadKnowledgeItems().size)
        assertEquals(1, session.currentSession.questionCount)
        assertEquals(1, session.currentSession.correctCount)

        now = 2_000L
        val finished = session.finish()
        assertEquals(2_000L, finished.endedAt)
        assertEquals(finished, store.loadSessions().single())
    }
}
