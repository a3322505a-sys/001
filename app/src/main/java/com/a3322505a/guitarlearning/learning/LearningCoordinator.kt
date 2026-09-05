package com.a3322505a.guitarlearning.learning

import java.time.Instant
import java.time.ZoneId

/** Pure state transitions. The caller must commit the returned state before exposing it. */
class LearningCoordinator(private val scheduler: LessonScheduler = LessonScheduler()) {
    fun start(state: LearnerState, nodeId: String, now: Long): LearnerState {
        val node = Curriculum.node(nodeId)
        require(Curriculum.available(state, node)) { "请先完成前置内容。" }
        if (state.sessionId != null && state.currentNode == nodeId && state.active != null) return state
        val session = state.sessions.lastOrNull { it.id == state.sessionId && it.endedAt == null } ?: LearningSession(startedAt = now)
        val next = state.copy(currentNode = nodeId, sessionId = session.id,
            sessions = if (session in state.sessions) state.sessions else state.sessions + session,
            active = null, reviewMode = Curriculum.mastered(state, nodeId), endedSummary = null)
        return next.copy(active = ActiveTask(scheduler.next(next, now)))
    }

    fun hint(state: LearnerState): LearnerState {
        val a = state.active ?: return state
        if (a.phase != Phase.ANSWERING) return state
        return state.copy(active = a.copy(hintLevel = (a.hintLevel + 1).coerceAtMost(2), hintRequested = true,
            feedback = if (a.hintLevel > 0) a.task.explanation else if (a.task.mappingNote != null) "先分清固定唱名还是调内级数；级数要先看主音。再次提示可查看对应关系。" else "先看琴弦粗细、弦枕和定位圆点；再点一次提示可查看答案。"))
    }

    fun answer(state: LearnerState, coordinate: Coordinate? = null, symbol: String? = null, now: Long, zone: ZoneId = ZoneId.systemDefault()): LearnerState {
        val active = state.active ?: return state
        if (active.phase in listOf(Phase.CORRECT, Phase.CORRECTED)) return state
        if ((coordinate == null) == (symbol == null)) return state
        val result = AnswerEvaluator.evaluate(active, coordinate, symbol)
        val record = InputRecord(now, coordinate, symbol, result)
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
            inputs = active.inputs + record, confirmed = confirmed, sequenceIndex = sequenceIndex,
            feedback = when (result) {
                ClickResult.WRONG -> AnswerEvaluator.wrongFeedback(active.task, coordinate)
                ClickResult.CORRECTION -> if (correctedDone) "已纠正。准备好后点下一题。" else "这一处已纠正，继续找剩余位置。"
                ClickResult.PARTIAL -> "这一处正确，继续。"
                else -> if (active.task.guided) "记住这个位置，接下来试着自己找。" else "正确"
            })
        val completed = phase in listOf(Phase.CORRECT, Phase.CORRECTED)
        val old = state.attempts.firstOrNull { it.task.id == active.task.id }
        val ordinal = old?.ordinal ?: (state.attempts.maxOfOrNull { it.ordinal } ?: 0) + 1
        val independent = MasteryPolicy.independent(state, changed, ordinal) && active.task.completion == CompletionKind.SINGLE
        val attempt = Attempt(active.task, requireNotNull(state.sessionId), ordinal, old?.at ?: now,
            old?.localDay ?: Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toString(),
            changed.firstCorrect, changed.hintLevel, phase == Phase.CORRECTED, completed, changed.inputs, independent)
        val attempts = if (old == null) state.attempts + attempt else state.attempts.map { if (it.task.id == active.task.id) attempt else it }
        val updated = state.copy(active = changed, attempts = attempts,
            introductions = if (completed && active.task.introductionId != null) state.introductions + active.task.introductionId else state.introductions)
        return MasteryPolicy.update(updated, now, attempt.localDay)
    }

    fun next(state: LearnerState, expectedTaskId: String, now: Long): LearnerState {
        val a = state.active ?: return state
        if (a.task.id != expectedTaskId || a.phase !in listOf(Phase.CORRECT, Phase.CORRECTED)) return state
        var changed = state.copy(active = null)
        if (!state.reviewMode && Curriculum.mastered(state, state.currentNode)) {
            val next = Curriculum.next(state)
            if (next == null) return end(changed, now, "首轮学习已完成。可以从知识树复习；后续课程会逐步补齐。")
            changed = changed.copy(currentNode = next.id)
        }
        return changed.copy(active = ActiveTask(scheduler.next(changed, now)))
    }

    fun end(state: LearnerState, now: Long, summary: String? = null): LearnerState {
        val id = state.sessionId ?: return state
        val attempts = state.attempts.filter { it.sessionId == id }
        val independent = attempts.filter { it.independent }
        return state.copy(sessionId = null, active = null,
            sessions = state.sessions.map { if (it.id == id) it.copy(endedAt = now) else it },
            endedSummary = summary ?: "本次完成${attempts.count { it.completed }}个任务，独立回答${independent.size}次，正确${independent.count { it.firstCorrect == true }}次。进度已保存。")
    }
}
