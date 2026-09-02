package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.Progress
import kotlin.test.Test
import kotlin.test.assertEquals

class PositionQuestionWeightsTest {
    @Test
    fun firstTwoCorrectAnswersKeepANewPositionAtInitialWeight() {
        val first = PositionQuestionWeights.afterAnswer(
            previousWeight = 1.0,
            totalAttempts = 1,
            isCorrect = true,
        )
        val second = PositionQuestionWeights.afterAnswer(
            previousWeight = first,
            totalAttempts = 2,
            isCorrect = true,
        )
        val third = PositionQuestionWeights.afterAnswer(
            previousWeight = second,
            totalAttempts = 3,
            isCorrect = true,
        )

        assertEquals(1.0, first)
        assertEquals(1.0, second)
        assertEquals(0.9, third, absoluteTolerance = 0.000_001)
    }

    @Test
    fun repeatedWrongAnswersRiseQuicklyButNeverExceedMaximum() {
        val first = wrong(1.0, attempts = 1)
        val second = wrong(first, attempts = 2)
        val third = wrong(second, attempts = 3)
        val fourth = wrong(third, attempts = 4)

        assertEquals(1.6, first, absoluteTolerance = 0.000_001)
        assertEquals(2.56, second, absoluteTolerance = 0.000_001)
        assertEquals(3.0, third)
        assertEquals(3.0, fourth)
    }

    @Test
    fun repeatedCorrectAnswersNeverFallBelowMinimum() {
        var weight = 1.0
        repeat(100) { index ->
            weight = PositionQuestionWeights.afterAnswer(
                previousWeight = weight,
                totalAttempts = index + 3,
                isCorrect = true,
            )
        }

        assertEquals(0.4, weight)
        assertEquals(
            0.4,
            PositionQuestionWeights.afterAnswer(weight, totalAttempts = 103, isCorrect = true),
        )
    }

    @Test
    fun loadedWeightsAreAlwaysSanitizedToTheSupportedRange() {
        assertEquals(
            0.4,
            PositionQuestionWeights.forProgress(Progress("low", weight = -10.0)),
        )
        assertEquals(
            3.0,
            PositionQuestionWeights.forProgress(Progress("high", weight = 10.0)),
        )
        assertEquals(
            1.0,
            PositionQuestionWeights.forProgress(Progress("invalid", weight = Double.NaN)),
        )
    }

    private fun wrong(weight: Double, attempts: Int): Double =
        PositionQuestionWeights.afterAnswer(
            previousWeight = weight,
            totalAttempts = attempts,
            isCorrect = false,
        )
}
