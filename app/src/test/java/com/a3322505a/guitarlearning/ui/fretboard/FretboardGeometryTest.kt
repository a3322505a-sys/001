package com.a3322505a.guitarlearning.ui.fretboard

import kotlin.test.Test
import kotlin.test.assertEquals

class FretboardGeometryTest {
    @Test
    fun stringCentersStayInsideTheCanvasWithEqualDynamicRows() {
        assertEquals(1f / 12f, stringCenterFraction(6), 0.0001f)
        assertEquals(0.5f, stringCenterFraction(3), 0.0001f)
        assertEquals(11f / 12f, stringCenterFraction(1), 0.0001f)
    }
}
