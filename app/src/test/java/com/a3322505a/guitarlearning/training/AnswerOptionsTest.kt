package com.a3322505a.guitarlearning.training

import kotlin.test.Test
import kotlin.test.assertEquals

class AnswerOptionsTest {
    @Test
    fun noteAndSolfegeChoicesAreFixed() {
        assertEquals(listOf("C", "D", "E", "F", "G", "A", "B"), AnswerOptions.notes)
        assertEquals(listOf("Do", "Re", "Mi", "Fa", "Sol", "La", "Si"), AnswerOptions.solfege)
        assertEquals(AnswerOptions.notes, AnswerOptions.forKind(AnswerKind.NOTE))
        assertEquals(AnswerOptions.solfege, AnswerOptions.forKind(AnswerKind.SOLFEGE))
    }
}
