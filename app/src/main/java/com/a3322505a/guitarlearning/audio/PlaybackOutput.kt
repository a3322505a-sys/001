package com.a3322505a.guitarlearning.audio

/** Device boundary. Cues contain actual MIDI pitches, never tasks or answer constraints. */
data class PlaybackRequest(
    val requestId: String,
    val cues: List<PitchCue>,
    val toneDurationMs: Int = 360,
    val gapMs: Int = 80,
    val events: List<TimedPitchEvent> = emptyList(),
    val startAtMs: Int = 0,
) {
    init { require((cues.isNotEmpty() || events.isNotEmpty()) && startAtMs >= 0 && toneDurationMs in 50..5000 && gapMs in 0..2000) }
}

enum class PlaybackStatus { STARTED, COMPLETED, CANCELLED, FAILED }
data class PlaybackEvent(val requestId: String, val status: PlaybackStatus, val detail: String? = null)

interface PlaybackOutput {
    fun play(request: PlaybackRequest, onEvent: (PlaybackEvent) -> Unit)
    fun positionMs(): Int = 0
    fun stop()
    fun release()
}

/** Elapsed time only bounds failure; success requires every expected output frame. */
internal fun playbackProgress(frames: Long, expected: Int, elapsedMs: Long, timeoutMs: Long): PlaybackStatus? = when {
    frames >= expected -> PlaybackStatus.COMPLETED
    elapsedMs >= timeoutMs -> PlaybackStatus.FAILED
    else -> null
}

/** Empty pitches is an explicit rest, not a fake fretboard target. */
data class TimedPitchEvent(val onsetMs: Int, val durationMs: Int, val pitches: List<Int>) {
    init { require(onsetMs >= 0 && durationMs > 0 && pitches.all { it in 0..127 }) }
}
