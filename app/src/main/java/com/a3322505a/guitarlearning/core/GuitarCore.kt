package com.a3322505a.guitarlearning.core

/** A physical fret location and its domain-level note labels. */
data class FretPosition(
    val string: Int,
    val fret: Int,
    val note: String,
    val solfege: String?,
) {
    init {
        require(string in GuitarCore.MIN_STRING..GuitarCore.MAX_STRING) {
            "string must be between ${GuitarCore.MIN_STRING} and ${GuitarCore.MAX_STRING}"
        }
        require(fret in GuitarCore.MIN_FRET..GuitarCore.MAX_FRET) {
            "fret must be between ${GuitarCore.MIN_FRET} and ${GuitarCore.MAX_FRET}"
        }
    }
}

/** Pure guitar-domain calculations. It has no Android, UI, or storage dependency. */
object GuitarCore {
    const val MIN_STRING = 1
    const val MAX_STRING = 6
    const val MIN_FRET = 0
    const val MAX_FRET = 12

    val chromaticNotes: List<String> = listOf(
        "C", "C#", "D", "D#", "E", "F",
        "F#", "G", "G#", "A", "A#", "B",
    )

    /** Standard tuning, keyed by the guitar string number used by players. */
    val openStringNotes: Map<Int, String> = mapOf(
        6 to "E",
        5 to "A",
        4 to "D",
        3 to "G",
        2 to "B",
        1 to "E",
    )

    val fixedSolfege: Map<String, String> = mapOf(
        "C" to "Do",
        "D" to "Re",
        "E" to "Mi",
        "F" to "Fa",
        "G" to "Sol",
        "A" to "La",
        "B" to "Si",
    )

    private val naturalNotes = fixedSolfege.keys

    fun getNote(string: Int, fret: Int): String {
        validatePosition(string, fret)
        val openNote = openStringNotes.getValue(string)
        val openIndex = chromaticNotes.indexOf(openNote)
        return chromaticNotes[(openIndex + fret) % chromaticNotes.size]
    }

    fun solfegeFor(note: String): String? = fixedSolfege[note]

    fun isNaturalNote(note: String): Boolean = note in naturalNotes

    fun getFretPosition(string: Int, fret: Int): FretPosition {
        val note = getNote(string, fret)
        return FretPosition(
            string = string,
            fret = fret,
            note = note,
            solfege = solfegeFor(note),
        )
    }

    fun allPositions(
        strings: Iterable<Int> = MIN_STRING..MAX_STRING,
        frets: IntRange = MIN_FRET..MAX_FRET,
        naturalOnly: Boolean = false,
    ): List<FretPosition> = strings.flatMap { string ->
        frets.mapNotNull { fret ->
            val position = getFretPosition(string, fret)
            position.takeUnless { naturalOnly && !isNaturalNote(it.note) }
        }
    }

    private fun validatePosition(string: Int, fret: Int) {
        require(string in MIN_STRING..MAX_STRING) {
            "string must be between $MIN_STRING and $MAX_STRING"
        }
        require(fret in MIN_FRET..MAX_FRET) {
            "fret must be between $MIN_FRET and $MAX_FRET"
        }
    }
}
