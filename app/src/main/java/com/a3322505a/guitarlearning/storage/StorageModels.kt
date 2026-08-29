package com.a3322505a.guitarlearning.storage

import com.a3322505a.guitarlearning.training.QuestionType

/** The deliberately small mastery vocabulary used by V0.2.1. */
enum class MasteryStatus {
    UNLEARNED,
    LEARNING,
    BASIC_MASTERY,
    STABLE_MASTERY,
}

/** A direction-specific learning item with one canonical note/solfege/degree triple. */
data class KnowledgeItem(
    val id: String,
    val questionType: QuestionType,
    val string: Int?,
    val fret: Int?,
    val note: String,
    val solfege: String,
    val degree: Int = 0,
    val status: MasteryStatus = MasteryStatus.UNLEARNED,
)

/** Per-Knowledge-Item progress kept separate for every training direction. */
data class Progress(
    val knowledgeItemId: String,
    val attempts: Int = 0,
    val correct: Int = 0,
    val streak: Int = 0,
    val lastSeenAt: Long? = null,
    val mastery: MasteryStatus = MasteryStatus.UNLEARNED,
    /** The latest outcomes are retained for the small V0.2.1 mastery rule. */
    val recentResults: List<Boolean> = emptyList(),
    /** UTC calendar days on which the item was answered. */
    val seenDays: Set<String> = emptySet(),
)

data class Session(
    val id: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val questionCount: Int = 0,
    val correctCount: Int = 0,
)

enum class NotationMode {
    FIXED_SOLFEGE,
}

/** Settings needed to construct the V0.2.1 question bank. */
data class Settings(
    val selectedStrings: Set<Int> = (1..6).toSet(),
    val fretStart: Int = 0,
    val fretEnd: Int = 12,
    val notationMode: NotationMode = NotationMode.FIXED_SOLFEGE,
    val naturalOnly: Boolean = true,
) {
    init {
        require(selectedStrings.isNotEmpty()) { "At least one string must be selected" }
        require(selectedStrings.all { it in 1..6 }) { "Strings must be between 1 and 6" }
        require(fretStart in 0..12) { "fretStart must be between 0 and 12" }
        require(fretEnd in 0..12) { "fretEnd must be between 0 and 12" }
        require(fretStart <= fretEnd) { "fretStart must not exceed fretEnd" }
    }
}
