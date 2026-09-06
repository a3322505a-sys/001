package com.a3322505a.guitarlearning.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

private const val SAMPLE_RATE = 44_100
private const val TONE_DURATION_MS = 360
private const val SEQUENCE_GAP_MS = 80L
private const val ATTACK_MS = 12
private const val RELEASE_MS = 45
private const val OUTPUT_GAIN = 0.24

/** A small reusable PCM player with a clean sine tone and no page-level audio logic. */
class AndroidPitchPlayer(private val onError: (Exception) -> Unit = {}) : PitchPlayer {
    private val executor = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private var generation = 0
    private var activeTrack: AudioTrack? = null
    private var released = false

    override fun play(cue: PitchCue) = playSequence(listOf(cue))

    /** Reports only a full cue duration without cancellation or an output error. */
    fun playSequence(cues: List<PitchCue>, onComplete: () -> Unit = {}) {
        require(cues.isNotEmpty())
        val token = synchronized(lock) {
            if (released) return
            generation += 1
            stopActiveTrack()
            generation
        }
        executor.execute {
            try {
                cues.forEachIndexed { cueIndex, cue ->
                    when (cue.style) {
                        PitchPlaybackStyle.SEQUENCE -> cue.pitches.forEachIndexed { index, pitch ->
                            if (!playTone(listOf(pitch), token)) return@execute
                            if (index < cue.pitches.lastIndex && isCurrent(token)) Thread.sleep(SEQUENCE_GAP_MS)
                        }
                        PitchPlaybackStyle.CHORD -> if (!playTone(cue.pitches, token)) return@execute
                    }
                    if (cueIndex < cues.lastIndex && isCurrent(token)) Thread.sleep(SEQUENCE_GAP_MS)
                }
                if (isCurrent(token)) onComplete()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (error: Exception) {
                if (isCurrent(token)) onError(error)
            }
        }
    }

    override fun stop() {
        synchronized(lock) {
            generation += 1
            stopActiveTrack()
        }
    }

    override fun release() {
        synchronized(lock) {
            if (released) return
            released = true
            generation += 1
            stopActiveTrack()
        }
        executor.shutdownNow()
    }

    private fun playTone(
        pitches: List<MidiPitch>,
        token: Int,
    ): Boolean {
        if (!isCurrent(token)) return false
        val pcm = renderPcm(pitches)
        val track =
            AudioTrack
                .Builder()
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                ).setAudioFormat(
                    AudioFormat
                        .Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                ).setBufferSizeInBytes(pcm.size * Short.SIZE_BYTES)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        synchronized(lock) {
            if (!isCurrent(token)) {
                track.release()
                return false
            }
            activeTrack = track
        }
        try {
            check(track.state == AudioTrack.STATE_INITIALIZED) { "Audio output was not initialized" }
            check(track.write(pcm, 0, pcm.size) == pcm.size) { "Audio output rejected samples" }
            track.play()
            check(track.playState == AudioTrack.PLAYSTATE_PLAYING) { "Audio output did not start" }
            Thread.sleep(TONE_DURATION_MS.toLong())
        } catch (error: IllegalStateException) {
            if (isCurrent(token)) throw error
            return false
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return false
        } finally {
            synchronized(lock) {
                if (activeTrack === track) activeTrack = null
            }
            safelyRelease(track)
        }
        return isCurrent(token)
    }

    private fun renderPcm(pitches: List<MidiPitch>): ShortArray {
        val sampleCount = SAMPLE_RATE * TONE_DURATION_MS / 1_000
        val attackSamples = SAMPLE_RATE * ATTACK_MS / 1_000
        val releaseSamples = SAMPLE_RATE * RELEASE_MS / 1_000
        return ShortArray(sampleCount) { sampleIndex ->
            val time = sampleIndex.toDouble() / SAMPLE_RATE
            val wave =
                pitches.sumOf { pitch ->
                    sin(2.0 * PI * pitch.frequencyHz * time)
                } / pitches.size
            val envelope =
                when {
                    sampleIndex < attackSamples -> sampleIndex.toDouble() / attackSamples
                    sampleIndex >= sampleCount - releaseSamples ->
                        (sampleCount - sampleIndex - 1).toDouble() / releaseSamples
                    else -> 1.0
                }.coerceIn(0.0, 1.0)
            (wave * envelope * OUTPUT_GAIN * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun isCurrent(token: Int): Boolean =
        synchronized(lock) {
            !released && generation == token
        }

    private fun stopActiveTrack() {
        activeTrack?.let(::safelyRelease)
        activeTrack = null
    }

    private fun safelyRelease(track: AudioTrack) {
        runCatching { track.pause() }
        runCatching { track.flush() }
        runCatching { track.release() }
    }
}
