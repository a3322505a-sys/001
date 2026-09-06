package com.a3322505a.guitarlearning.audio

/** Device boundary. Cues contain actual MIDI pitches, never tasks or answer constraints. */
data class PlaybackRequest(
    val requestId: String,
    val cues: List<PitchCue>,
    val toneDurationMs: Int = 360,
    val gapMs: Int = 80,
) {
    init { require(cues.isNotEmpty() && toneDurationMs in 50..5000 && gapMs in 0..2000) }
}

enum class PlaybackStatus { STARTED, COMPLETED, CANCELLED, FAILED }
data class PlaybackEvent(val requestId: String, val status: PlaybackStatus, val detail: String? = null)

interface PlaybackOutput {
    fun play(request: PlaybackRequest, onEvent: (PlaybackEvent) -> Unit)
    fun stop()
    fun release()
}

/** Elapsed time only bounds failure; success requires every expected output frame. */
internal fun playbackProgress(frames: Long, expected: Int, elapsedMs: Long, timeoutMs: Long): PlaybackStatus? = when {
    frames >= expected -> PlaybackStatus.COMPLETED
    elapsedMs >= timeoutMs -> PlaybackStatus.FAILED
    else -> null
}
