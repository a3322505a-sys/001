package com.a3322505a.guitarlearning.learning

/** Pitch spelling retains diatonic identity independently from the sounding MIDI value. */
data class SpelledPitch(val letter: Char, val accidental: Int, val octave: Int) {
    init { require(letter in "CDEFGAB" && accidental in -2..2) }
    val midi get() = (octave+1)*12 + mapOf('C' to 0,'D' to 2,'E' to 4,'F' to 5,'G' to 7,'A' to 9,'B' to 11).getValue(letter)+accidental
    val name get() = "$letter" + when(accidental) {-2->"♭♭";-1->"♭";1->"♯";2->"𝄪";else->""}
    val label get() = "$name$octave"
    companion object {
        fun fromNaturalRoot(root: Int, diatonicOffset: Int, semitones: Int): SpelledPitch {
            val natural=listOf(0,2,4,5,7,9,11)
            val start=natural.indexOf(root%12); require(start>=0)
            val degree=start+diatonicOffset
            val octave=root/12-1+degree/7
            val raw=(octave+1)*12+natural[degree%7]
            return SpelledPitch("CDEFGAB"[degree%7],root+semitones-raw,octave)
        }
    }
}

