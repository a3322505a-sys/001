package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.Progress
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressUpdaterTest {
    @Test
    fun attemptsAverageAndStreakFollowAcceptedAnswers() {
        val initial = Progress(
            knowledgeItemId = "item-1",
            attempts = 1,
            correct = 1,
            streak = 1,
            avgResponseMs = 300.0,
        )
        val correct = AnswerResult(
            accepted = true,
            isCorrect = true,
            submittedAnswer = "A",
            correctAnswer = "A",
            responseMs = 500L,
            knowledgeItemId = "item-1",
        )
        val wrong = AnswerResult(
            accepted = true,
            isCorrect = false,
            submittedAnswer = "B",
            correctAnswer = "A",
            responseMs = 300L,
            knowledgeItemId = "item-1",
        )

        val afterCorrect = ProgressUpdater.record(initial, correct, 1_756_000_000_000L)
        val afterWrong = ProgressUpdater.record(afterCorrect, wrong, 1_756_086_400_000L)

        assertEquals(2, afterCorrect.attempts)
        assertEquals(2, afterCorrect.correct)
        assertEquals(2, afterCorrect.streak)
        assertEquals(400.0, afterCorrect.avgResponseMs)
        assertEquals(listOf(true), afterCorrect.recentResults)
        assertEquals(3, afterWrong.attempts)
        assertEquals(2, afterWrong.correct)
        assertEquals(0, afterWrong.streak)
        assertEquals(1_100.0 / 3.0, afterWrong.avgResponseMs)
        assertEquals(300L, afterWrong.lastResponseMs)
        assertEquals(2, afterWrong.seenDays.size)
        assertEquals(listOf(true, false), afterWrong.recentResults)
    }
}
