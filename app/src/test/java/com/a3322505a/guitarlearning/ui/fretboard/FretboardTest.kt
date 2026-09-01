package com.a3322505a.guitarlearning.ui.fretboard

import com.a3322505a.guitarlearning.core.GuitarCore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FretboardTest {
    @Test
    fun rowsAreOrderedFromFirstStringToSixthString() {
        assertEquals(0, rowIndexForString(1))
        assertEquals(1, rowIndexForString(2))
        assertEquals(2, rowIndexForString(3))
        assertEquals(5, rowIndexForString(6))
    }

    @Test
    fun highlightedCellUsesBothStringAndFret() {
        val selected = GuitarCore.getFretPosition(6, 5)

        assertTrue(isHighlighted(selected, string = 6, fret = 5))
        assertFalse(isHighlighted(selected, string = 5, fret = 5))
        assertFalse(isHighlighted(selected, string = 6, fret = 4))
    }

    @Test
    fun everyValidTargetHighlightsExactlyOneMatchingCell() {
        for (string in 1..6) {
            for (fret in FIRST_FRET..LAST_FRET) {
                val selected = GuitarCore.getFretPosition(string, fret)
                val highlighted = highlightedCells(selected)

                assertEquals(1, highlighted.size, "Expected one highlighted cell for ${string}弦${fret}品")
                assertEquals(FretboardCell(string, fret), highlighted.single())
            }
        }
    }

    @Test
    fun noTargetHighlightsNoCells() {
        assertEquals(emptyList(), highlightedCells(null))
    }

    @Test
    fun requiredReferencePositionsMapToTheirOwnCells() {
        val references = listOf(
            1 to 0,
            1 to 12,
            3 to 7,
            6 to 0,
            6 to 12,
        )

        references.forEach { (string, fret) ->
            val selected = GuitarCore.getFretPosition(string, fret)
            assertTrue(isHighlighted(selected, string, fret))
            assertEquals(string - 1, rowIndexForString(selected.string))
        }
    }

    @Test
    fun invalidStringRowIsRejected() {
        assertFailsWith<IllegalArgumentException> { rowIndexForString(0) }
        assertFailsWith<IllegalArgumentException> { rowIndexForString(7) }
    }

    @Test
    fun openStringRegionSitsLeftOfTheNutAndIsNarrowerThanAFret() {
        assertEquals(0f, fretLeftFraction(0))
        assertEquals(fretRightFraction(0), fretLeftFraction(1))
        assertTrue(
            fretRightFraction(0) - fretLeftFraction(0) <
                fretRightFraction(1) - fretLeftFraction(1),
        )
        assertEquals(
            fretRightFraction(1) - fretLeftFraction(1),
            fretRightFraction(3) - fretLeftFraction(3),
            0.0001f,
        )
        assertEquals(1f, fretRightFraction(12))
    }

    @Test
    fun targetCentersFollowOpenStringAndPhysicalFretCells() {
        assertTrue(fretCenterFraction(0) < fretRightFraction(0))
        assertTrue(fretCenterFraction(1) > fretRightFraction(0))
        for (fret in 1..LAST_FRET) {
            assertEquals(
                (fretLeftFraction(fret) + fretRightFraction(fret)) / 2f,
                fretCenterFraction(fret),
                0.0001f,
            )
        }
    }

    @Test
    fun headerUsesTheSameNarrowOpenStringWidthAsTheBoard() {
        assertTrue(fretWidthWeight(0) < fretWidthWeight(1))
        assertEquals(1f, fretWidthWeight(1))
        assertEquals(1f, fretWidthWeight(12))
    }

    @Test
    fun standardInlayMarkersUseSingleDotsAndTwelfthFretDoubleDot() {
        assertEquals(1, markerCountForFret(3))
        assertEquals(1, markerCountForFret(5))
        assertEquals(1, markerCountForFret(7))
        assertEquals(1, markerCountForFret(9))
        assertEquals(2, markerCountForFret(12))
        assertEquals(0, markerCountForFret(4))
    }
}
