package com.a3322505a.guitarlearning.ui.fretboard

import com.a3322505a.guitarlearning.core.GuitarCore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FretboardTest {
    @Test
    fun rowsAreOrderedFromSixthStringToFirstString() {
        assertEquals(0, rowIndexForString(6))
        assertEquals(1, rowIndexForString(5))
        assertEquals(3, rowIndexForString(3))
        assertEquals(5, rowIndexForString(1))
    }

    @Test
    fun highlightedCellUsesBothStringAndFret() {
        val selected = GuitarCore.getFretPosition(6, 5)

        assertTrue(isHighlighted(selected, string = 6, fret = 5))
        assertFalse(isHighlighted(selected, string = 5, fret = 5))
        assertFalse(isHighlighted(selected, string = 6, fret = 4))
    }

    @Test
    fun requiredReferencePositionsMapToTheirOwnCells() {
        val references = listOf(
            6 to 0,
            6 to 5,
            5 to 12,
            3 to 7,
            1 to 12,
        )

        references.forEach { (string, fret) ->
            val selected = GuitarCore.getFretPosition(string, fret)
            assertTrue(isHighlighted(selected, string, fret))
            assertEquals(6 - string, rowIndexForString(selected.string))
        }
    }

    @Test
    fun invalidStringRowIsRejected() {
        assertFailsWith<IllegalArgumentException> { rowIndexForString(0) }
        assertFailsWith<IllegalArgumentException> { rowIndexForString(7) }
    }
}
