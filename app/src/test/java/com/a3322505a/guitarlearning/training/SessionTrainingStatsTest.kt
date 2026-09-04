package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.Session
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionTrainingStatsTest {
    @Test
    fun summaryCountsAnswersAndRoundsTheCorrectRate() {
        val stats = SessionTrainingStats.from(
            session = Session(
                id = "round",
                startedAt = 0L,
                questionCount = 21,
                correctCount = 15,
            ),
            mistakes = List(6) { SessionMistake("E", 3, 7) },
        )

        assertEquals(21, stats.answerCount)
        assertEquals(15, stats.correctCount)
        assertEquals(6, stats.errorCount)
        assertEquals(71, stats.correctRatePercent)
    }

    @Test
    fun mostMistakenNotesRetainEveryTiedTargetNote() {
        val mistakes = listOf(
            SessionMistake("E", 2, 3),
            SessionMistake("E", 3, 7),
            SessionMistake("B", 1, 7),
            SessionMistake("B", 4, 4),
            SessionMistake("C", 5, 3),
        )

        val stats = SessionTrainingStats.from(session(5, 0), mistakes)

        assertEquals(listOf("B", "E"), stats.mostMistakenNotes)
        assertEquals(2, stats.mostMistakenNoteErrorCount)
    }

    @Test
    fun weakestLocationGroupsTheActualWrongTapByStringAndFretBand() {
        val mistakes = listOf(
            SessionMistake("E", 3, 5),
            SessionMistake("A", 3, 7),
            SessionMistake("B", 3, 8),
            SessionMistake("C", 3, 9),
            SessionMistake("D", 2, 7),
        )

        val stats = SessionTrainingStats.from(session(5, 0), mistakes)

        assertEquals(
            listOf(SessionWeakLocation(string = 3, fretRange = 5..8, errorCount = 3)),
            stats.weakestLocations,
        )
    }

    @Test
    fun zeroAnswersAndZeroErrorsProduceAnEmptySafeSummary() {
        val stats = SessionTrainingStats.from(session(0, 0), emptyList())

        assertEquals(0, stats.correctRatePercent)
        assertEquals(0, stats.errorCount)
        assertEquals(emptyList(), stats.mostMistakenNotes)
        assertEquals(emptyList(), stats.weakestLocations)
    }

    private fun session(questionCount: Int, correctCount: Int): Session = Session(
        id = "round",
        startedAt = 0L,
        questionCount = questionCount,
        correctCount = correctCount,
    )
}
