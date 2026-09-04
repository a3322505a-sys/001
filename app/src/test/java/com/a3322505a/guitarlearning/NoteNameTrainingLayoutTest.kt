package com.a3322505a.guitarlearning

import com.a3322505a.guitarlearning.training.NoteTrainingRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NoteNameTrainingLayoutTest {
    @Test
    fun middlePositionKeepsTheSameFretDensityAsTheFirstPosition() {
        assertEquals(1f, noteTrainingFretboardWidthFraction(NoteTrainingRange.LOW_POSITION))
        assertEquals(1f, noteTrainingFretboardWidthFraction(NoteTrainingRange.FULL_FRETBOARD))

        val middleWidth = noteTrainingFretboardWidthFraction(NoteTrainingRange.MID_POSITION)
        assertEquals(4f / 4.55f, middleWidth, 0.0001f)
        assertTrue(middleWidth < 1f)
    }
}
