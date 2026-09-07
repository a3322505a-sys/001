package com.a3322505a.guitarlearning.learning

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ProgressionVisualsTest {
    private fun beginner(n:Int=2)=LearnerState(progress=listOf("g00","n00").associateWith { NodeProgress(1) },
        introductions=Curriculum.nodes.flatMap { it.positions }.take(n).map { AdaptiveEvidence.positionTarget(it) }.toSet())
    private class Run(seed:Int) {
        val scheduler=LessonScheduler(Random(seed)); val co=LearningCoordinator(scheduler);var now=1000L
        fun step(input:LearnerState,quality:TimingQuality=TimingQuality.VALID,ms:Long=1000):LearnerState {
            val t=input.active!!.task
            var s=ResponseTiming.record(input,t.id,ms to quality)
            now+=ms+100
            s=if(t.options.isEmpty()) co.answer(s,coordinate=AnswerEvaluator.validPositions(t).first(),now=now) else co.answer(s,symbol=t.constraint.symbol,now=now)
            s=co.next(s,t.id,++now)
            return s
        }
    }
    @Test fun fastFirstRoundActuallyExpandsAndSixRoundsDoNotCircleTheSamePool() {
        for(seed in 1..4) {
            val r=Run(seed);var s=r.co.startRegion(beginner(),"LOW",r.now)
            val historical=s.progress
            repeat(12) { s=r.step(s) }
            assertNull(s.active)
            assertEquals(12,s.attempts.size)
            assertTrue("seed $seed: ${s.attempts.map { it.task.coordinate to it.task.direction }}",s.attempts.any { it.task.coordinate !in Curriculum.node("p01").positions })
            assertTrue(s.attempts.any { it.task.introductionId!=null })
            repeat(5) {
                s=r.co.startRegion(LearningCodec.decode(LearningCodec.encode(s)),"LOW",++r.now)
                repeat(12) { s=r.step(s) }
            }
            assertTrue("seed $seed coverage ${s.introductions}",s.introductions.size>=8)
            assertEquals(historical["g00"],s.progress["g00"])
            assertFalse("trial does not grant stable fluency",MiddleReadiness.ready(s,r.now))
            assertTrue(s.attempts.filter { it.task.guided }.none { it.independent })
            assertEquals(72,s.attempts.size)
        }
    }
    @Test fun invalidOrSlowTimingCannotAdvanceSmallTaughtPool() {
        for(quality in listOf(TimingQuality.INTERRUPTED,TimingQuality.TOO_FAST,TimingQuality.UNAVAILABLE,TimingQuality.VALID)) {
            val r=Run(8);var s=r.co.startRegion(beginner(),"LOW",r.now)
            repeat(12) { s=r.step(s,quality,if(quality==TimingQuality.VALID) 7000 else 1000) }
            assertEquals(beginner().introductions,s.introductions)
        }
    }
    @Test fun tinyPoolsAndMultipleProtectionsReachSpacedRecoveryAcrossRestart() {
        for(n in 1..3) for(count in 1..minOf(n,3)) {
            val r=Run(n*10+count);var s=r.co.startRegion(beginner(n),"LOW",r.now)
            val coordinates=RegionTraining.known(s,"LOW").map { it.second }.take(count)
            coordinates.forEach { c ->
                val node=Curriculum.nodes.first { c in it.positions }.id
                s=RegionProtection.protect(s,r.scheduler.makePosition(node,c,Direction.POSITION_TO_NOTE,TaskSource.MAIN),++r.now)
            }
            val keys=s.positionProtections.keys
            repeat(90) {
                if(RegionProtection.active(s).isNotEmpty()) {
                    s=r.step(s)
                    if(s.active==null) s=r.co.startRegion(LearningCodec.decode(LearningCodec.encode(s)),"LOW",++r.now)
                }
            }
            assertTrue("pool $n protections $count: ${s.positionProtections}",keys.all { s.positionProtections.getValue(it).resolvedAt!=null })
            keys.forEach { key -> assertTrue(s.attempts.count { it.task.adaptive?.protectionKey==key && it.task.adaptive.originalProbe }>=3) }
            assertTrue(s.attempts.filter { it.task.adaptive?.scaffolded==true }.none { it.independent })
        }
    }
    @Test fun oldUnansweredTwoChoiceTaskRebuildsWithoutChangingHistory() {
        val r=Run(2);val s=r.co.startRegion(beginner(),"LOW",r.now)
        val old=r.scheduler.makePosition("p01",Coordinate(1,0),Direction.POSITION_TO_NOTE,TaskSource.MAIN).copy(options=listOf("E","F"),evidenceVersion=1)
        val raw=s.copy(active=ActiveTask(old))
        val restored=LearningCodec.decode(LearningCodec.encode(raw))
        assertEquals(NaturalRecognition.options,restored.active!!.task.options)
        assertEquals(old.id,restored.active!!.task.id)
        assertEquals(TimingQuality.INTERRUPTED,restored.responseObservations[old.id]!!.quality)
        val answered=r.co.answer(raw,symbol="E",now=2000)
        val kept=LearningCodec.decode(LearningCodec.encode(answered))
        assertEquals(listOf("E","F"),kept.attempts.last().task.options)
        assertFalse(NaturalRecognition.current(kept.attempts.last().task))
    }
    @Test fun chordRotationHasSameCellFactsIncludingOpenAndHighFrets() {
        for(first in listOf(0,3,9)) for(vertical in listOf(false,true)) {
            val g=ChordDiagramGeometry(first,if(first==0) 4 else first+3,vertical)
            for(string in 1..6) for(fret in listOf(0)+(g.first..g.last)) {
                val c=Coordinate(string,fret);val xy=g.center(c)
                assertEquals(c,g.at(xy.first,xy.second))
            }
            assertNull(g.at(-1f,-1f))
        }
        val horizontal=ChordDiagramGeometry(0,4,false)
        val vertical=ChordDiagramGeometry(0,4,true)
        assertTrue(horizontal.center(Coordinate(1,1)).second<horizontal.center(Coordinate(6,1)).second)
        assertTrue(vertical.center(Coordinate(6,1)).first<vertical.center(Coordinate(1,1)).first)
        val s=beginner().copy(chordVertical=true)
        assertTrue(LearningCodec.decode(LearningCodec.encode(s)).chordVertical)
        assertFalse(LearningCodec.decode(LearningCodec.encode(s).replace(",\"chordVertical\":true", "")).chordVertical)
        for(shape in listOf(ChordShapes.am,ChordShapes.fBarre)) {
            val task=ChordLessons.make(shape,"chord-am",TaskSource.DEMONSTRATION)
            val a=TrainingUiAdapter.training(s.copy(active=ActiveTask(task)),false,AudioUiState()).board!!
            val b=TrainingUiAdapter.training(s.copy(chordVertical=false,active=ActiveTask(task)),false,AudioUiState()).board!!
            assertEquals(a.chord,b.chord);assertEquals(a.viewId,b.viewId);assertEquals(a.answerPositions,b.answerPositions)
        }
    }
}
