package com.a3322505a.guitarlearning.audio

import kotlin.math.*

/** Offline CC0 FreePats multisamples; 22050 Hz, mono, signed little-endian PCM16. */
class GuitarSampler(private val load: (Int) -> ByteArray) {
    private val roots = listOf(36, 41, 45, 48, 52, 55, 59, 64, 67, 71, 74, 80, 85)
    private val cache = mutableMapOf<Int, DoubleArray>()
    private fun sample(root: Int): DoubleArray = cache.getOrPut(root) {
        val bytes = load(root)
        require(bytes.size >= 2 && bytes.size % 2 == 0)
        val values = DoubleArray(bytes.size / 2) { i ->
            ((bytes[2 * i].toInt() and 255) or (bytes[2 * i + 1].toInt() shl 8)).toShort() / 32768.0
        }
        val head = values.take(minOf(values.size, 7938))
        val rms = sqrt(head.sumOf { it * it } / head.size).coerceAtLeast(0.0001)
        val peak = values.maxOf { abs(it) }.coerceAtLeast(0.0001)
        val gain = minOf(0.23 / rms, 0.88 / peak)
        values.map { it * gain }.toDoubleArray()
    }

    fun render(pitches: List<MidiPitch>, durationMs: Int, sampleRate: Int = 44100): ShortArray {
        require(pitches.isNotEmpty() && durationMs > 0)
        val voices = pitches.map { p ->
            val root = roots.minBy { abs(it - p.noteNumber) }
            sample(root) to (22050.0 / sampleRate * 2.0.pow((p.noteNumber - root) / 12.0))
        }
        val count = sampleRate * durationMs / 1000
        val release = minOf(count / 3, sampleRate * 35 / 1000).coerceAtLeast(1)
        val mix = DoubleArray(count) { i ->
            val value = voices.sumOf { (wave, step) ->
                val at = i * step
                val index = at.toInt()
                if (index >= wave.lastIndex) 0.0 else wave[index] + (wave[index + 1] - wave[index]) * (at - index)
            } / sqrt(voices.size.toDouble())
            val envelope = minOf(1.0, i / (sampleRate * 0.002), (count - i - 1).toDouble() / release).coerceAtLeast(0.0)
            value * envelope
        }
        // Shared mix gain preserves voicing; headroom prevents integer wrap or chord clipping.
        val gain = minOf(1.0, 0.92 / mix.maxOf { abs(it) }.coerceAtLeast(0.0001))
        return ShortArray(count) { (mix[it] * gain * Short.MAX_VALUE).roundToInt().toShort() }
    }
}
