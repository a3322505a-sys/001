package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.InMemoryTrainingStore
import com.a3322505a.guitarlearning.storage.Progress
import com.a3322505a.guitarlearning.storage.Settings
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
            engine = TrainingEngine(random = Random(9)),
            store = store,
            nowMs = { now },
            sessionId = "session-1",
        )

        assertEquals(1, store.loadSessions().size)
        assertNull(store.loadSessions().single().endedAt)
        val question = session.currentQuestion()
        now = 1_250L

        val first = session.submitAnswer(question.correctAnswerValue)
        val duplicate = session.submitAnswer(question.correctAnswerValue)
        val progress = store.loadProgress(question.knowledgeItemId)
        val item = store.findKnowledgeItem(question.knowledgeItemId)

        assertTrue(first.accepted)
        assertTrue(first.isCorrect)
        assertFalse(duplicate.accepted)
        assertNotNull(progress)
        assertEquals(1, progress.attempts)
        assertEquals(1, progress.correct)
        assertEquals(1, progress.streak)
        assertEquals(1_250L, progress.lastSeenAt)
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

    @Test
    fun wrongNoteNameAnswerRaisesOnlyThatPhysicalPositionsWeight() {
        val store = InMemoryTrainingStore()
        val session = TrainingSession(
            engine = TrainingEngine(
                settings = Settings(selectedStrings = setOf(1), fretStart = 0, fretEnd = 1),
                random = Random(12),
                progressProvider = { store.loadProgress() },
                enabledQuestionTypes = listOf(QuestionType.FretToNote),
            ),
            store = store,
            nowMs = { 1_000L },
            sessionId = "weighted-session",
        )
        val question = session.currentQuestion()

        val correct = question.correctAnswerValue as AnswerValue.FretPosition
        val wrong = AnswerValue.FretPosition(
            string = correct.string,
            fret = if (correct.fret == 12) 11 else correct.fret + 1,
        )
        session.submitAnswer(wrong)

        val updated = assertNotNull(store.loadProgress(question.knowledgeItemId))
        assertEquals(1, updated.attempts)
        assertEquals(1.6, updated.weight, absoluteTolerance = 0.000_001)
        val stats = session.currentStats()
        assertEquals(1, stats.errorCount)
        assertEquals(listOf(question.note), stats.mostMistakenNotes)
        assertEquals(wrong.string, stats.weakestLocations.single().string)
        assertEquals(fretBandFor(wrong.fret), stats.weakestLocations.single().fretRange)
        val otherId = if (question.knowledgeItemId.endsWith(":f0")) {
            "FretToNote:s1:f1"
        } else {
            "FretToNote:s1:f0"
        }
        assertNull(store.loadProgress(otherId))
    }

    @Test
    fun changingTrainingRangeDoesNotResetStoredPositionHistory() {
        val store = InMemoryTrainingStore()
        val history = Progress(
            knowledgeItemId = "FretToNote:s1:f3",
            attempts = 7,
            correct = 3,
            weight = 2.0,
        )
        store.saveProgress(history)
        val session = TrainingSession(
            engine = TrainingEngine(
                settings = NoteTrainingRange.LOW_POSITION.applyTo(Settings()),
                progressProvider = { store.loadProgress() },
                enabledQuestionTypes = listOf(QuestionType.FretToNote),
            ),
            store = store,
            nowMs = { 1_000L },
            sessionId = "range-session",
        )

        session.resetForSettings(NoteTrainingRange.MID_POSITION.applyTo(Settings()))

        assertEquals(history, store.loadProgress(history.knowledgeItemId))
        assertEquals(0, session.currentStats().answerCount)
        assertEquals(emptyList(), session.currentStats().mostMistakenNotes)
    }
}
