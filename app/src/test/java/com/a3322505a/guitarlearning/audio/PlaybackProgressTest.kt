package com.a3322505a.guitarlearning.audio

import org.junit.Test
import org.junit.Assert.*

class PlaybackProgressTest {
    @Test fun elapsedDurationDoesNotProveThatOutputCompleted() {
        assertNull(playbackProgress(0, 15876, 360, 2860))
        assertNull(playbackProgress(15875, 15876, 800, 2860))
        assertEquals(PlaybackStatus.FAILED, playbackProgress(15875, 15876, 2860, 2860))
        assertEquals(PlaybackStatus.COMPLETED, playbackProgress(15876, 15876, 700, 2860))
    }
}
