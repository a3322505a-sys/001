package com.a3322505a.guitarlearning

import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingTrainingTest {
    @Test
    fun ledgerLinesCoverNotesOutsideTheFiveStaffLines() {
        assertEquals(listOf(-2, -4, -6), staffLedgerSteps(-7))
        assertEquals(emptyList(), staffLedgerSteps(0))
        assertEquals(emptyList(), staffLedgerSteps(8))
        assertEquals(listOf(10, 12), staffLedgerSteps(12))
    }
}
