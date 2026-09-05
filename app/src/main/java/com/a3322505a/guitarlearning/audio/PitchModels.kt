package com.a3322505a.guitarlearning.audio

import com.a3322505a.guitarlearning.core.FretPosition
import kotlin.math.pow

data class MidiPitch(
    val noteNumber: Int,
) {
    init {
        require(noteNumber in 0..127) { "MIDI note number must be between 0 and 127" }
    }

    val frequencyHz: Double
        get() = 440.0 * 2.0.pow((noteNumber - 69) / 12.0)
}

enum class PitchPlaybackStyle {
    SEQUENCE,
    CHORD,
}

data class PitchCue(
    val pitches: List<MidiPitch>,
    val style: PitchPlaybackStyle = PitchPlaybackStyle.SEQUENCE,
) {
    init {
        require(pitches.isNotEmpty()) { "A pitch cue must contain at least one pitch" }
    }
}

interface PitchPlayer {
    fun play(cue: PitchCue)

    fun stop()

    fun release()
}

/** The single pitch authority shared by fretboard and symbolic mapping exercises. */
object PitchCatalog {
    private val cMajorMidi =
        mapOf(
            "C" to 60,
            "D" to 62,
            "E" to 64,
            "F" to 65,
            "G" to 67,
            "A" to 69,
            "B" to 71,
        )

    fun forFretPosition(position: FretPosition): MidiPitch = MidiPitch(com.a3322505a.guitarlearning.core.MusicFacts.midi(position.string, position.fret))

    fun forNaturalNote(note: String): MidiPitch = MidiPitch(requireNotNull(cMajorMidi[note]) { "Only C-major natural notes are supported" })
}
