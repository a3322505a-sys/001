package com.a3322505a.guitarlearning.storage

import com.a3322505a.guitarlearning.training.QuestionType

/** The deliberately small mastery vocabulary used by V0.1. */
enum class MasteryStatus {
    UNLEARNED,
    LEARNING,
    BASIC_MASTERY,
    STABLE_MASTERY,
}

/** A direction-specific learning item; direction is part of its identity. */
data class KnowledgeItem(
    val id: String,
    val questionType: QuestionType,
    val string: Int?,
    val fret: Int?,
    val note: String,
    val solfege: String,
    val status: MasteryStatus = MasteryStatus.UNLEARNED,
)

enum class NotationMode {
    FIXED_SOLFEGE,
}

/** Settings needed to construct the V0.1 question bank. */
data class Settings(
    val selectedStrings: Set<Int> = (1..6).toSet(),
    val fretStart: Int = 0,
    val fretEnd: Int = 12,
    val notationMode: NotationMode = NotationMode.FIXED_SOLFEGE,
    val naturalOnly: Boolean = true,
)
