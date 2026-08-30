package com.a3322505a.guitarlearning.training

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CombinedMappingTrainingTest {
    @Test
    fun generatedQuestionsCoverAllSixPathsAndEveryAnswerDomain() {
        val factory = CombinedQuestionFactory(Random(20260830))
        val paths = buildSet {
            repeat(600) {
                val question = factory.create()
                add(
                    listOf(
                        question.sourceType,
                        question.firstTargetType,
                        question.secondTargetType,
                    ),
                )
                AnswerKind.entries.forEach { kind ->
                    val choices = factory.shuffledAnswers(kind)
                    assertEquals(7, choices.size)
                    assertEquals(7, choices.distinct().size)
                    assertTrue(question.valueFor(kind) in choices)
                }
            }
        }

        assertEquals(6, paths.size)
    }

    @Test
    fun twoCorrectStepsCountAsExactlyOneCorrectQuestion() {
        val machine = CombinedMappingStateMachine(Random(11))
        val first = assertIs<CombinedMappingState.AwaitingAnswer>(machine.state)

        val firstCorrect = assertIs<CombinedMappingState.Correct>(
            machine.submitAnswer(first.correctAnswer),
        )
        assertTrue(!firstCorrect.completesQuestion)
        assertEquals(0, machine.correctCount)
        assertEquals(0, machine.errorCount)

        val second = assertIs<CombinedMappingState.AwaitingAnswer>(
            machine.advanceAfterCorrect(),
        )
        assertEquals(CombinedStep.SECOND, second.step)
        assertEquals(first.question.id, second.question.id)
        assertTrue(second.prompt.startsWith(first.question.valueFor(first.question.sourceType)))
        assertTrue(second.prompt.contains(first.question.valueFor(first.question.firstTargetType)))

        val complete = assertIs<CombinedMappingState.Correct>(
            machine.submitAnswer(second.correctAnswer),
        )
        assertTrue(complete.completesQuestion)
        assertEquals(1, machine.correctCount)
        assertEquals(0, machine.errorCount)

        val next = assertIs<CombinedMappingState.AwaitingAnswer>(machine.advanceAfterCorrect())
        assertNotEquals(first.question.id, next.question.id)
        assertEquals(CombinedStep.FIRST, next.step)
    }

    @Test
    fun anErrorAtEitherStepEndsTheQuestionAndCountsOnlyOnce() {
        val machine = CombinedMappingStateMachine(Random(12))
        val first = assertIs<CombinedMappingState.AwaitingAnswer>(machine.state)
        val wrong = first.choices.first { it != first.correctAnswer }

        val incorrect = assertIs<CombinedMappingState.Incorrect>(machine.submitAnswer(wrong))
        assertEquals(1, machine.errorCount)
        assertEquals(0, machine.correctCount)
        assertSame(incorrect, machine.submitAnswer(first.correctAnswer))
        assertEquals(1, machine.errorCount)

        val next = assertIs<CombinedMappingState.AwaitingAnswer>(machine.nextQuestion())
        assertNotEquals(first.question.id, next.question.id)

        assertIs<CombinedMappingState.Correct>(machine.submitAnswer(next.correctAnswer))
        val second = assertIs<CombinedMappingState.AwaitingAnswer>(machine.advanceAfterCorrect())
        val secondWrong = second.choices.first { it != second.correctAnswer }
        assertIs<CombinedMappingState.Incorrect>(machine.submitAnswer(secondWrong))
        assertEquals(2, machine.errorCount)
        assertEquals(0, machine.correctCount)
    }
}
