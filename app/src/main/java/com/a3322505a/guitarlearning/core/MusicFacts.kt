package com.a3322505a.guitarlearning.core

/** Standard tuning is defined here for old adapters, new courses, notation and audio. */
object MusicFacts {
    val openStringMidi: Map<Int, Int> = mapOf(1 to 64, 2 to 59, 3 to 55, 4 to 50, 5 to 45, 6 to 40)
    val fixedSolfege = mapOf("C" to "Do", "D" to "Re", "E" to "Mi", "F" to "Fa", "G" to "Sol", "A" to "La", "B" to "Si")
    val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    fun midi(string: Int, fret: Int): Int {
        require(string in 1..6 && fret in 0..24)
        return openStringMidi.getValue(string) + fret
    }
    fun note(string: Int, fret: Int): String = noteNames[midi(string, fret) % 12]
    fun label(string: Int, fret: Int): String = "${note(string, fret)}${midi(string, fret) / 12 - 1}"
    fun majorDegree(midi: Int, tonicPitchClass: Int): Int? {
        require(tonicPitchClass in 0..11)
        val index = listOf(0, 2, 4, 5, 7, 9, 11).indexOf(Math.floorMod(midi - tonicPitchClass, 12))
        return if (index < 0) null else index + 1
    }
}
