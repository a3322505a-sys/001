package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.Settings

/** The three deliberate training stages exposed by the note-name trainer. */
enum class StringDifficulty(
    val selectedStrings: Set<Int>,
    val summaryLabel: String,
    val menuLabel: String,
) {
    BASIC(
        selectedStrings = setOf(1),
        summaryLabel = "仅 1 弦",
        menuLabel = "基础｜仅 1 弦",
    ),
    CROSS_STRING(
        selectedStrings = (1..3).toSet(),
        summaryLabel = "1–3 弦",
        menuLabel = "跨弦｜1–3 弦",
    ),
    FULL_FRETBOARD(
        selectedStrings = (1..6).toSet(),
        summaryLabel = "全指板",
        menuLabel = "全指板｜1–6 弦",
    );

    companion object {
        fun fromStringCount(count: Int): StringDifficulty = when {
            count <= 1 -> BASIC
            count <= 3 -> CROSS_STRING
            else -> FULL_FRETBOARD
        }

        fun fromSettings(settings: Settings): StringDifficulty =
            fromStringCount(settings.selectedStrings.maxOrNull() ?: 1)

        /** Maps settings saved by the former six-stage UI onto a supported stage. */
        fun normalize(settings: Settings): Settings =
            settings.copy(selectedStrings = fromSettings(settings).selectedStrings)
    }
}
