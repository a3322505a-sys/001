package com.a3322505a.guitarlearning.core

/** Legacy natural-note mapping: fixed solfege, with the degree interpreted in C major only. */
data class NoteMapping(
    val note: String,
    val solfege: String,
    val degree: Int,
)

/** A physical fret location and its domain-level note labels. */
data class FretPosition(
    val string: Int,
    val fret: Int,
    val note: String,
    val solfege: String?,
) {
    init {
        require(string in GuitarCore.MIN_STRING..GuitarCore.MAX_STRING) {
            "string must be between " + GuitarCore.MIN_STRING + " and " + GuitarCore.MAX_STRING
        }
        require(fret in GuitarCore.MIN_FRET..GuitarCore.MAX_FRET) {
            "fret must be between " + GuitarCore.MIN_FRET + " and " + GuitarCore.MAX_FRET
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
    val openStringNotes: Map<Int, String> = MusicFacts.openStringMidi.mapValues { (_, midi) -> MusicFacts.noteNames[midi % 12] }

    /** Legacy compatibility table: fixed solfege and C-major degrees, not key-independent degrees.
     * New degree tasks need explicit key context; see MusicFacts.majorDegree and docs/legacy-v1.md.
     */
    val fixedMappings: List<NoteMapping> = listOf(
        NoteMapping(note = "C", solfege = "Do", degree = 1),
        NoteMapping(note = "D", solfege = "Re", degree = 2),
        NoteMapping(note = "E", solfege = "Mi", degree = 3),
        NoteMapping(note = "F", solfege = "Fa", degree = 4),
        NoteMapping(note = "G", solfege = "Sol", degree = 5),
        NoteMapping(note = "A", solfege = "La", degree = 6),
        NoteMapping(note = "B", solfege = "Si", degree = 7),
    )

    /** Kept as a compatibility lookup for the existing core API. */
    val fixedSolfege: Map<String, String> = fixedMappings.associate { it.note to it.solfege }

    val fixedDegrees: Map<String, Int> = fixedMappings.associate { it.note to it.degree }

    private val naturalNotes = fixedMappings.map { it.note }.toSet()

    fun getNote(string: Int, fret: Int): String {
        validatePosition(string, fret)
        val openNote = openStringNotes.getValue(string)
        val openIndex = chromaticNotes.indexOf(openNote)
        return chromaticNotes[(openIndex + fret) % chromaticNotes.size]
    }

    fun solfegeFor(note: String): String? = fixedSolfege[note]

    fun degreeFor(note: String): Int? = fixedDegrees[note]

    fun mappingForNote(note: String): NoteMapping? =
        fixedMappings.firstOrNull { it.note == note }

    fun mappingForDegree(degree: Int): NoteMapping? =
        fixedMappings.firstOrNull { it.degree == degree }

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
            "string must be between " + MIN_STRING + " and " + MAX_STRING
        }
        require(fret in MIN_FRET..MAX_FRET) {
            "fret must be between " + MIN_FRET + " and " + MAX_FRET
        }
    }
}
