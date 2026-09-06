package com.a3322505a.guitarlearning.learning

import org.junit.Assert.*
import org.junit.Test

class ShortScorePilotTest {
    private fun profile() = LearnerState(progress = mapOf("tab01" to NodeProgress(1),"staff" to NodeProgress(1)),
        introductions = setOf("position:s1:f0","position:s1:f1","position:s1:f3"))
    @Test fun eightOriginalScoresHaveExactBarsAndMatchingTabStaffPlayback() {
        val pool = ShortScorePilot.pool(profile())
        val scores = (0..7).map { ShortScorePilot.score(it,pool) }
        assertEquals(8,scores.map { it.events.map { e -> e.midi } }.distinct().size)
        scores.forEach { score ->
            assertEquals(32,score.events.sumOf { it.duration })
            assertEquals(score.notation(NotationKind.TAB).pitches,score.notation(NotationKind.STAFF).pitches)
            assertEquals(score.notes.map { it.midi },score.playback().flatMap { it.pitches })
            assertEquals(9600,score.playback().last().let { it.onsetMs + it.durationMs })
            assertTrue(score.events.filter { it.midi == null }.all { it.coordinate == null })
        }
    }
    @Test fun slowPilotRetainsWrongFirstAnswerAndRestDoesNotCreateATarget() {
        val co = LearningCoordinator()
        var s = ShortScorePilot.begin(profile(),PilotMode.SLOW,1)
        val task = s.active!!.task
        assertNull(TaskAudioPolicy.prompt(s.active!!))
        assertEquals(8,task.sequence.size)
        s = co.answer(s,coordinate = Coordinate(6,0),now=2)
        while (s.active!!.sequenceIndex < task.sequence.size) s = co.answer(s,coordinate = task.sequence[s.active!!.sequenceIndex].coordinate,now=3)
        assertEquals(Phase.CORRECTED,s.active!!.phase)
        s = ShortScorePilot.finish(s.copy(pilot=s.pilot!!.copy(elapsedMs=4500)),4)
        assertEquals(7,s.pilotResults.single().firstCorrect)
        assertEquals(4500,s.pilotResults.single().elapsedMs)
        assertNull(s.pilot)
        assertEquals(1,ShortScorePilot.nextClip(s,PilotMode.SLOW))
    }
    @Test fun guitarSelfReportIsSeparateAndOriginalTaskSurvivesBackup() {
        val co = LearningCoordinator()
        val original = co.start(profile(),"g00",1)
        val started = ShortScorePilot.begin(original,PilotMode.GUITAR,2)
        val restored = LearningCodec.decode(LearningCodec.encode(started))
        assertEquals(started,ShortScorePilot.begin(restored,PilotMode.GUITAR,3))
        assertEquals(restored,co.answer(restored,coordinate=Coordinate(1,0),now=4))
        val done = ShortScorePilot.finish(restored,5,"有停顿","二分音符")
        assertEquals(original.active,done.active)
        assertEquals(original.sessionId,done.sessionId)
        assertEquals(0,done.pilotResults.single().firstCorrect)
        assertEquals("有停顿",done.pilotResults.single().rating)
        assertEquals(original.attempts,done.attempts)
    }
}
