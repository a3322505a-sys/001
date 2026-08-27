package com.a3322505a.guitarlearning.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GuitarCoreTest {
    @Test
    fun everyStringAndFretInV01RangeIsCalculated() {
        val expectedOpenNotes = mapOf(
            6 to "E",
            5 to "A",
            4 to "D",
            3 to "G",
            2 to "B",
            1 to "E",
        )

        var positionCount = 0
        for (string in 1..6) {
            for (fret in 0..12) {
                val openIndex = GuitarCore.chromaticNotes.indexOf(expectedOpenNotes.getValue(string))
                val expected = GuitarCore.chromaticNotes[
                    (openIndex + fret) % GuitarCore.chromaticNotes.size,
                ]
                assertEquals(expected, GuitarCore.getNote(string, fret))
                positionCount += 1
            }
        }

        assertEquals(6 * 13, positionCount)
        assertEquals(78, GuitarCore.allPositions().size)
    }

    @Test
    fun openStringsAndTwelfthFretAreCorrect() {
        assertEquals("E", GuitarCore.getNote(6, 0))
        assertEquals("A", GuitarCore.getNote(5, 0))
        assertEquals("D", GuitarCore.getNote(4, 0))
        assertEquals("G", GuitarCore.getNote(3, 0))
        assertEquals("B", GuitarCore.getNote(2, 0))
        assertEquals("E", GuitarCore.getNote(1, 0))

        assertEquals("E", GuitarCore.getNote(6, 12))
        assertEquals("A", GuitarCore.getNote(5, 12))
        assertEquals("E", GuitarCore.getNote(1, 12))
    }

    @Test
    fun semitoneTransitionsAreCorrect() {
        assertEquals("C", GuitarCore.getNote(2, 1), "B to C")
        assertEquals("F", GuitarCore.getNote(1, 1), "E to F")
        assertEquals("C", GuitarCore.getNote(6, 8), "E to C across the octave")
    }

    @Test
    fun naturalNotesHaveFixedSolfege() {
        val expected = mapOf(
            "C" to "Do",
            "D" to "Re",
            "E" to "Mi",
            "F" to "Fa",
            "G" to "Sol",
            "A" to "La",
            "B" to "Si",
        )

        expected.forEach { (note, solfege) ->
            assertTrue(GuitarCore.isNaturalNote(note))
            assertEquals(solfege, GuitarCore.solfegeFor(note))
        }
        assertTrue(!GuitarCore.isNaturalNote("C#"))
        assertEquals(null, GuitarCore.solfegeFor("C#"))
    }

    @Test
    fun fretPositionContainsBothLabels() {
        val position = GuitarCore.getFretPosition(6, 5)

        assertEquals(6, position.string)
        assertEquals(5, position.fret)
        assertEquals("A", position.note)
        assertEquals("La", position.solfege)
        assertNotNull(position.solfege)
    }

    @Test
    fun invalidStringIsRejected() {
        assertFailsWith<IllegalArgumentException> { GuitarCore.getNote(0, 0) }
        assertFailsWith<IllegalArgumentException> { GuitarCore.getNote(7, 0) }
    }

    @Test
    fun invalidFretIsRejected() {
        assertFailsWith<IllegalArgumentException> { GuitarCore.getNote(1, -1) }
        assertFailsWith<IllegalArgumentException> { GuitarCore.getNote(1, 13) }
    }
}
