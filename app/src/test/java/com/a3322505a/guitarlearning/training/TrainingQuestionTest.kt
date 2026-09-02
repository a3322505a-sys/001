package com.a3322505a.guitarlearning.training

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
}
