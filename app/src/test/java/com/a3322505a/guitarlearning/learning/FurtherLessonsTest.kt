package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.core.MusicFacts
import kotlin.random.Random
import kotlin.test.*

class FurtherLessonsTest {
    private val co = LearningCoordinator(LessonScheduler(Random(73)))
    private fun state(task: LearningTask): LearnerState {
        val session=LearningSession(startedAt=1)
        return LearnerState(active=ActiveTask(task.copy(id=newId())),currentNode=task.nodeId,sessionId=session.id,sessions=listOf(session))
    }
    private fun solveOne(s: LearnerState, now: Long): LearnerState {
        val a=s.active!!
        val rule=a.task.sequence.getOrNull(a.sequenceIndex) ?: a.task.constraint
        val ready=if(a.task.relation?.ear==true)co.playbackCompleted(s,a.task.id) else s
        return co.answer(ready,if(rule.kind==ConstraintKind.SYMBOL)null else AnswerEvaluator.validPositions(a.task,a.sequenceIndex).first(),rule.symbol,now)
    }
    private fun work(): LearnerState {
        var s=state(FurtherLessons.tasks("compose-eight").single())
        repeat(16) { s=solveOne(s,it+2L) }
        assertTrue(s.attempts.single().completed)
        return s
    }
    private fun ancestors(id: String): Set<String> = Curriculum.node(id).prerequisites.flatMap{listOf(it)+ancestors(it)}.toSet()

    @Test fun everyNewGoalHasReachableAnswersAndNoCircularPrerequisites() {
        assertEquals(25,FurtherLessons.ids.size)
        for(id in FurtherLessons.ids) {
            assertTrue(id !in ancestors(id))
            assertTrue(FurtherLessons.tasks(id).isNotEmpty(),id)
            for(t in FurtherLessons.tasks(id)) {
                (t.sequence.ifEmpty{listOf(t.constraint)}).forEachIndexed { i,r ->
                    if(r.kind==ConstraintKind.SYMBOL) assertTrue(r.symbol in t.options,"${t.skillId}:$i")
                    else assertTrue(AnswerEvaluator.validPositions(t,i).isNotEmpty(),"${t.skillId}:$i")
                }
                LearningCodec.decode(LearningCodec.encode(state(t)))
            }
        }
        assertTrue("full" !in ancestors("pentatonic-a"))
        assertTrue("full" !in ancestors("compose-eight"))
    }

    @Test fun motifQuestionHasARealRestAndEndingVariationChangesOnlyOneDimension() {
        val question=FurtherLessons.tasks("motif-answer").first()
        assertTrue(question.referenceScore!!.events.any{it.midi==null})
        val audio=TaskAudioPolicy.prompt(ActiveTask(question))!!
        assertEquals(AudioPurpose.PROMPT,audio.purpose)
        assertTrue(audio.events.any{it.pitches.isEmpty()&&it.durationMs==1200})
        val variant=FurtherLessons.tasks("motif-answer").last()
        assertEquals(listOf(57,60),variant.sequence.take(2).map{it.midi})
        assertEquals(variant.referenceScore!!.events.map{it.duration},variant.creationDurations)
        assertEquals(setOf(9),variant.sequence.last().allowedPitches.map{it%12}.toSet())
    }
    @Test fun spellingPitchSetsAndInversionsUseMusicalFacts() {
        assertEquals("B♭3",SpelledPitch.fromNaturalRoot(53,3,5).label)
        assertEquals("E♭3",SpelledPitch.fromNaturalRoot(48,2,3).label)
        val t=FurtherLessons.tasks("octave-build").first()
        val target=t.constraint.midi!!
        assertTrue(AnswerEvaluator.validPositions(t).all{MusicFacts.midi(it.string,it.fret)==target})
        assertFalse(AnswerEvaluator.matches(FurtherLessons.coordinate(target-12),t.constraint))
        FurtherLessons.tasks("chord-landings").forEach { task ->
            val valid=AnswerEvaluator.validPositions(task)
            assertTrue(valid.size>3)
            assertEquals(task.constraint.allowedPitches.intersect(task.range.positions().map{MusicFacts.midi(it.string,it.fret)}.toSet()),valid.map{MusicFacts.midi(it.string,it.fret)}.toSet())
        }
        FurtherLessons.tasks("triad-inversions").forEach { task ->
            val r=task.relation!!;val bass=(r.targetPitches.min()-r.referencePitches.single())%12
            assertEquals(when(bass){0->"原位";3,4->"第一转位";else->"第二转位"},task.constraint.symbol)
        }
    }

    @Test fun actualCompositionIsSavedPlayedAndTransposedWithItsRhythm() {
        val saved=LearningCodec.decode(LearningCodec.encode(work()))
        val original=saved.attempts.single()
        val audio=TaskAudioPolicy.demonstration(saved.active!!)!!
        assertEquals(16,audio.events.size)
        assertEquals(38400,audio.events.last().let{it.onsetMs+it.durationMs})
        val adapted=FurtherLessons.adaptOwnWork(saved,FurtherLessons.tasks("rework-key").single())
        assertEquals(original.task.creationDurations,adapted.creationDurations)
        val expected=original.inputs.map{MusicFacts.midi(it.coordinate!!.string,it.coordinate.fret)-5}
        assertEquals(expected,adapted.sequence.map{it.midi})
        assertEquals(expected,audio.events.map{it.pitches.single()-5})
        assertFalse(Curriculum.available(LearnerState(progress=ancestors("rework-key").associateWith{NodeProgress(masteredAt=1)}),Curriculum.node("rework-key")))
        val ui=TrainingUiAdapter.training(saved,false,AudioUiState())
        assertNull(ui.autoNextDelayMs)
        assertTrue(ui.canNext)
        assertTrue(ui.relation!!.demonstrationEnabled)
    }

    @Test fun earMemoryAndRhythmWaitForPlaybackAndKeepSeparateEvidence() {
        val t=FurtherLessons.tasks("ear-memory").first()
        var s=state(t)
        val c=AnswerEvaluator.validPositions(s.active!!.task).first()
        assertEquals(s,co.answer(s,c,now=2))
        assertEquals(BoardInteraction.DISABLED,TrainingUiAdapter.board(s.active!!,FingeringMode.COLORS).interaction)
        s=co.playbackCompleted(co.playbackStarted(s,s.active!!.task.id),s.active!!.task.id)
        s=co.answer(s,c,now=3)
        assertEquals(1,s.active!!.sequenceIndex)
        assertTrue(s.attempts.single().audioPlayed)
        val rhythm=FurtherLessons.tasks("ear-memory").last()
        assertNull(rhythm.notation)
        assertTrue(rhythm.skillId.contains("rhythm:"))
        val timed=TaskAudioPolicy.prompt(ActiveTask(rhythm))!!
        assertEquals(AudioPurpose.EAR,timed.purpose)
        assertEquals(5800,timed.events.last().let{it.onsetMs+it.durationMs})
        val changed=s.copy(physicalReports=listOf(PhysicalReport("pentatonic-a","t01","顺畅",4)))
        assertEquals(s.progress,LearningCodec.decode(LearningCodec.encode(changed)).progress)
        assertEquals(s.attempts,changed.attempts)
    }

    @Test fun allIncrementalCoursesFinishWithIndependentGoalEvidenceAndReload() {
        val own=work()
        for(id in FurtherLessons.ids) {
            var s=co.start(own.copy(active=null,sessionId=null,currentNode="g00",progress=ancestors(id).associateWith{NodeProgress(masteredAt=1)}),id,50).copy(reviewMode=true)
            var n=0
            while(!Curriculum.mastered(s,id)&&n<1200) {
                val task=s.active!!.task
                s=solveOne(s,n+100L)
                if(!Curriculum.mastered(s,id)&&s.active!!.phase==Phase.CORRECT) s=co.next(s,task.id,n+101L)
                if(n%50==0)s=LearningCodec.decode(LearningCodec.encode(s))
                n++
            }
            assertTrue(Curriculum.mastered(s,id),"$id stalled after $n inputs")
            assertTrue(s.attempts.filter{it.task.nodeId==id}.all{it.curriculumVersion==8})
            assertTrue(s.attempts.filter{it.task.guided}.none{it.independent||it.members.any{m->m.independent}})
        }
    }
}
