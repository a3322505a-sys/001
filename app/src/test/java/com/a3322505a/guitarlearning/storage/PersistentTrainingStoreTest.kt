package com.a3322505a.guitarlearning.storage

import com.a3322505a.guitarlearning.training.QuestionType
import kotlin.test.Test
import kotlin.test.assertEquals

class PersistentTrainingStoreTest {
    @Test
    fun allV01RecordsSurviveStoreReconstruction() {
        val backend = FakePreferenceBackend()
        val store = PersistentTrainingStore(backend)
        val item = KnowledgeItem(
            id = "FretToNote:s6:f5",
            questionType = QuestionType.FretToNote,
            string = 6,
            fret = 5,
            note = "A",
            solfege = "La",
            status = MasteryStatus.LEARNING,
        )
        val progress = Progress(
            knowledgeItemId = item.id,
            attempts = 3,
            correct = 2,
            streak = 0,
            weight = 2.56,
            lastSeenAt = 1_700_000_000_000L,
            mastery = MasteryStatus.LEARNING,
            recentResults = listOf(true, true, false),
            seenDays = setOf("2026-08-26", "2026-08-27"),
        )
        val session = Session(
            id = "session-1",
            startedAt = 1_700_000_000_000L,
            endedAt = 1_700_000_010_000L,
            questionCount = 3,
            correctCount = 2,
        )
        val settings = Settings(
            selectedStrings = setOf(1, 3, 6),
            fretStart = 2,
            fretEnd = 8,
            noteTrainingRangeId = "MID_POSITION",
            naturalOnly = true,
        )

        store.saveState(StorageState(version = 2))
        store.saveSettings(settings)
        store.upsertKnowledgeItem(item)
        store.saveProgress(progress)
        store.saveSession(session)

        val restored = PersistentTrainingStore(backend)

        assertEquals(StorageState(version = 2), restored.loadState())
        assertEquals(settings, restored.loadSettings())
        assertEquals(listOf(item), restored.loadKnowledgeItems())
        assertEquals(progress, restored.loadProgress(item.id))
        assertEquals(listOf(progress), restored.loadProgress())
        assertEquals(listOf(session), restored.loadSessions())
    }

    @Test
    fun sameKnowledgeItemIdIsUpsertedInsteadOfDuplicated() {
        val store = PersistentTrainingStore(FakePreferenceBackend())
        val item = KnowledgeItem(
            id = "NoteToSolfege:note:A",
            questionType = QuestionType.NoteToSolfege,
            string = null,
            fret = null,
            note = "A",
            solfege = "La",
        )

        store.upsertKnowledgeItem(item)
        store.upsertKnowledgeItem(item.copy(status = MasteryStatus.BASIC_MASTERY))

        assertEquals(1, store.loadKnowledgeItems().size)
        assertEquals(MasteryStatus.BASIC_MASTERY, store.findKnowledgeItem(item.id)?.status)
    }

    @Test
    fun legacyProgressWithoutWeightRestoresAtInitialWeight() {
        val backend = FakePreferenceBackend()
        backend.putString(
            "v01.storage",
            """
            version=1
            progress.count=1
            progress.0.knowledgeItemId=FretToNote\:s1\:f3
            progress.0.attempts=2
            progress.0.correct=2
            progress.0.streak=2
            """.trimIndent(),
        )

        val progress = PersistentTrainingStore(backend).loadProgress().single()

        assertEquals("FretToNote:s1:f3", progress.knowledgeItemId)
        assertEquals(2, progress.attempts)
        assertEquals(1.0, progress.weight)
    }

    private class FakePreferenceBackend : PreferenceBackend {
        private var value: String? = null

        override fun getString(key: String, defaultValue: String?): String? = value ?: defaultValue

        override fun putString(key: String, value: String) {
            this.value = value
        }
    }
}
