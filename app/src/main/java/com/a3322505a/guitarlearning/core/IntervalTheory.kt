package com.a3322505a.guitarlearning.core

/** Pure interval arithmetic shared by question generation, explanations, and tests. */
object IntervalTheory {
    val naturalNotes: List<String> = listOf("C", "D", "E", "F", "G", "A", "B")

    fun semitoneDistance(from: String, to: String, octave: Boolean = false): Int {
        val fromIndex = GuitarCore.chromaticNotes.indexOf(from)
        val toIndex = GuitarCore.chromaticNotes.indexOf(to)
        require(fromIndex >= 0 && toIndex >= 0) { "Notes must use the canonical sharp spelling" }
        val distance = (toIndex - fromIndex + GuitarCore.chromaticNotes.size) %
            GuitarCore.chromaticNotes.size
        return if (octave && distance == 0) GuitarCore.chromaticNotes.size else distance
    }

    fun diatonicSpan(from: String, to: String, octave: Boolean = false): Int {
        val fromIndex = naturalNotes.indexOf(from)
        val toIndex = naturalNotes.indexOf(to)
        require(fromIndex >= 0 && toIndex >= 0) { "Diatonic span supports natural notes only" }
        val span = (toIndex - fromIndex + naturalNotes.size) % naturalNotes.size + 1
        return if (octave && span == 1) 8 else span
    }

    fun chromaticPath(from: String, to: String, octave: Boolean = false): List<String> {
        val start = GuitarCore.chromaticNotes.indexOf(from)
        val distance = semitoneDistance(from, to, octave)
        return (0..distance).map { step ->
            GuitarCore.chromaticNotes[(start + step) % GuitarCore.chromaticNotes.size]
        }
    }

    fun diatonicPath(from: String, to: String, octave: Boolean = false): List<String> {
        val start = naturalNotes.indexOf(from)
        val span = diatonicSpan(from, to, octave)
        return (0 until span).map { step -> naturalNotes[(start + step) % naturalNotes.size] }
    }
}
