package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.MasteryStatus
import com.a3322505a.guitarlearning.storage.Progress

/** V0.1 mastery thresholds; this is intentionally not a full spaced-repetition scheduler. */
object MasteryEvaluator {
    private const val RECENT_WINDOW = 20
    private const val BASIC_MAX_AVG_MS = 2_000.0
    private const val STABLE_MAX_AVG_MS = 1_500.0

    fun evaluate(progress: Progress): MasteryStatus {
        val recent = progress.recentResults.takeLast(RECENT_WINDOW)
        val accuracy = recent.count { it }.toDouble() / RECENT_WINDOW
        val hasRecentWindow = recent.size >= RECENT_WINDOW
        val meetsAccuracy = hasRecentWindow && accuracy >= 0.90
        if (meetsAccuracy && progress.seenDays.size >= 2 &&
            progress.avgResponseMs <= STABLE_MAX_AVG_MS
        ) {
            return MasteryStatus.STABLE_MASTERY
        }
        if (meetsAccuracy && progress.avgResponseMs <= BASIC_MAX_AVG_MS) {
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
        if (progress.lastResponseMs != null && progress.lastResponseMs > 2_000L) weight *= 3.0
        return weight
    }
}
