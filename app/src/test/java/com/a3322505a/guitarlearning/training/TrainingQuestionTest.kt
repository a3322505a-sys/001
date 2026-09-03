package com.a3322505a.guitarlearning.training

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TrainingQuestionTest {
    private val payload = IntervalPayload("C", "D", 2, null, null)

    @Test
    fun judgementUsesStableChoiceIdsInsteadOfLabels() {
        val question = TrainingQuestion(
            moduleId = TrainingModuleIds.INTERVAL,
            kind = "whole_half",
            prompt = "C → D",
            answerChoices = listOf(
                AnswerChoice("whole_step", "全音"),
                AnswerChoice("half_step", "半音"),
            ),
            correctChoiceId = "whole_step",
            knowledgeItemId = "interval:whole_half:C:D",
            payload = payload,
        )

        assertEquals("全音", question.correctAnswer)
        assertEquals("whole_step", question.choiceForLabel("全音")?.id)
    }

    @Test
    fun invalidOrDuplicateChoiceIdsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            TrainingQuestion(
                TrainingModuleIds.INTERVAL,
                "test",
                "C → D",
                listOf(AnswerChoice("same", "A"), AnswerChoice("same", "B")),
                "same",
                "id",
                payload,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            TrainingQuestion(
                TrainingModuleIds.INTERVAL,
                "test",
                "C → D",
                listOf(AnswerChoice("a", "A")),
                "missing",
                "id",
                payload,
            )
        }
    }

    @Test
    fun fretboardQuestionCarriesASemanticPositionWithoutChoiceButtons() {
        val question = QuestionFactory().create(
            QuestionType.FretToNote,
            com.a3322505a.guitarlearning.core.GuitarCore.getFretPosition(2, 1),
        )

        assertEquals(AnswerMode.FRETBOARD, question.answerMode)
        assertEquals(AnswerValue.FretPosition(2, 1), question.correctAnswerValue)
        assertTrue(question.answerChoices.isEmpty())
        assertEquals("FretToNote:s2:f1", question.knowledgeItemId)
    }
}
