package com.a3322505a.guitarlearning.core

import kotlin.test.Test
import kotlin.test.assertEquals

class IntervalTheoryTest {
    @Test
    fun naturalHalfStepBoundariesAreCorrect() {
        assertEquals(1, IntervalTheory.semitoneDistance("E", "F"))
        assertEquals(1, IntervalTheory.semitoneDistance("B", "C"))
        assertEquals(2, IntervalTheory.semitoneDistance("C", "D"))
    }

    @Test
    fun examplesHaveCorrectSemitoneAndLetterDistances() {
        assertEquals(4, IntervalTheory.semitoneDistance("C", "E"))
        assertEquals(3, IntervalTheory.diatonicSpan("C", "E"))
        assertEquals(3, IntervalTheory.semitoneDistance("D", "F"))
        assertEquals(3, IntervalTheory.diatonicSpan("D", "F"))
        assertEquals(7, IntervalTheory.semitoneDistance("C", "G"))
        assertEquals(5, IntervalTheory.diatonicSpan("C", "G"))
    }

    @Test
    fun unisonAndOctaveAreExplicitlyDifferent() {
        assertEquals(0, IntervalTheory.semitoneDistance("C", "C"))
        assertEquals(12, IntervalTheory.semitoneDistance("C", "C", octave = true))
        assertEquals(1, IntervalTheory.diatonicSpan("C", "C"))
        assertEquals(8, IntervalTheory.diatonicSpan("C", "C", octave = true))
        assertEquals(13, IntervalTheory.chromaticPath("C", "C", octave = true).size)
    }
}
