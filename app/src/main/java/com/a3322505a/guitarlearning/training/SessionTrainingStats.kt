package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.Session
import kotlin.math.roundToInt

/** One wrong first attempt retained only for the currently active training session. */
data class SessionMistake(
    val targetNote: String,
    val string: Int,
    val fret: Int,
) {
    init {
        require(targetNote.isNotBlank()) { "targetNote must not be blank" }
        require(string in 1..6) { "string must be between 1 and 6" }
        require(fret in 0..12) { "fret must be between 0 and 12" }
    }
}

data class SessionWeakLocation(
    val string: Int,
    val fretRange: IntRange,
    val errorCount: Int,
)

/** Lightweight summary for one visit to the note-name training screen. */
data class SessionTrainingStats(
    val answerCount: Int,
    val correctCount: Int,
    val errorCount: Int,
    val correctRatePercent: Int,
    val mostMistakenNotes: List<String>,
    val mostMistakenNoteErrorCount: Int,
    val weakestLocations: List<SessionWeakLocation>,
) {
    companion object {
        fun from(
            session: Session,
            mistakes: List<SessionMistake>,
        ): SessionTrainingStats {
            val errorCount = session.questionCount - session.correctCount
            val noteCounts = mistakes.groupingBy(SessionMistake::targetNote).eachCount()
            val highestNoteCount = noteCounts.values.maxOrNull() ?: 0
            val mostMistakenNotes = noteCounts
                .filterValues { it == highestNoteCount }
                .keys
                .sorted()

            val locationCounts = mistakes.groupingBy {
                it.string to fretBandFor(it.fret)
            }.eachCount()
            val highestLocationCount = locationCounts.values.maxOrNull() ?: 0
            val weakestLocations = locationCounts
                .filterValues { it == highestLocationCount }
                .map { (location, count) ->
                    SessionWeakLocation(
                        string = location.first,
                        fretRange = location.second,
                        errorCount = count,
                    )
                }
                .sortedWith(compareBy({ it.string }, { it.fretRange.first }))

            return SessionTrainingStats(
                answerCount = session.questionCount,
                correctCount = session.correctCount,
                errorCount = errorCount,
                correctRatePercent = if (session.questionCount == 0) {
                    0
                } else {
                    (session.correctCount * 100.0 / session.questionCount).roundToInt()
                },
                mostMistakenNotes = mostMistakenNotes,
                mostMistakenNoteErrorCount = highestNoteCount,
                weakestLocations = weakestLocations,
            )
        }
    }
}

internal fun fretBandFor(fret: Int): IntRange = when (fret) {
    in 0..4 -> 0..4
    in 5..8 -> 5..8
    in 9..12 -> 9..12
    else -> throw IllegalArgumentException("fret must be between 0 and 12")
}
