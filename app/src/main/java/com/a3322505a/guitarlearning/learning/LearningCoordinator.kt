package com.a3322505a.guitarlearning.learning

import java.time.Instant
import java.time.ZoneId

/** Pure state transitions. The caller must commit the returned state before exposing it. */
class LearningCoordinator(private val scheduler: LessonScheduler = LessonScheduler()) {
    fun startRegion(state: LearnerState, regionId: String, now: Long): LearnerState {
        require(RegionTraining.available(state, regionId)) { "请先完成该区域前置内容。" }
        val inRegion = RegionSessions.active(state)
        if (inRegion && state.regionTraining?.regionId == regionId && state.active != null) return state.copy(queuedRegion = null)
        val savedRegions = if (inRegion) state.pausedRegions + (state.regionTraining!!.regionId to RegionSessions.capture(state)) else state.pausedRegions
        val outside = if (!inRegion && (state.active != null || state.sessionId != null)) RegionSessions.capture(state) else state.pausedTraining
        val base = RegionSessions.clear(state).copy(pausedTraining = outside, pausedRegions = savedRegions - regionId)
        val resume = savedRegions[regionId]
        return if (resume != null) RegionSessions.restore(base, resume) else activateRegion(base, regionId, now)
    }

    private fun activateRegion(state: LearnerState, regionId: String, now: Long): LearnerState {
        val run = RegionRun(regionId, (state.attempts.maxOfOrNull { it.ordinal } ?: 0) + 1, 0,
            adaptive = state.regionContinuations[regionId] ?: AdaptiveRun(), roundEnabled = true)
        val session = state.sessions.lastOrNull { it.id == state.sessionId && it.endedAt == null } ?: LearningSession(startedAt = now, mode = "region", regionId = regionId)
        val next = state.copy(regionTraining = run, queuedRegion = null, sessionId = session.id,
            sessions = if (session in state.sessions) state.sessions else state.sessions + session,
            active = null, reviewMode = false, endedSummary = null)
        val adopted = RegionProtection.adopt(next, scheduler, now)
        val task = scheduler.next(adopted, now)
        return AdaptiveEvidence.present(adopted.copy(currentNode = task.nodeId), task, now)
    }

    fun start(state: LearnerState, nodeId: String, now: Long): LearnerState {
        if (RegionSessions.active(state) || state.pausedTraining != null) return start(RegionSessions.leave(state), nodeId, now)
        if (state.pilot != null) return state
        if (state.practice != null) return start(endPractice(state, now), nodeId, now)
        val node = Curriculum.node(nodeId)
        require(Curriculum.available(state, node)) { "请先完成前置内容。" }
        if (state.sessionId != null && state.currentNode == nodeId && state.active != null) return state
        val session = state.sessions.lastOrNull { it.id == state.sessionId && it.endedAt == null } ?: LearningSession(startedAt = now)
        val next = state.copy(currentNode = nodeId, sessionId = session.id,
            sessions = if (session in state.sessions) state.sessions else state.sessions + session,
            active = null, reviewMode = Curriculum.mastered(state, nodeId), endedSummary = null, regionTraining = null, queuedRegion = null)
        return AdaptiveEvidence.present(next, scheduler.next(next, now), now)
    }

    fun startPractice(state: LearnerState, selection: PracticePlan, now: Long): LearnerState {
        if (RegionSessions.active(state) || state.pausedTraining != null) return startPractice(RegionSessions.leave(state), selection, now)
        if (state.pilot != null) return state
        PracticeLessons.validateSelection(state, selection)
        if (state.practice == selection && state.active != null) return state
        val base = if (state.practice != null) endPractice(state, now) else state
        val session = LearningSession(startedAt = now, mode = "practice")
        val next = base.copy(practice = selection,
            suspendedLesson = SuspendedLesson(base.currentNode, base.sessionId, base.active, base.reviewMode),
            currentNode = selection.nodeIds.first(), sessionId = session.id, sessions = base.sessions + session,
            active = null, reviewMode = false, endedSummary = null)
        return AdaptiveEvidence.present(next, scheduler.next(next, now), now)
    }

    private fun endPractice(state: LearnerState, now: Long): LearnerState {
        val resume = requireNotNull(state.suspendedLesson)
        val count = state.attempts.count { it.sessionId == state.sessionId && it.completed }
        return state.copy(currentNode = resume.currentNode, sessionId = resume.sessionId, active = resume.active,
            reviewMode = resume.reviewMode, practice = null, suspendedLesson = null,
            sessions = state.sessions.map { if (it.id == state.sessionId) it.copy(endedAt = now) else it },
            endedSummary = "专项完成 $count 个任务，记录已保存。")
    }

    fun hint(state: LearnerState, now: Long = System.currentTimeMillis()): LearnerState {
        val a = state.active ?: return state
        if (a.phase != Phase.ANSWERING) return state
        return AdaptiveEvidence.expose(state, a.task, now, true).copy(active = a.copy(hintLevel = (a.hintLevel + 1).coerceAtMost(2), hintRequested = true,
            feedback = if (a.hintLevel > 0) a.task.explanation else if (a.task.relation != null) "先看参考音和题目语境；听觉题可重复播放。再次提示可查看关系。" else if (a.task.mappingNote != null) "先分清固定唱名还是调内级数；级数要先看主音。再次提示可查看对应关系。" else "先看琴弦粗细、弦枕和定位圆点；再点一次提示可查看答案。"))
    }

    fun playbackStarted(state: LearnerState, taskId: String, now: Long = System.currentTimeMillis()): LearnerState {
        val active = state.active?.takeIf { it.task.id == taskId } ?: return state
        val relation = active.task.relation
        if (relation == null && active.task.notation?.score == null && active.task.chordProgression.isEmpty()) return state
        val exposed = if (relation?.ear != true) AdaptiveEvidence.expose(state, active.task, now, true) else state
        return exposed.copy(active = active.copy(audioReady = false,
            hintLevel = if (relation?.ear == true || active.task.guided) active.hintLevel else maxOf(1, active.hintLevel)))
    }

    fun playbackCompleted(state: LearnerState, taskId: String): LearnerState {
        val active = state.active?.takeIf { it.task.id == taskId && it.task.relation != null } ?: return state
        return state.copy(active = active.copy(audioReady = true))
    }

    fun answer(state: LearnerState, coordinate: Coordinate? = null, symbol: String? = null, now: Long, zone: ZoneId = ZoneId.systemDefault()): LearnerState {
        if (state.pilot?.mode == PilotMode.GUITAR) return state
        val active = state.active ?: return state
        if (active.phase in listOf(Phase.CORRECT, Phase.CORRECTED)) return state
        if (active.task.relation?.ear == true && !active.audioReady) return state
        if ((coordinate == null) == (symbol == null)) return state
        val result = AnswerEvaluator.evaluate(active, coordinate, symbol)
        val record = InputRecord(now, coordinate, symbol, result, if (active.task.completion == CompletionKind.SEQUENCE) active.sequenceIndex else null)
        if (result == ClickResult.REPEATED) return state.copy(active = active.copy(feedback = "这个位置已确认。"))
        if (result == ClickResult.OUTSIDE) return state.copy(active = active.copy(inputs = active.inputs + record, feedback = "这是本题范围外的位置，不计错。"))
        if (result == ClickResult.EXTRA_CORRECT) return state.copy(active = active.copy(inputs = active.inputs + record, feedback = "这个音也正确。请继续找齐本题要求的位置。"))
        val firstCorrect = active.firstCorrect ?: (result != ClickResult.WRONG)
        val confirmed = if (result != ClickResult.WRONG && coordinate != null) (active.confirmed + coordinate).distinct() else active.confirmed
        val sequenceIndex = active.sequenceIndex + if (result != ClickResult.WRONG && active.task.completion == CompletionKind.SEQUENCE) 1 else 0
        val correctedDone = when (active.task.completion) {
            CompletionKind.SINGLE -> true
            CompletionKind.SET -> confirmed.containsAll(active.task.requiredTargets)
            CompletionKind.SEQUENCE -> sequenceIndex >= active.task.sequence.size
        }
        val phase = when (result) {
            ClickResult.WRONG -> Phase.CORRECTING
            ClickResult.CORRECTION -> if (correctedDone) Phase.CORRECTED else Phase.CORRECTING
            ClickResult.CORRECT -> if (active.firstCorrect == false) Phase.CORRECTED else Phase.CORRECT
            else -> active.phase
        }
        val changed = active.copy(phase = phase,
            // For sets/sequences the first wrong member keeps the whole attempt incorrect.
            firstCorrect = if (result == ClickResult.WRONG) false else firstCorrect,
            firstAnswerAt = active.firstAnswerAt ?: now,
            firstUnassisted = active.firstUnassisted ?: (!active.task.guided && active.hintLevel == 0 && !active.hintRequested),
            inputs = active.inputs + record, confirmed = confirmed, sequenceIndex = sequenceIndex,
            feedback = when (result) {
                ClickResult.WRONG -> AnswerEvaluator.wrongFeedback(active.task, coordinate, active.sequenceIndex)
                ClickResult.CORRECTION -> if (correctedDone) "已纠正。准备好后点下一题。" else "这一处已纠正，继续找剩余位置。"
                ClickResult.PARTIAL -> "这一处正确，继续。"
                else -> if (active.task.guided) "记住这个位置，接下来试着自己找。" else "正确"
            })
        val completed = phase in listOf(Phase.CORRECT, Phase.CORRECTED)
        val old = state.attempts.firstOrNull { it.task.id == active.task.id }
        val ordinal = old?.ordinal ?: (state.attempts.maxOfOrNull { it.ordinal } ?: 0) + 1
        val independent = MasteryPolicy.independent(state, changed, ordinal) && active.task.completion == CompletionKind.SINGLE && active.task.adaptive?.options?.isNotEmpty() != true &&
            !(active.task.adaptive?.familyScope != null && active.task.adaptive.stage == 0)
        val attempt = Attempt(active.task, requireNotNull(state.sessionId), ordinal, old?.at ?: now,
            old?.localDay ?: Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString(),
            changed.firstCorrect, changed.hintLevel, phase == Phase.CORRECTED, completed, changed.inputs, independent,
            curriculumVersion = if (active.task.nodeId in FurtherLessons.ids) 8 else 7, policyVersion = 2, audioPlayed = active.audioReady,
            members = MemberEvidencePolicy.record(state, active, old, result, coordinate, now),
            firstUnassisted = old?.firstUnassisted ?: changed.firstUnassisted)
        val attempts = if (old == null) state.attempts + attempt else state.attempts.map { if (it.task.id == active.task.id) attempt else it }
        val updated = state.copy(active = changed, attempts = attempts,
            introductions = if (completed && active.task.introductionId != null) state.introductions + active.task.introductionId else state.introductions)
        val exposed = if (result == ClickResult.WRONG || active.phase == Phase.CORRECTING) CorrectionPresentation.expose(updated, changed, now)
        else if (active.task.completion == CompletionKind.SEQUENCE || old == null || completed && !old.completed)
            AdaptiveEvidence.exposeAnswer(updated, active.task, active.sequenceIndex, now, result == ClickResult.WRONG || active.task.guided) else updated
        return FamilyAdaptation.transition(AdaptiveMix.transition(AdaptiveTraining.transition(MasteryPolicy.update(exposed, now, attempt.localDay), now), now), now)
    }

    fun next(state: LearnerState, expectedTaskId: String, now: Long): LearnerState {
        if (state.pilot != null) return state
        val a = state.active ?: return state
        if (a.task.id != expectedTaskId || a.phase !in listOf(Phase.CORRECT, Phase.CORRECTED)) return state
        var changed = state.copy(active = null)
        if (state.practice == null && state.queuedRegion != null) return activateRegion(changed, state.queuedRegion, now)
        if (state.practice == null && state.regionTraining != null) {
            if (RegionRounds.finished(state)) return end(state, now, reason = "natural")
            val task = scheduler.next(changed, now)
            return AdaptiveEvidence.present(changed.copy(currentNode = task.nodeId), task, now)
        }
        if (state.practice == null && !state.reviewMode && Curriculum.mastered(state, state.currentNode)) {
            val next = Curriculum.next(state)
            if (next == null) return end(changed, now, "首轮学习已完成。可以从知识树复习；后续课程会逐步补齐。")
            changed = changed.copy(currentNode = next.id)
        }
        return AdaptiveEvidence.present(changed, scheduler.next(changed, now), now)
    }

    fun end(state: LearnerState, now: Long, summary: String? = null, reason: String = "back"): LearnerState {
        if (state.practice != null) return endPractice(state, now)
        val id = state.sessionId ?: return state
        if (state.sessions.any { it.id == id && it.endedAt != null }) return state
        val attempts = state.attempts.filter { it.sessionId == id }
        val independent = attempts.filter { it.independent }
        val evaluated = RoundExperience.finish(state, now, reason == "natural")
        return evaluated.copy(sessionId = null, active = null, regionTraining = null, queuedRegion = null,
            sessions = state.sessions.map { if (it.id == id) it.copy(endedAt = now, endReason = reason, regionId = state.regionTraining?.regionId ?: it.regionId) else it },
            regionContinuations = state.regionTraining?.let { state.regionContinuations + (it.regionId to it.adaptive) } ?: state.regionContinuations,
            endedSummary = summary ?: (if (state.regionTraining != null) RegionProgression.summary(state) else null) ?: "本次完成${attempts.count { it.completed }}个任务，独立回答${independent.size + attempts.sumOf { it.members.count { m -> m.independent } }}项，正确${independent.count { it.firstCorrect == true } + attempts.sumOf { it.members.count { m -> m.independent && m.firstCorrect } }}项。进度已保存。")
    }
}

/** Region entry never resumes an unrelated exercise. Leaving it restores the paused course context. */
internal object RegionSessions {
    fun active(s: LearnerState) = s.regionTraining != null && s.practice == null && s.pilot == null
    fun capture(s: LearnerState) = PausedTraining(s.currentNode, s.sessionId, s.active, s.reviewMode,
        s.practice, s.suspendedLesson, s.regionTraining, s.pilot, s.pilotSuspended)
    fun clear(s: LearnerState) = s.copy(sessionId = null, active = null, reviewMode = false, practice = null,
        suspendedLesson = null, regionTraining = null, queuedRegion = null, pilot = null, pilotSuspended = null, endedSummary = null)
    fun restore(s: LearnerState, p: PausedTraining) = s.copy(currentNode = p.currentNode, sessionId = p.sessionId,
        active = p.active, reviewMode = p.reviewMode, practice = p.practice, suspendedLesson = p.suspendedLesson,
        regionTraining = p.regionTraining, pilot = p.pilot, pilotSuspended = p.pilotSuspended, queuedRegion = null, endedSummary = null)
    fun leave(s: LearnerState): LearnerState {
        val regions = if (active(s)) s.pausedRegions + (s.regionTraining!!.regionId to capture(s)) else s.pausedRegions
        val base = clear(s).copy(pausedTraining = null, pausedRegions = regions)
        return s.pausedTraining?.let { restore(base, it) } ?: base
    }
}
