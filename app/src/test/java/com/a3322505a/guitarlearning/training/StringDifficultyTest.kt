package com.a3322505a.guitarlearning.training

import kotlin.test.Test
import kotlin.test.assertEquals

class StringDifficultyTest {
    @Test
    fun everyLevelUsesAContiguousRangeStartingAtStringOne() {
        assertEquals(setOf(1), StringDifficulty.ONE.selectedStrings)
        assertEquals(setOf(1, 2), StringDifficulty.TWO.selectedStrings)
        assertEquals(setOf(1, 2, 3), StringDifficulty.THREE.selectedStrings)
        assertEquals(setOf(1, 2, 3, 4), StringDifficulty.FOUR.selectedStrings)
        assertEquals(setOf(1, 2, 3, 4, 5), StringDifficulty.FIVE.selectedStrings)
        assertEquals((1..6).toSet(), StringDifficulty.SIX.selectedStrings)
    }

    @Test
    fun labelsDescribeTheActualContinuousRange() {
        assertEquals("1弦", StringDifficulty.ONE.label)
        assertEquals("1–2弦", StringDifficulty.TWO.label)
        assertEquals("1–6弦", StringDifficulty.SIX.label)
    }
}
