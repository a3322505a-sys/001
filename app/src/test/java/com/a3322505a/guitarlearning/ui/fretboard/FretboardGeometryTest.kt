package com.a3322505a.guitarlearning.ui.fretboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FretboardGeometryTest {
    @Test
    fun stringCentersStayInsideTheCanvasWithEqualDynamicRows() {
        assertEquals(1f / 12f, stringCenterFraction(1), 0.0001f)
        assertEquals(5f / 12f, stringCenterFraction(3), 0.0001f)
        assertEquals(11f / 12f, stringCenterFraction(6), 0.0001f)
    }

    @Test
    fun everyCellCenterMapsBackToTheSamePhysicalPosition() {
        val width = 1_000f
        val height = 600f

        for (string in 1..6) {
            for (fret in FIRST_FRET..LAST_FRET) {
                val position = assertNotNull(
                    FretboardGeometry.positionAt(
                        x = width * fretCenterFraction(fret),
                        y = height * stringCenterFraction(string),
                        width = width,
                        height = height,
                    ),
                )
                assertEquals(string, position.string)
                assertEquals(fret, position.fret)
            }
        }
    }

    @Test
    fun fretBoundariesUseOneStableNonOverlappingCell() {
        val width = 1_000f
        val height = 600f
        val y = height * stringCenterFraction(3)

        assertEquals(0, FretboardGeometry.positionAt(0f, y, width, height)?.fret)
        assertEquals(
            1,
            FretboardGeometry.positionAt(width * fretRightFraction(0), y, width, height)?.fret,
        )
        assertEquals(
            12,
            FretboardGeometry.positionAt(width * fretRightFraction(11), y, width, height)?.fret,
        )
        assertEquals(12, FretboardGeometry.positionAt(width, y, width, height)?.fret)
    }

    @Test
    fun verticalBandsSnapToTheNearestStringIncludingBoardEdges() {
        val width = 1_000f
        val height = 600f
        val x = width * fretCenterFraction(5)

        assertEquals(1, FretboardGeometry.positionAt(x, 0f, width, height)?.string)
        assertEquals(2, FretboardGeometry.positionAt(x, height / 6f, width, height)?.string)
        assertEquals(6, FretboardGeometry.positionAt(x, height, width, height)?.string)
    }

    @Test
    fun tapsOutsideTheRenderedBoardAreIgnored() {
        assertNull(FretboardGeometry.positionAt(-1f, 10f, 100f, 60f))
        assertNull(FretboardGeometry.positionAt(101f, 10f, 100f, 60f))
        assertNull(FretboardGeometry.positionAt(10f, -1f, 100f, 60f))
        assertNull(FretboardGeometry.positionAt(10f, 61f, 100f, 60f))
        assertNull(FretboardGeometry.positionAt(10f, 10f, 0f, 60f))
    }
}
