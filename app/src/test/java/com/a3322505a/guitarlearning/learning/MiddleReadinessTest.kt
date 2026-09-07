package com.a3322505a.guitarlearning.learning

import org.junit.Assert.*
import org.junit.Test

class MiddleReadinessTest {
    private fun qualified(): LearnerState {
        var s = LearnerState(progress = Curriculum.nodes.associate { it.id to NodeProgress(1) },
            introductions = AdaptiveEvidence.targets(FretboardRegion.LOW).map { AdaptiveEvidence.positionTarget(it) }.toSet())
        val positions = AdaptiveEvidence.targets(FretboardRegion.LOW)
        val scheduler = LessonScheduler()
        fun add(session: String, c: Coordinate, d: Direction, at: Long, slot: Int? = null) {
            val t = scheduler.makePosition("p09",c,d,TaskSource.MAIN).copy(evidenceVersion=1,roundSlot=slot)
            val input = if (t.options.isEmpty()) InputRecord(at,coordinate=c,result=ClickResult.CORRECT) else InputRecord(at,symbol=t.constraint.symbol,result=ClickResult.CORRECT)
            val a=Attempt(t,session,s.attempts.size+1,at,"day",true,0,false,true,listOf(input),true,firstUnassisted=true)
            s=s.copy(attempts=s.attempts+a,knowledgeExposures=s.knowledgeExposures+KnowledgeExposure(t.id,AdaptiveEvidence.positionTarget(c),at),
                responseObservations=s.responseObservations+(t.id to ResponseObservation(t,session,1000,TimingQuality.VALID)))
        }
        repeat(4) { cycle ->
            val session="base$cycle"; val start=1000L+cycle*ExperiencePolicy.RETENTION_MS
            s=s.copy(sessions=s.sessions+LearningSession(session,start,start+100000,"learning"))
            for((di,d) in AdaptiveEvidence.positionDirections.withIndex()) for((i,c) in positions.withIndex()) add(session,c,d,start+(di*18+i)*1000L)
        }
        repeat(6) { round ->
            val id="round$round";val start=1000L+(round+5)*ExperiencePolicy.RETENTION_MS
            s=s.copy(sessions=s.sessions+LearningSession(id,start,start+100000,"region","natural","LOW"))
            repeat(7) { i -> add(id,positions.first { it.string == i%6+1 },AdaptiveEvidence.positionDirections[(i+round)%2],start+i*1000L,4+i) }
            s=s.copy(roundEvidence=s.roundEvidence+RoundEvidence(id,"LOW",start+100000,true,"standard",7,7,false,0,3,3))
        }
        return s
    }
    @Test fun allGatesPermitRecommendationButOneMissingDirectionTimingBlocksIt() {
        val s=qualified();val now=s.roundEvidence.last().endedAt
        assertTrue(MiddleReadiness.ready(s,now))
        val noFindTiming=s.copy(responseObservations=s.responseObservations.mapValues { (_,o) -> if(o.task.direction==Direction.NOTE_TO_POSITION) o.copy(durationMs=null,quality=TimingQuality.INTERRUPTED) else o })
        assertFalse(MiddleReadiness.ready(noFindTiming,now))
        assertFalse(MiddleReadiness.ready(s.copy(roundEvidence=s.roundEvidence.dropLast(1)+s.roundEvidence.last().copy(protected=true)),now))
        val c=Coordinate(3,4);val t=LessonScheduler().makePosition("p09",c,Direction.POSITION_TO_NOTE,TaskSource.MAIN)
        assertFalse(MiddleReadiness.ready(RegionProtection.protect(s,t,now),now))
    }
    @Test fun retentionIsMeasuredSinceLastExposureEvenWhenNoAnswerWasSubmitted() {
        val s=qualified();val a=s.attempts.dropLast(1).last()
        assertTrue(MiddleReadiness.retained(s,a))
        val exposed=s.copy(knowledgeExposures=s.knowledgeExposures+KnowledgeExposure("peek",AdaptiveEvidence.positionTarget(a.task.coordinate!!),a.at-1000,true))
        assertFalse(MiddleReadiness.retained(exposed,a))
    }
}
