package com.a3322505a.guitarlearning.audio

import org.junit.Test
import org.junit.Assert.*
import java.io.File
import kotlin.math.*

class GuitarSamplerTest {
    private val sampler = GuitarSampler { File("src/main/assets/guitar/$it.pcm").readBytes() }
    @Test fun samplesCoverChromaticRangeAndChordsWithoutClippingOrSilentOutput() {
        for (midi in 40..85) {
            val pcm = sampler.render(listOf(MidiPitch(midi)), 360)
            assertEquals(15876, pcm.size)
            assertTrue("silent $midi", sqrt(pcm.map { (it / 32768.0).pow(2) }.average()) > 0.04)
            assertTrue(pcm.maxOf { abs(it.toInt()) } <= 30147)
            assertEquals(0, pcm.last().toInt())
        }
        for (notes in listOf(listOf(45,52,57,60,64), listOf(43,50,55))) {
            val chord = sampler.render(notes.map(::MidiPitch), 360)
            assertTrue(chord.maxOf { abs(it.toInt()) } <= 30147)
            assertTrue(sqrt(chord.map { (it / 32768.0).pow(2) }.average()) > 0.06)
        }
    }
}
