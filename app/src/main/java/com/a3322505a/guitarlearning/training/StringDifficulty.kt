package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.Settings

/** Continuous note-training ranges: 1弦, then 1–2弦, up to all six strings. */
enum class StringDifficulty(
    val stringCount: Int,
) {
    ONE(1),
    TWO(2),
    THREE(3),
    FOUR(4),
    FIVE(5),
    SIX(6);

    val selectedStrings: Set<Int>
        get() = (1..stringCount).toSet()

    val label: String
        get() = if (stringCount == 1) {
            "1弦"
        } else {
            "1–" + stringCount + "弦"
        }

    companion object {
        fun fromStringCount(count: Int): StringDifficulty =
            entries.first { it.stringCount == count }

        fun fromSettings(settings: Settings): StringDifficulty =
            fromStringCount(settings.selectedStrings.size.coerceIn(1, 6))
    }
}
