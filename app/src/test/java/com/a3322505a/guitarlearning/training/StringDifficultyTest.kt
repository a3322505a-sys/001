package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.Settings
import kotlin.test.Test
import kotlin.test.assertEquals

class StringDifficultyTest {
    @Test
    fun onlyTheThreeDeliberateTrainingStagesAreExposed() {
        assertEquals(3, StringDifficulty.entries.size)
        assertEquals(setOf(1), StringDifficulty.BASIC.selectedStrings)
        assertEquals((1..3).toSet(), StringDifficulty.CROSS_STRING.selectedStrings)
        assertEquals((1..6).toSet(), StringDifficulty.FULL_FRETBOARD.selectedStrings)
    }

    @Test
    fun summaryAndMenuLabelsExplainTheActualRange() {
        assertEquals("仅 1 弦", StringDifficulty.BASIC.summaryLabel)
        assertEquals("1–3 弦", StringDifficulty.CROSS_STRING.summaryLabel)
        assertEquals("全指板", StringDifficulty.FULL_FRETBOARD.summaryLabel)
        assertEquals("基础｜仅 1 弦", StringDifficulty.BASIC.menuLabel)
        assertEquals("跨弦｜1–3 弦", StringDifficulty.CROSS_STRING.menuLabel)
        assertEquals("全指板｜1–6 弦", StringDifficulty.FULL_FRETBOARD.menuLabel)
    }

    @Test
    fun formerSixStageSettingsMigrateToTheNearestSupportedStage() {
        assertEquals(StringDifficulty.BASIC, StringDifficulty.fromStringCount(1))
        assertEquals(StringDifficulty.CROSS_STRING, StringDifficulty.fromStringCount(2))
        assertEquals(StringDifficulty.CROSS_STRING, StringDifficulty.fromStringCount(3))
        assertEquals(StringDifficulty.FULL_FRETBOARD, StringDifficulty.fromStringCount(4))
        assertEquals(StringDifficulty.FULL_FRETBOARD, StringDifficulty.fromStringCount(6))

        val migrated = StringDifficulty.normalize(
            Settings(selectedStrings = (1..2).toSet()),
        )
        assertEquals(StringDifficulty.CROSS_STRING.selectedStrings, migrated.selectedStrings)
    }
}
