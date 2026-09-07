package com.a3322505a.guitarlearning.learning

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ExperiencePolicyTest {
    private val scheduler = LessonScheduler(Random(81))
    private val co = LearningCoordinator(scheduler)
    private fun profile() = LearnerState(progress = Curriculum.nodes.associate { it.id to NodeProgress(1) },
        introductions = Curriculum.nodes.flatMap { it.positions }.map { "position:${it.id}" }.toSet())
    private fun correct(s: LearnerState, now: Long): LearnerState {
        val t = s.active!!.task
        return if (t.options.isNotEmpty()) co.answer(s, symbol = t.constraint.symbol, now = now)
        else co.answer(s, coordinate = AnswerEvaluator.validPositions(t).first(), now = now)
    }
    @Test fun monotonicClockDoesNotRestartOnRedrawAndInterruptionsAreNotSlow() {
        val clock = ResponseClock()
        clock.displayed("a", 1000)
        clock.displayed("a", 3000)
        assertEquals(2500L, clock.sample("a", 3500).first)
        clock.interrupt()
        assertEquals(TimingQuality.INTERRUPTED, clock.stop("a", 100000).second)
        clock.displayed("b", 110000)
        assertEquals(TimingQuality.TOO_FAST, clock.sample("b", 110249).second)
        assertEquals(TimingQuality.VALID, clock.sample("b", 110250).second)
        assertEquals(TimingQuality.UNAVAILABLE, clock.sample("a", 120000).second)
        clock.displayed("restored", 200000, true)
        assertEquals(TimingQuality.INTERRUPTED, clock.sample("restored", 200500).second)
    }
    @Test fun deadlineSurvivesCorrectAnswerBackSerializationAndCannotDuplicate() {
        var s = co.startRegion(profile(), "LOW", 1000)
        val task = scheduler.makePosition("p09", Coordinate(3,4), Direction.POSITION_TO_NOTE, TaskSource.MAIN).copy(roundSlot = 4)
        s = AdaptiveEvidence.present(s, task, 2000)
        val event = LongThought(s.active!!.task, 10000, 8000)
        s = ResponseTiming.long(s, event)
        s = ResponseTiming.long(s, event)
        s = ResponseTiming.record(s, task.id, 9000L to TimingQuality.VALID)
        s = correct(s, 11000)
        assertEquals(true, s.attempts.last().firstCorrect)
        assertFalse(s.attempts.last().independent)
        assertFalse(ResponseTiming.timely(s, s.attempts.last()))
        assertEquals(1, s.longThoughts.size)
        s = LearningCodec.decode(LearningCodec.encode(co.end(s, 12000)))
        assertEquals(1, s.longThoughts.size)
        s = co.startRegion(s, "LOW", 13000)
        assertTrue(s.active!!.task.adaptive!!.scaffolded)
        assertEquals(task.coordinate, s.active!!.task.coordinate)
    }
    @Test fun allTwelveSlotsStayProtectedAndOneTargetsSuccessCannotReleaseAnother() {
        var s = co.startRegion(profile(), "LOW", 1000)
        val tasks = listOf(Coordinate(4,2), Coordinate(3,4)).map { c -> scheduler.makePosition("p09", c, Direction.POSITION_TO_NOTE, TaskSource.MAIN) }
        tasks.forEachIndexed { i,t -> s = RegionProtection.protect(s,t,2000L+i) }
        val second = RegionProtection.key(tasks[1])
        for (slot in 1..12) {
            val t = RegionProtection.next(s, Random(slot), 3000L+slot*1000)!!
            assertTrue(t.adaptive!!.scaffolded || t.adaptive.originalProbe)
            assertTrue(t.adaptive.options.isEmpty())
            if (t.adaptive.scaffolded) assertEquals(7,t.options.size)
            s = AdaptiveEvidence.present(s,t.copy(roundSlot=slot),3000L+slot*1000)
            s = correct(s,3500L+slot*1000)
        }
        assertNull(s.positionProtections.getValue(second).resolvedAt)
        assertEquals(2,s.positionProtections.size)
    }
    @Test fun rawSevenCorrectWithOneLongThoughtIsSixOfSevenTimely() {
        var s = co.startRegion(profile(), "LOW", 1000)
        for (slot in 1..12) {
            val t = s.active!!.task
            s = ResponseTiming.record(s,t.id,1000L to TimingQuality.VALID)
            if (slot == 5) s = s.copy(longThoughts = s.longThoughts + (t.id to LongThought(t,slot*10000L,ExperiencePolicy.deadline(t.direction))))
            // Isolate round arithmetic from scheduling protection after this fact.
            s = correct(s,slot*10000L+1000)
            if (slot < 12) {
                val next = scheduler.makePosition("p09",Coordinate((slot%6)+1,0),AdaptiveEvidence.positionDirections[slot%2],TaskSource.MAIN).copy(roundSlot=slot+1)
                s = AdaptiveEvidence.present(s,next,slot*10000L+2000)
            }
        }
        val r = RoundExperience.summarize(s,130000,true)!!
        assertEquals(7,r.standard)
        assertEquals(6,r.timely)
        assertTrue(r.protected)
    }
    @Test fun geometryScalesAsOneInstrumentAndEveryCellHasUniqueHitCenter() {
        for (height in listOf(96f,160f,280f)) for(last in listOf(4,8,12)) {
            val g=TeachingGeometry(0,last)
            val layout=g.layout(height,40f)
            assertEquals(layout.height*1.05f,layout.left,.001f)
            assertTrue(layout.width*(g.right(last)-g.left(last))>=39.99f)
            for(string in 1..6) for(fret in 0..last) assertEquals(Coordinate(string,fret),g.at(g.center(fret),g.stringCenter(string)))
        }
    }
    @Test fun highScoreWithoutCoverageOrRetentionCannotRecommendMiddle() {
        val s=profile().copy(roundEvidence=(1..6).map { RoundEvidence("s$it","LOW",it*1000L,true,"same",7,7,false,0,3,3) })
        assertFalse(MiddleReadiness.ready(s,100000))
        assertFalse(Fluency.ready(s,"position:s1:f0:POSITION_TO_NOTE",100000))
        assertEquals(20,ExperiencePolicy.FLUENT_WINDOW)
        assertEquals(1,ExperiencePolicy.FLUENT_ALLOWED_ERRORS)
    }
    @Test fun fluencyAllowsOneIsolatedErrorButNotTwoOrMissingTiming() {
        val target=Coordinate(3,4)
        var s=profile().copy(sessions=listOf(LearningSession("first",1000),LearningSession("second",50_000_000)))
        repeat(20) { cycle ->
            val session=if(cycle<10) "first" else "second"
            val base=(if(cycle<10) 1000L else 50_000_000L)+(cycle%10)*3000
            for((j,c) in listOf(target,Coordinate(4,2),Coordinate(5,3)).withIndex()) {
                val task=scheduler.makePosition("p09",c,Direction.POSITION_TO_NOTE,TaskSource.MAIN).copy(evidenceVersion=1)
                val at=base+j*1000
                val good=cycle!=4 || c!=target
                val inputs=listOf(InputRecord(at,symbol=if(good) task.constraint.symbol else task.options.first { it!=task.constraint.symbol },result=if(good) ClickResult.CORRECT else ClickResult.WRONG)) +
                    if(good) emptyList() else listOf(InputRecord(at+1,symbol=task.constraint.symbol,result=ClickResult.CORRECTION))
                s=s.copy(attempts=s.attempts+Attempt(task,session,s.attempts.size+1,at,"day",good,0,!good,true,inputs,true,firstUnassisted=true),
                    knowledgeExposures=s.knowledgeExposures+KnowledgeExposure(task.id,AdaptiveEvidence.positionTarget(c),at+1),
                    responseObservations=s.responseObservations+(task.id to ResponseObservation(task,session,1000,TimingQuality.VALID)))
            }
        }
        val unit=AdaptiveEvidence.positionUnit(target,Direction.POSITION_TO_NOTE)
        val now=51_000_000L
        assertTrue(Fluency.ready(s,unit,now))
        val secondError=s.attempts.first { it.task.coordinate==target && it.firstCorrect==true }
        val bad=s.copy(attempts=s.attempts.map { if(it.task.id==secondError.task.id) it.copy(firstCorrect=false,inputs=listOf(it.inputs.first().copy(result=ClickResult.WRONG))) else it })
        assertFalse(Fluency.ready(bad,unit,now))
        assertFalse(Fluency.ready(s.copy(responseObservations=emptyMap()),unit,now))
        val t=scheduler.makePosition("p09",target,Direction.POSITION_TO_NOTE,TaskSource.MAIN)
        assertTrue(RegionProtection.protect(s,t,now).positionProtections.values.single().isolated)
        assertFalse(ResponseTiming.long(s,LongThought(t,now,8000)).positionProtections.values.single().isolated)
    }

}
