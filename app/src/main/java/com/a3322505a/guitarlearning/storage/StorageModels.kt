package com.a3322505a.guitarlearning.storage

import com.a3322505a.guitarlearning.training.QuestionType
import com.a3322505a.guitarlearning.training.TrainingModuleIds

/** The deliberately small mastery vocabulary used by V0.2.3. */
enum class MasteryStatus {
    UNLEARNED,
    LEARNING,
    BASIC_MASTERY,
    STABLE_MASTERY,
}

/** A direction-specific learning item with one canonical note/solfege/degree triple. */
data class KnowledgeItem(
    val id: String,
    val questionType: QuestionType? = null,
    val string: Int? = null,
    val fret: Int? = null,
    val note: String? = null,
    val solfege: String? = null,
    val degree: Int? = null,
    val status: MasteryStatus = MasteryStatus.UNLEARNED,
    val moduleId: String = defaultModuleId(questionType),
    val kind: String = questionType?.name.orEmpty(),
)

private fun defaultModuleId(questionType: QuestionType?): String = when (questionType) {
    QuestionType.FretToNote, QuestionType.FretToSolfege -> TrainingModuleIds.FRET_NOTE
    null -> ""
    else -> TrainingModuleIds.NOTE_MAPPING
}

/** Per-Knowledge-Item progress kept separate for every training direction. */
data class Progress(
    val knowledgeItemId: String,
    val attempts: Int = 0,
    val correct: Int = 0,
    val streak: Int = 0,
    /** Persisted sampling weight for a concrete training item. */
    val weight: Double = 1.0,
    val lastSeenAt: Long? = null,
    val mastery: MasteryStatus = MasteryStatus.UNLEARNED,
    /** The latest outcomes are retained for the small V0.2.3 mastery rule. */
    val recentResults: List<Boolean> = emptyList(),
    /** UTC calendar days on which the item was answered. */
    val seenDays: Set<String> = emptySet(),
)

/** Rolling results for one first-fretboard curriculum level. */
data class LevelProgress(
    val level: Int,
    val recentResults: List<Boolean> = emptyList(),
) {
    init {
        require(level in 1..6) { "level must be between 1 and 6" }
    }

    val attempts: Int get() = recentResults.size
    val correct: Int get() = recentResults.count { it }
}

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

/** Settings needed to construct the local question banks. */
data class Settings(
    val selectedStrings: Set<Int> = (1..6).toSet(),
    val fretStart: Int = 0,
    val fretEnd: Int = 4,
    val noteTrainingRangeId: String? = "LOW_POSITION",
    val intervalLevelId: String? = null,
    val unlockedFretboardLevel: Int = 1,
    /** Non-open natural-note coordinates currently active in the first-position curriculum. */
    val firstPositionActiveKnowledgeIds: Set<String> = emptySet(),
    /** True after E/A/D/G/B have each been answered correctly at least once. */
    val firstPositionBaselineComplete: Boolean = false,
    val firstPositionComplete: Boolean = false,
    /** One-shot, persisted introductions already shown by the integrated curriculum. */
    val seenIntroductionIds: Set<String> = emptySet(),
    val notationMode: NotationMode = NotationMode.FIXED_SOLFEGE,
    val naturalOnly: Boolean = true,
) {
    init {
        require(selectedStrings.isNotEmpty()) { "At least one string must be selected" }
        require(selectedStrings.all { it in 1..6 }) { "Strings must be between 1 and 6" }
        require(fretStart in 0..12) { "fretStart must be between 0 and 12" }
        require(fretEnd in 0..12) { "fretEnd must be between 0 and 12" }
        require(fretStart <= fretEnd) { "fretStart must not exceed fretEnd" }
        require(unlockedFretboardLevel in 1..6) {
            "unlockedFretboardLevel must be between 1 and 6"
        }
        require(firstPositionActiveKnowledgeIds.all { FIRST_POSITION_ID.matches(it) }) {
            "First-position knowledge IDs must use the s<STRING>f<FRET> form"
        }
    }

    private companion object {
        val FIRST_POSITION_ID = Regex("s[1-6]f[0-4]")
    }
}
