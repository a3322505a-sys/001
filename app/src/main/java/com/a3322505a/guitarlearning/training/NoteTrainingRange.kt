package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.Settings

/** The three physical regions the learner can choose; difficulty stays automatic. */
enum class NoteTrainingRange(
    val selectedStrings: Set<Int>,
    val fretRange: IntRange,
    val label: String,
) {
    LOW_POSITION(
        selectedStrings = (1..6).toSet(),
        fretRange = 0..4,
        label = "第一把位｜0–4 品",
    ),
    MID_POSITION(
        selectedStrings = (1..6).toSet(),
        fretRange = 5..8,
        label = "中把位｜5–8 品",
    ),
    FULL_FRETBOARD(
        selectedStrings = (1..6).toSet(),
        fretRange = 0..12,
        label = "全指板｜0–12 品",
    );

    fun applyTo(settings: Settings): Settings = settings.copy(
        selectedStrings = selectedStrings,
        fretStart = fretRange.first,
        fretEnd = fretRange.last,
        noteTrainingRangeId = name,
    )

    companion object {
        fun fromId(id: String?): NoteTrainingRange? = when (id) {
            "BASIC", "SINGLE_STRING_1", "CROSS_STRING", "CROSS_STRING_1_TO_3" -> LOW_POSITION
            else -> entries.firstOrNull { it.name == id }
        }

        fun fromSettings(settings: Settings): NoteTrainingRange {
            val storedId = settings.noteTrainingRangeId
            if (storedId != null) return fromId(storedId) ?: LOW_POSITION

            if (settings.selectedStrings != (1..6).toSet()) return LOW_POSITION
            return when (settings.fretStart..settings.fretEnd) {
                5..8 -> MID_POSITION
                0..12 -> FULL_FRETBOARD
                else -> LOW_POSITION
            }
        }

        /** Migrates legacy string-count settings and repairs inconsistent saved coordinates. */
        fun normalize(settings: Settings): Settings = fromSettings(settings).applyTo(settings)
    }
}
