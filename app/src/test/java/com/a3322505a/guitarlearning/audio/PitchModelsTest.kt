package com.a3322505a.guitarlearning.audio

import com.a3322505a.guitarlearning.core.GuitarCore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class PitchModelsTest {
    @Test
    fun midiPitchUsesEqualTemperamentWithA4At440Hz() {
        assertEquals(440.0, MidiPitch(69).frequencyHz, absoluteTolerance = 0.0001)
        assertEquals(261.6256, MidiPitch(60).frequencyHz, absoluteTolerance = 0.001)
    }

    @Test
    fun fretPositionsMapToTheirActualStringOctaves() {
        assertEquals(40, PitchCatalog.forFretPosition(GuitarCore.getFretPosition(6, 0)).noteNumber)
        assertEquals(64, PitchCatalog.forFretPosition(GuitarCore.getFretPosition(1, 0)).noteNumber)
        assertEquals(48, PitchCatalog.forFretPosition(GuitarCore.getFretPosition(5, 3)).noteNumber)
        assertEquals(60, PitchCatalog.forFretPosition(GuitarCore.getFretPosition(2, 1)).noteNumber)
    }

    @Test
    fun twelfthFretIsExactlyOneOctaveAboveEachOpenString() {
        (1..6).forEach { string ->
            val open = PitchCatalog.forFretPosition(GuitarCore.getFretPosition(string, 0))
            val octave = PitchCatalog.forFretPosition(GuitarCore.getFretPosition(string, 12))
            assertEquals(open.noteNumber + 12, octave.noteNumber)
        }
    }

    @Test
    fun cMajorSymbolPitchesShareOneCanonicalOctave() {
        assertEquals(
            listOf(60, 62, 64, 65, 67, 69, 71),
            listOf("C", "D", "E", "F", "G", "A", "B")
                .map { PitchCatalog.forNaturalNote(it).noteNumber },
        )
    }

    @Test
    fun everyInteractiveTapBothPlaysAndForwardsThePosition() {
        val player = RecordingPitchPlayer()
        var forwarded = GuitarCore.getFretPosition(1, 0)
        val lowC = GuitarCore.getFretPosition(5, 3)
        val highC = GuitarCore.getFretPosition(2, 1)

        handleFretboardTap(lowC, player) { forwarded = it }
        assertSame(lowC, forwarded)
        handleFretboardTap(highC, player) { forwarded = it }
        assertSame(highC, forwarded)

        assertEquals(listOf(48, 60), player.cues.map { it.pitches.single().noteNumber })
    }

    private class RecordingPitchPlayer : PitchPlayer {
        val cues = mutableListOf<PitchCue>()

        override fun play(cue: PitchCue) {
            cues += cue
        }

        override fun stop() = Unit

        override fun release() = Unit
    }
}
