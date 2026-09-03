package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.core.GuitarCore
import com.a3322505a.guitarlearning.storage.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoteTrainingRangeTest {
    @Test
    fun onlyTheFiveSpecifiedTrainingRangesAreExposed() {
        assertEquals(
            listOf(
                NoteTrainingRange.SINGLE_STRING_1,
                NoteTrainingRange.CROSS_STRING_1_TO_3,
                NoteTrainingRange.LOW_POSITION,
                NoteTrainingRange.MID_POSITION,
                NoteTrainingRange.FULL_FRETBOARD,
            ),
            NoteTrainingRange.entries,
        )
        assertEquals(2, NoteTrainingRange.entries.count {
            it.group == NoteTrainingRangeGroup.BEGINNER
        })
        assertEquals(3, NoteTrainingRange.entries.count {
            it.group == NoteTrainingRangeGroup.POSITION
        })
    }

    @Test
    fun labelsAndCoordinatesMatchTheConstructionSpecification() {
        val expected = listOf(
            RangeExpectation("单弦｜1 弦", setOf(1), 0..12),
            RangeExpectation("跨弦｜1–3 弦", (1..3).toSet(), 0..12),
            RangeExpectation("第一把位｜0–4 品", (1..6).toSet(), 0..4),
            RangeExpectation("中把位｜5–8 品", (1..6).toSet(), 5..8),
            RangeExpectation("全指板｜0–12 品", (1..6).toSet(), 0..12),
        )

        NoteTrainingRange.entries.zip(expected).forEach { (range, expectation) ->
            assertEquals(expectation.label, range.label)
            assertEquals(expectation.strings, range.selectedStrings)
            assertEquals(expectation.frets, range.fretRange)

            val settings = range.applyTo(Settings())
            assertEquals(range.name, settings.noteTrainingRangeId)
            assertEquals(range.selectedStrings, settings.selectedStrings)
            assertEquals(range.fretRange.first, settings.fretStart)
            assertEquals(range.fretRange.last, settings.fretEnd)

            val candidates = GuitarCore.allPositions(
                strings = range.selectedStrings.sorted(),
                frets = range.fretRange,
                naturalOnly = true,
            )
            assertTrue(candidates.any { it.fret == range.fretRange.first })
            assertTrue(candidates.any { it.fret == range.fretRange.last })
        }
    }

    @Test
    fun legacyStringStagesMigrateWithoutLeakingRemovedRanges() {
        val legacyMappings = mapOf(
            setOf(1) to NoteTrainingRange.SINGLE_STRING_1,
            (1..2).toSet() to NoteTrainingRange.CROSS_STRING_1_TO_3,
            (1..3).toSet() to NoteTrainingRange.CROSS_STRING_1_TO_3,
            (1..4).toSet() to NoteTrainingRange.LOW_POSITION,
            (1..5).toSet() to NoteTrainingRange.LOW_POSITION,
            (1..6).toSet() to NoteTrainingRange.LOW_POSITION,
        )

        legacyMappings.forEach { (legacyStrings, expectedRange) ->
            val migrated = NoteTrainingRange.normalize(
                Settings(selectedStrings = legacyStrings, fretStart = 0, fretEnd = 12),
            )
            assertEquals(expectedRange.name, migrated.noteTrainingRangeId)
            assertEquals(expectedRange.selectedStrings, migrated.selectedStrings)
            assertEquals(expectedRange.fretRange.first, migrated.fretStart)
            assertEquals(expectedRange.fretRange.last, migrated.fretEnd)
        }
    }

    @Test
    fun explicitNewRangeSurvivesAndInvalidIdFallsBackSafely() {
        val full = NoteTrainingRange.FULL_FRETBOARD.applyTo(Settings())
        assertEquals(NoteTrainingRange.FULL_FRETBOARD, NoteTrainingRange.fromSettings(full))
        assertEquals(full, NoteTrainingRange.normalize(full))

        val invalid = Settings(
            selectedStrings = (1..6).toSet(),
            noteTrainingRangeId = "REMOVED_OR_CORRUPT",
        )
        assertEquals(
            NoteTrainingRange.SINGLE_STRING_1.applyTo(invalid),
            NoteTrainingRange.normalize(invalid),
        )
    }

    private data class RangeExpectation(
        val label: String,
        val strings: Set<Int>,
        val frets: IntRange,
    )
}
