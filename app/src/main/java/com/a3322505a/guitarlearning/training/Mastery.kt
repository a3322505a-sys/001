package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.MasteryStatus
import com.a3322505a.guitarlearning.storage.Progress

/** V0.2.3 mastery thresholds; this is intentionally not a full spaced-repetition scheduler. */
object MasteryEvaluator {
    private const val RECENT_WINDOW = 20

    fun evaluate(progress: Progress): MasteryStatus {
        val recent = progress.recentResults.takeLast(RECENT_WINDOW)
        val accuracy = recent.count { it }.toDouble() / RECENT_WINDOW
        val hasRecentWindow = recent.size >= RECENT_WINDOW
        val meetsAccuracy = hasRecentWindow && accuracy >= 0.90
        if (meetsAccuracy && progress.seenDays.size >= 2) {
            return MasteryStatus.STABLE_MASTERY
        }
        if (meetsAccuracy) {
            return MasteryStatus.BASIC_MASTERY
        }
        return if (progress.attempts == 0) MasteryStatus.UNLEARNED else MasteryStatus.LEARNING
    }
}

/** Exact first-version multipliers used by the weighted question sampler. */
object QuestionWeights {
    fun forProgress(progress: Progress?): Double {
        if (progress == null) return 1.0
        var weight = when (progress.mastery) {
            MasteryStatus.UNLEARNED -> 1.0
            MasteryStatus.LEARNING -> 2.0
            MasteryStatus.BASIC_MASTERY -> 1.0
            MasteryStatus.STABLE_MASTERY -> 0.5
        }
        if (progress.recentResults.lastOrNull() == false) weight *= 4.0
        return weight
    }
}

/** Bounded, deliberately asymmetric weight updates for physical note-name positions. */
object PositionQuestionWeights {
    const val INITIAL = 1.0
    const val MINIMUM = 0.4
    const val MAXIMUM = 3.0
    private const val WRONG_MULTIPLIER = 1.6
    private const val CORRECT_MULTIPLIER = 0.9
    private const val MINIMUM_ATTEMPTS_BEFORE_DECAY = 3

    fun forProgress(progress: Progress?): Double = sanitize(progress?.weight ?: INITIAL)

    fun afterAnswer(
        previousWeight: Double,
        totalAttempts: Int,
        isCorrect: Boolean,
    ): Double {
        val current = sanitize(previousWeight)
        return when {
            !isCorrect -> (current * WRONG_MULTIPLIER).coerceAtMost(MAXIMUM)
            totalAttempts >= MINIMUM_ATTEMPTS_BEFORE_DECAY ->
                (current * CORRECT_MULTIPLIER).coerceAtLeast(MINIMUM)
            else -> current
        }
    }

    private fun sanitize(weight: Double): Double =
        if (weight.isFinite()) weight.coerceIn(MINIMUM, MAXIMUM) else INITIAL
}
