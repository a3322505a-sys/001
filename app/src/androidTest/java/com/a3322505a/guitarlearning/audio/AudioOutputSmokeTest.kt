package com.a3322505a.guitarlearning.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Runs on the existing API 35 emulator. Output completion is not a human listening test. */
@RunWith(AndroidJUnit4::class)
class AudioOutputSmokeTest {
    @Test fun verifyStaticAudioPlaybackAndCancellation() {
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(31752).setTransferMode(AudioTrack.MODE_STATIC).build()
        try {
            // Regression: alpha10 rejected exactly this healthy pre-write state.
            assertEquals(AudioTrack.STATE_NO_STATIC_DATA, track.state)
            assertEquals(15876, track.write(ShortArray(15876), 0, 15876))
            assertEquals(AudioTrack.STATE_INITIALIZED, track.state)
        } finally { track.release() }

        val output = AndroidPitchPlayer(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        val events = CopyOnWriteArrayList<PlaybackEvent>()
        val started = CountDownLatch(1)
        val completed = CountDownLatch(1)
        try {
            output.play(PlaybackRequest("old", listOf(PitchCue(List(8) { MidiPitch(59) })))) {
                events += it
                if (it.status == PlaybackStatus.STARTED) started.countDown()
            }
            assertTrue("old output did not start: $events", started.await(5, TimeUnit.SECONDS))
            output.play(PlaybackRequest("new", listOf(PitchCue(listOf(MidiPitch(59), MidiPitch(47)))))) {
                events += it
                if (it.status == PlaybackStatus.COMPLETED || it.status == PlaybackStatus.FAILED) completed.countDown()
            }
            assertTrue("new output timed out: $events", completed.await(10, TimeUnit.SECONDS))
            assertTrue(events.any { it.requestId == "old" && it.status == PlaybackStatus.CANCELLED })
            assertFalse(events.any { it.requestId == "old" && it.status == PlaybackStatus.COMPLETED })
            assertTrue("actual frames did not complete: $events", events.any { it.requestId == "new" && it.status == PlaybackStatus.COMPLETED })
            assertFalse(events.any { it.status == PlaybackStatus.FAILED })
        } finally { output.release() }
    }
}
