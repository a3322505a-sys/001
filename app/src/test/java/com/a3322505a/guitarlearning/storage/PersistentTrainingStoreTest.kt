package com.a3322505a.guitarlearning.storage

import com.a3322505a.guitarlearning.training.QuestionType
import com.a3322505a.guitarlearning.training.TrainingModuleIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
            unlockedFretboardLevel = 4,
            firstPositionActiveKnowledgeIds = setOf("s6f1", "s2f1"),
            firstPositionBaselineComplete = true,
            firstPositionComplete = true,
            seenIntroductionIds = setOf("half_step"),
            naturalOnly = true,
        )

        store.saveState(StorageState(version = 2))
        store.saveSettings(settings)
        store.upsertKnowledgeItem(item)
        store.saveProgress(progress)
        store.saveLevelProgress(
            LevelProgress(3, List(18) { true } + listOf(false, false)),
        )
        store.saveSession(session)

        val restored = PersistentTrainingStore(backend)

        assertEquals(StorageState(version = 2), restored.loadState())
        assertEquals(settings, restored.loadSettings())
        assertEquals(listOf(item), restored.loadKnowledgeItems())
        assertEquals(progress, restored.loadProgress(item.id))
        assertEquals(listOf(progress), restored.loadProgress())
        assertEquals(18, restored.loadLevelProgress(3)?.correct)
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
        assertEquals(1, PersistentTrainingStore(backend).loadSettings().unlockedFretboardLevel)
        assertEquals(emptySet(), PersistentTrainingStore(backend)
            .loadSettings().firstPositionActiveKnowledgeIds)
        assertFalse(PersistentTrainingStore(backend).loadSettings().firstPositionBaselineComplete)
        assertFalse(PersistentTrainingStore(backend).loadSettings().firstPositionComplete)
    }

    @Test
    fun legacyFretStageMigratesToActiveCoordinatesWithoutLosingProgress() {
        val backend = FakePreferenceBackend()
        backend.putString(
            "v01.storage",
            """
            version=1
            settings.selectedStrings=1,2,3,4,5,6
            settings.fretStart=0
            settings.fretEnd=4
            settings.noteTrainingRangeId=LOW_POSITION
            settings.firstPositionMaxFret=2
            settings.firstPositionStageAttempts=7
            settings.firstPositionComplete=false
            progress.count=1
            progress.0.knowledgeItemId=FretToNoteSet\:LOW_POSITION\:E\:s1f0-s4f2-s6f0
            progress.0.attempts=9
            progress.0.correct=8
            progress.0.weight=0.7
            """.trimIndent(),
        )

        val store = PersistentTrainingStore(backend)
        val settings = store.loadSettings()

        assertTrue(settings.firstPositionBaselineComplete)
        assertEquals(
            setOf("s6f1", "s2f1", "s1f1", "s5f2", "s4f2", "s3f2"),
            settings.firstPositionActiveKnowledgeIds,
        )
        assertEquals(9, store.loadProgress().single().attempts)
        assertEquals(0.7, store.loadProgress().single().weight)
    }

    @Test
    fun intervalSettingsAndGenericKnowledgeSurviveReconstruction() {
        val backend = FakePreferenceBackend()
        val store = PersistentTrainingStore(backend)
        val settings = Settings(intervalLevelId = "LV3")
        val item = KnowledgeItem(
            id = "interval:identify:C:G",
            moduleId = TrainingModuleIds.INTERVAL,
            kind = "identify",
        )

        store.saveSettings(settings)
        store.upsertKnowledgeItem(item)

        val restored = PersistentTrainingStore(backend)
        assertEquals("LV3", restored.loadSettings().intervalLevelId)
        assertEquals(item, restored.findKnowledgeItem(item.id))
    }

    @Test
    fun levelSixUnlockAndProgressSurviveReconstruction() {
        val backend = FakePreferenceBackend()
        val store = PersistentTrainingStore(backend)

        store.saveSettings(Settings(unlockedFretboardLevel = 6))
        store.saveLevelProgress(LevelProgress(6, listOf(true, false, true)))

        val restored = PersistentTrainingStore(backend)
        assertEquals(6, restored.loadSettings().unlockedFretboardLevel)
        assertEquals(listOf(true, false, true), restored.loadLevelProgress(6)?.recentResults)
    }

    private class FakePreferenceBackend : PreferenceBackend {
        private var value: String? = null

        override fun getString(key: String, defaultValue: String?): String? = value ?: defaultValue

        override fun putString(key: String, value: String) {
            this.value = value
        }
    }
}
