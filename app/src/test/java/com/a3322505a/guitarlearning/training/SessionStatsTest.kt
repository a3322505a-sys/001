package com.a3322505a.guitarlearning.training

import kotlin.test.Test
import kotlin.test.assertEquals

class SessionStatsTest {
    @Test
    fun acceptedAnswersUpdateSessionCountsAndCurrentTime() {
        var stats = SessionStats()
        stats = stats.record(
            AnswerResult(
                accepted = true,
                isCorrect = true,
                submittedAnswer = "A",
                correctAnswer = "A",
                responseMs = 200L,
                knowledgeItemId = "item-1",
            ),
        )
        stats = stats.record(
            AnswerResult(
                accepted = true,
                isCorrect = false,
                submittedAnswer = "B",
                correctAnswer = "A",
                responseMs = 400L,
                knowledgeItemId = "item-2",
            ),
        )

        assertEquals(2, stats.questionCount)
        assertEquals(1, stats.correctCount)
        assertEquals(1, stats.incorrectCount)
        assertEquals(400L, stats.currentResponseMs)
        assertEquals(300L, stats.averageResponseMs)
    }

    @Test
    fun rejectedDuplicateDoesNotChangeSessionStats() {
        val stats = SessionStats().record(
            AnswerResult(
                accepted = false,
                isCorrect = false,
                submittedAnswer = "A",
                correctAnswer = "B",
                responseMs = 999L,
                knowledgeItemId = "item-1",
            ),
        )

        assertEquals(SessionStats(), stats)
    }
}
