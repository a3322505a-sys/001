package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.audio.TimedPitchEvent
import com.a3322505a.guitarlearning.core.MusicFacts
import kotlinx.serialization.Serializable

/** Four integer ticks per quarter note. MIDI is sounding pitch; spelling is written separately. */
@Serializable data class ScoreEvent(val tick: Int, val duration: Int, val midi: Int? = null, val coordinate: Coordinate? = null, val spelling: String? = null) {
    init {
        require(tick >= 0 && duration > 0)
        require(midi == null || midi in 40..88)
        require(coordinate == null || midi == MusicFacts.midi(coordinate.string, coordinate.fret))
        require(midi != null || coordinate == null)
    }
}
@Serializable data class ShortScore(val id: String, val version: Int = 1, val events: List<ScoreEvent>, val bars: Int = 2,
    val beatsPerBar: Int = 4, val beatUnit: Int = 4, val bpm: Int = 50,
    val author: String = "项目01原创短练习", val license: String = "CC0-1.0", val source: String = "original:project-01") {
    init {
        require(events.isNotEmpty() && bars in 1..8 && beatsPerBar == 4 && beatUnit == 4 && bpm in 40..80)
        require(events.first().tick == 0 && events.zipWithNext().all { (a,b) -> a.tick + a.duration == b.tick })
        require(events.last().tick + events.last().duration == bars * 16)
        require(events.all { it.tick / 16 == (it.tick + it.duration - 1) / 16 })
    }
    val notes get() = events.filter { it.midi != null }
    fun playback(tempo: Int = bpm): List<TimedPitchEvent> {
        require(tempo in 40..80)
        fun ms(tick: Int) = (tick * 60000L / (tempo * 4)).toInt()
        return events.map { TimedPitchEvent(ms(it.tick), ms(it.tick + it.duration) - ms(it.tick), listOfNotNull(it.midi)) }
    }
    fun notation(kind: NotationKind) = NotationPrompt(kind, notes.map { requireNotNull(it.midi) },
        if (kind == NotationKind.TAB) notes.map { requireNotNull(it.coordinate) } else emptyList(), this)
}

@Serializable enum class PilotMode(val title: String) { SLOW("慢读找音"), GUITAR("连读 / 拿琴试奏") }
@Serializable enum class PilotRole { BASELINE, PRACTICE, RETEST }
@Serializable data class PilotRun(val clip: Int, val mode: PilotMode, val score: ShortScore, val kind: NotationKind,
    val elapsedMs: Long = 0, val playbackMs: Int = 0, val assisted: Boolean = false, val bpm: Int = 50)
@Serializable data class PilotResult(val scoreId: String, val version: Int, val clip: Int, val mode: PilotMode, val kind: NotationKind,
    val role: PilotRole, val taskId: String, val elapsedMs: Long, val notes: Int, val firstCorrect: Int,
    val assisted: Boolean, val rating: String? = null, val comment: String = "")

object ShortScorePilot {
    // Separate fixed orders: retest melodies are never used by normal lessons or another display.
    private val routes = listOf(
        listOf(0,1,2,1,0,2,1,0), listOf(2,0,1,2,1,0,2,0),
        listOf(0,1,0,2,1,2,1,0), listOf(0,2,1,2,0,1),
        listOf(0,1,-1,2,1,-1,0,2), listOf(2,1,0,-1,1,2,0),
        listOf(1,2,0,1,2,0,1,0), listOf(0,2,1,0,1,2,0,2))
    private val lengths = listOf(List(8){4},List(8){4},List(8){4},listOf(4,4,8,4,4,8),List(8){4},listOf(4,4,4,4,4,4,8),List(8){4},List(8){4})
    fun role(clip: Int) = when (clip) { 0,1 -> PilotRole.BASELINE; 6,7 -> PilotRole.RETEST; else -> PilotRole.PRACTICE }
    fun kind(clip: Int) = if (clip % 2 == 0) NotationKind.TAB else NotationKind.STAFF
    fun nextClip(s: LearnerState, mode: PilotMode) = (0..7).firstOrNull { clip -> s.pilotResults.none { it.mode == mode && it.clip == clip } }
    fun pool(s: LearnerState): List<Coordinate> = listOf(Coordinate(1,0), Coordinate(1,1), Coordinate(1,3))
        .filter { "position:${it.id}" in s.introductions || MasteryPolicy.positionPassed(s, it) }
    fun available(s: LearnerState, clip: Int): Boolean = pool(s).size >= 2 && Curriculum.mastered(s,"tab01") &&
        (kind(clip) == NotationKind.TAB || "reading:staff:octave" in s.introductions || Curriculum.mastered(s,"staff"))
    fun score(clip: Int, pool: List<Coordinate>): ShortScore {
        require(clip in 0..7 && pool.size >= 2)
        var tick = 0
        val events = routes[clip].mapIndexed { i,n ->
            val c = if (n < 0) null else pool[n % pool.size]
            ScoreEvent(tick, lengths[clip][i], c?.let { MusicFacts.midi(it.string,it.fret) }, c).also { tick += it.duration }
        }
        return ShortScore("pilot-${clip + 1}-pool-${pool.joinToString("_"){it.id}}", events = events)
    }
    fun task(run: PilotRun): LearningTask {
        val notation = run.score.notation(run.kind)
        val rules = run.score.notes.map { e -> if (run.kind == NotationKind.TAB) AnswerConstraint(ConstraintKind.COORDINATE, coordinate = e.coordinate)
            else AnswerConstraint(ConstraintKind.PITCH, midi = e.midi) }
        return LearningTask(nodeId = if (run.kind == NotationKind.TAB) "tab02" else "staff02", skillId = "pilot:${run.score.id}",
            direction = if (run.kind == NotationKind.TAB) Direction.TAB_TO_POSITION else Direction.STAFF_TO_POSITION,
            prompt = "${if (role(run.clip) == PilotRole.PRACTICE) "短谱练习" else "短谱测读"} · ${run.clip + 1}/8",
            explanation = "按从左到右的顺序读谱。空拍休止不点指板；手机找音只记录音高与顺序。",
            constraint = rules.first(), sequence = rules, completion = CompletionKind.SEQUENCE,
            targetSkillIds = rules.indices.map { "pilot:${run.score.id}:$it" }, notation = notation)
    }
    fun begin(s: LearnerState, mode: PilotMode, now: Long): LearnerState {
        if (s.pilot != null) return s
        val clip = nextClip(s, mode) ?: return s
        require(available(s, clip)) { "先完成 TAB 与对应的五线谱八度说明。" }
        // Freeze the pitch pool at baseline, so retests use matched difficulty.
        val pool = s.pilotPool.ifEmpty { pool(s) }
        val run = PilotRun(clip, mode, score(clip,pool), kind(clip))
        val session = LearningSession(startedAt = now, mode = "score-pilot")
        return s.copy(pilot = run, pilotPool = pool,
            pilotSuspended = SuspendedLesson(s.currentNode,s.sessionId,s.active,s.reviewMode),
            active = ActiveTask(task(run)), currentNode = if (run.kind == NotationKind.TAB) "tab02" else "staff02",
            sessionId = session.id, sessions = s.sessions + session, endedSummary = null)
    }
    fun finish(s: LearnerState, now: Long, rating: String? = null, comment: String = ""): LearnerState {
        val run = s.pilot ?: return s
        val active = requireNotNull(s.active)
        require(if (run.mode == PilotMode.SLOW) active.phase in listOf(Phase.CORRECT,Phase.CORRECTED) else rating in listOf("顺畅","有停顿","困难"))
        val attempt = s.attempts.firstOrNull { it.task.id == active.task.id }
        val result = PilotResult(run.score.id,run.score.version,run.clip,run.mode,run.kind,role(run.clip),active.task.id,run.elapsedMs,
            run.score.notes.size,attempt?.members?.count { it.firstCorrect } ?: 0,run.assisted || active.hintLevel > 0,rating,comment.take(200))
        return restore(s.copy(pilotResults = s.pilotResults + result),now)
    }
    fun restore(s: LearnerState, now: Long): LearnerState {
        val old = requireNotNull(s.pilotSuspended)
        return s.copy(pilot = null,pilotSuspended = null,currentNode = old.currentNode,sessionId = old.sessionId,active = old.active,reviewMode = old.reviewMode,
            sessions = s.sessions.map { if(it.id == s.sessionId) it.copy(endedAt=now) else it })
    }
}
