package com.a3322505a.guitarlearning.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.Executors
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sin

private const val SAMPLE_RATE = 44_100

/** One output owner. Cancellation pauses immediately; only the worker releases its track. */
class AndroidPitchPlayer(
    private val context: Context? = null,
    private val onError: (Exception) -> Unit = {},
) : PitchPlayer, PlaybackOutput {
    private val sampler = GuitarSampler { root -> requireNotNull(context) { "缺少音源上下文" }.assets.open("guitar/$root.pcm").use { it.readBytes() } }
    private val executor = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private class Pending(val request: PlaybackRequest, val callback: (PlaybackEvent) -> Unit) {
        var terminal = false
        var started = false
    }
    private var pending: Pending? = null
    private var activeTrack: AudioTrack? = null
    private var released = false

    override fun play(cue: PitchCue) = playSequence(listOf(cue))

    /** Legacy adapter; current learning uses the request/event boundary exclusively. */
    fun playSequence(cues: List<PitchCue>, onComplete: () -> Unit = {}) {
        play(PlaybackRequest(UUID.randomUUID().toString(), cues)) { event ->
            when (event.status) {
                PlaybackStatus.COMPLETED -> onComplete()
                PlaybackStatus.FAILED -> onError(IllegalStateException(event.detail))
                else -> Unit
            }
        }
    }

    override fun play(request: PlaybackRequest, onEvent: (PlaybackEvent) -> Unit) {
        synchronized(lock) {
            if (released) { onEvent(PlaybackEvent(request.requestId, PlaybackStatus.FAILED, "播放器已关闭")); return }
            cancelCurrent()
            val work = Pending(request, onEvent)
            pending = work
            Log.d("GuitarAudio", "request=${request.requestId} midi=${request.cues.flatMap { it.pitches }.map { it.noteNumber }}")
            executor.execute {
                try {
                    if (!isCurrent(work)) return@execute
                    val manager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    check(manager == null || manager.getStreamVolume(AudioManager.STREAM_MUSIC) > 0) { "媒体音量为 0，请调节媒体音量后重试" }
                    val tones = request.cues.flatMap { cue ->
                        if (cue.style == PitchPlaybackStyle.CHORD) listOf(cue.pitches) else cue.pitches.map { listOf(it) }
                    }
                    tones.forEachIndexed { index, pitches ->
                        if (!playTone(pitches, work)) return@execute
                        if (index < tones.lastIndex && !gap(work)) return@execute
                    }
                    synchronized(lock) { if (isCurrent(work)) finish(work, PlaybackStatus.COMPLETED) }
                } catch (_: InterruptedException) {
                    synchronized(lock) { if (!work.terminal) finish(work, PlaybackStatus.CANCELLED) }
                    Thread.currentThread().interrupt()
                } catch (error: Exception) {
                    synchronized(lock) { if (isCurrent(work)) finish(work, PlaybackStatus.FAILED, error.message ?: "音频输出失败") }
                }
            }
        }
    }

    override fun stop() { synchronized(lock) { cancelCurrent() } }
    override fun release() {
        synchronized(lock) { if (released) return; cancelCurrent(); released = true }
        executor.shutdownNow()
    }

    private fun finish(work: Pending, status: PlaybackStatus, detail: String? = null) {
        if (work.terminal) return
        work.terminal = true
        if (pending === work) pending = null
        Log.d("GuitarAudio", "request=${work.request.requestId} $status ${detail.orEmpty()}")
        work.callback(PlaybackEvent(work.request.requestId, status, detail))
    }
    private fun cancelCurrent() {
        // No release here: the worker may still be writing/reading this track.
        runCatching { activeTrack?.pause() }
        pending?.let { finish(it, PlaybackStatus.CANCELLED) }
    }
    private fun isCurrent(work: Pending): Boolean = synchronized(lock) { !released && pending === work && !work.terminal }
    private fun gap(work: Pending): Boolean {
        val until = SystemClock.elapsedRealtime() + work.request.gapMs
        while (isCurrent(work) && SystemClock.elapsedRealtime() < until) Thread.sleep(8)
        return isCurrent(work)
    }
    private fun playTone(pitches: List<MidiPitch>, work: Pending): Boolean {
        if (!isCurrent(work)) return false
        val pcm = renderPcm(pitches, work.request.toneDurationMs)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(pcm.size * Short.SIZE_BYTES).setTransferMode(AudioTrack.MODE_STATIC).build()
        try {
            synchronized(lock) {
                if (!isCurrent(work)) return false
                activeTrack = track
                // MODE_STATIC starts in STATE_NO_STATIC_DATA until its first successful write.
                check(track.state != AudioTrack.STATE_UNINITIALIZED) { "音轨初始化失败" }
                val written = track.write(pcm, 0, pcm.size)
                check(written == pcm.size) { "音轨写入失败 ($written/${pcm.size})" }
                check(track.state == AudioTrack.STATE_INITIALIZED) { "音轨数据尚未就绪" }
                track.play()
                check(track.playState == AudioTrack.PLAYSTATE_PLAYING) { "音轨未启动" }
                if (!work.started) {
                    work.started = true
                    work.callback(PlaybackEvent(work.request.requestId, PlaybackStatus.STARTED))
                }
            }
            val start = SystemClock.elapsedRealtime()
            while (isCurrent(work)) {
                val frames = track.playbackHeadPosition.toLong() and 0xffffffffL
                when (playbackProgress(frames, pcm.size, SystemClock.elapsedRealtime() - start, work.request.toneDurationMs + 2500L)) {
                    PlaybackStatus.COMPLETED -> {
                        Log.d("GuitarAudio", "request=${work.request.requestId} frames=$frames/${pcm.size} route=${track.routedDevice?.type}")
                        return isCurrent(work)
                    }
                    PlaybackStatus.FAILED -> error("音轨播放超时 ($frames/${pcm.size})，请重试并检查媒体输出设备")
                    else -> Thread.sleep(8)
                }
            }
            return false
        } finally {
            synchronized(lock) {
                if (activeTrack === track) activeTrack = null
                runCatching { track.pause() }
                track.release()
            }
        }
    }

    private fun renderPcm(pitches: List<MidiPitch>, durationMs: Int): ShortArray = sampler.render(pitches, durationMs, SAMPLE_RATE)
}
