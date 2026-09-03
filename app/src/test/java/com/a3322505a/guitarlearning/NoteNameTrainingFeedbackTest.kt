package com.a3322505a.guitarlearning

import kotlin.test.Test
import kotlin.test.assertEquals

class NoteNameTrainingFeedbackTest {
    @Test
    fun correctFretboardAnswerRemainsVisibleForOneSecond() {
        assertEquals(1_000L, FRETBOARD_CORRECT_FEEDBACK_DURATION_MS)
    }
}
