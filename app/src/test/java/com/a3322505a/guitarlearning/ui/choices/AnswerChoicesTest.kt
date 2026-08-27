package com.a3322505a.guitarlearning.ui.choices

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnswerChoicesTest {
    @Test
    fun oneQuestionAcceptsOnlyOneAnswer() {
        val state = AnswerSubmissionState(listOf("C", "D", "E"))

        assertTrue(state.submit("C"))
        assertFalse(state.submit("D"))
        assertEquals("C", state.submittedAnswer)
        assertFalse(state.canSubmit)
    }

    @Test
    fun invalidAnswersAndRapidRepeatedClicksAreIgnored() {
        val state = AnswerSubmissionState(listOf("Do", "Re", "Mi"))
        var accepted = 0

        repeat(1_000) {
            if (state.submit(if (it == 0) "Do" else "Re")) accepted += 1
        }

        assertEquals(1, accepted)
        assertEquals("Do", state.submittedAnswer)
        assertFalse(state.submit("Fa"))
    }

    @Test
    fun resetClearsStateForTheNextQuestion() {
        val state = AnswerSubmissionState(listOf("C", "D"))

        assertTrue(state.submit("C"))
        state.reset()

        assertTrue(state.canSubmit)
        assertEquals(null, state.submittedAnswer)
        assertTrue(state.submit("D"))
    }

    @Test
    fun sevenChoicesAlwaysUseFourColumnsWithOneEmptyFinalSlot() {
        assertEquals(
            listOf(
                listOf("C", "D", "E", "F"),
                listOf("G", "A", "B", null),
            ),
            answerChoiceGridSlots(listOf("C", "D", "E", "F", "G", "A", "B")),
        )
    }

    @Test
    fun aFullRowDoesNotCreateAnExtraPaddingRow() {
        assertEquals(
            listOf(listOf("Do", "Re", "Mi", "Fa")),
            answerChoiceGridSlots(listOf("Do", "Re", "Mi", "Fa")),
        )
    }
}
