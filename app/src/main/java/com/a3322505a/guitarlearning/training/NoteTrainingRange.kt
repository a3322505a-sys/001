package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.Settings

enum class NoteTrainingRangeGroup(val label: String) {
    BEGINNER("入门"),
    POSITION("区域"),
}

/** The five deliberate stages exposed by the note-name trainer. */
enum class NoteTrainingRange(
    val group: NoteTrainingRangeGroup,
    val selectedStrings: Set<Int>,
    val fretRange: IntRange,
    val label: String,
) {
    SINGLE_STRING_1(
        group = NoteTrainingRangeGroup.BEGINNER,
        selectedStrings = setOf(1),
        fretRange = 0..12,
        label = "单弦｜1 弦",
    ),
    CROSS_STRING_1_TO_3(
        group = NoteTrainingRangeGroup.BEGINNER,
        selectedStrings = (1..3).toSet(),
        fretRange = 0..12,
        label = "跨弦｜1–3 弦",
    ),
    LOW_POSITION(
        group = NoteTrainingRangeGroup.POSITION,
        selectedStrings = (1..6).toSet(),
        fretRange = 0..4,
        label = "低把位｜0–4 品",
    ),
    MID_POSITION(
        group = NoteTrainingRangeGroup.POSITION,
        selectedStrings = (1..6).toSet(),
        fretRange = 5..8,
        label = "中把位｜5–8 品",
    ),
    FULL_FRETBOARD(
        group = NoteTrainingRangeGroup.POSITION,
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
            "BASIC" -> SINGLE_STRING_1
            "CROSS_STRING" -> CROSS_STRING_1_TO_3
            else -> entries.firstOrNull { it.name == id }
        }

        fun fromSettings(settings: Settings): NoteTrainingRange {
            val storedId = settings.noteTrainingRangeId
            if (storedId != null) return fromId(storedId) ?: SINGLE_STRING_1

            return when (settings.selectedStrings) {
                setOf(1) -> SINGLE_STRING_1
                (1..2).toSet(), (1..3).toSet() -> CROSS_STRING_1_TO_3
                (1..4).toSet(), (1..5).toSet(), (1..6).toSet() -> LOW_POSITION
                else -> SINGLE_STRING_1
            }
        }

        /** Migrates legacy string-count settings and repairs inconsistent saved coordinates. */
        fun normalize(settings: Settings): Settings = fromSettings(settings).applyTo(settings)
    }
}
