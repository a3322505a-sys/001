package com.a3322505a.guitarlearning.training

/** In-memory statistics for the current session; persistence is added in P07. */
data class SessionStats(
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val totalResponseMs: Long = 0L,
    val currentResponseMs: Long? = null,
) {
    val questionCount: Int
        get() = correctCount + incorrectCount

    val averageResponseMs: Long
        get() = if (questionCount == 0) 0L else totalResponseMs / questionCount

    fun record(result: AnswerResult): SessionStats {
        if (!result.accepted) return this
        return copy(
            correctCount = correctCount + if (result.isCorrect) 1 else 0,
            incorrectCount = incorrectCount + if (result.isCorrect) 0 else 1,
            totalResponseMs = totalResponseMs + result.responseMs,
            currentResponseMs = result.responseMs,
        )
    }
}
