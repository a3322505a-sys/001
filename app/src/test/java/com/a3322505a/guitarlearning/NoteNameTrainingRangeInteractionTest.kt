package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.core.GuitarCore
import com.a3322505a.guitarlearning.training.NoteTrainingRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoteNameTrainingRangeInteractionTest {
    @Test
    fun everyTrainingRangeDisplaysTheCompleteFretboard() {
        NoteTrainingRange.entries.forEach { range ->
            assertEquals(0..12, NOTE_TRAINING_VISIBLE_FRET_RANGE, range.name)
        }
    }

    @Test
    fun firstPositionOnlyDispatchesFretsZeroThroughFour() {
        assertOnlyFretsAreDispatched(NoteTrainingRange.LOW_POSITION, 0..4)
    }

    @Test
    fun middlePositionOnlyDispatchesFretsFiveThroughEight() {
        assertOnlyFretsAreDispatched(NoteTrainingRange.MID_POSITION, 5..8)
    }

    @Test
    fun fullFretboardDispatchesEveryVisibleFret() {
        assertOnlyFretsAreDispatched(NoteTrainingRange.FULL_FRETBOARD, 0..12)
    }

    private fun assertOnlyFretsAreDispatched(
        range: NoteTrainingRange,
        expectedAllowedFrets: IntRange,
    ) {
        val dispatchedFrets = mutableListOf<Int>()

        for (fret in NOTE_TRAINING_VISIBLE_FRET_RANGE) {
            val wasDispatched = dispatchNoteTrainingTap(
                position = GuitarCore.getFretPosition(string = 3, fret = fret),
                trainingRange = range,
            ) { dispatchedFrets += it.fret }

            if (fret in expectedAllowedFrets) {
                assertTrue(wasDispatched, "${range.name} should accept fret $fret")
            } else {
                assertFalse(wasDispatched, "${range.name} should ignore fret $fret")
            }
        }

        assertEquals(expectedAllowedFrets.toList(), dispatchedFrets)
    }
}
