package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.core.GuitarCore
import com.a3322505a.guitarlearning.storage.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoteTrainingRangeTest {
    @Test
    fun onlyTheThreePhysicalTrainingRegionsAreExposed() {
        assertEquals(
            listOf(
                NoteTrainingRange.LOW_POSITION,
                NoteTrainingRange.MID_POSITION,
                NoteTrainingRange.FULL_FRETBOARD,
            ),
            NoteTrainingRange.entries,
        )
    }

    @Test
    fun labelsAndCoordinatesMatchTheConstructionSpecification() {
        val expected = listOf(
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
    fun removedBeginnerRangeIdsMigrateToFirstPosition() {
        listOf("BASIC", "SINGLE_STRING_1", "CROSS_STRING", "CROSS_STRING_1_TO_3")
            .forEach { legacyId ->
                val legacy = Settings(
                    selectedStrings = setOf(1),
                    fretStart = 0,
                    fretEnd = 12,
                    noteTrainingRangeId = legacyId,
                )
                assertEquals(
                    NoteTrainingRange.LOW_POSITION.applyTo(legacy),
                    NoteTrainingRange.normalize(legacy),
                )
            }
    }

    @Test
    fun explicitRegionSurvivesAndInvalidIdFallsBackSafely() {
        val full = NoteTrainingRange.FULL_FRETBOARD.applyTo(Settings())
        assertEquals(NoteTrainingRange.FULL_FRETBOARD, NoteTrainingRange.fromSettings(full))
        assertEquals(full, NoteTrainingRange.normalize(full))

        val invalid = Settings(noteTrainingRangeId = "REMOVED_OR_CORRUPT")
        assertEquals(
            NoteTrainingRange.LOW_POSITION.applyTo(invalid),
            NoteTrainingRange.normalize(invalid),
        )
    }

    private data class RangeExpectation(
        val label: String,
        val strings: Set<Int>,
        val frets: IntRange,
    )
}
