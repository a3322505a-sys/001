package com.a3322505a.guitarlearning.learning

import kotlinx.serialization.Serializable

@Serializable enum class TimingQuality { UNAVAILABLE, VALID, TOO_FAST, INTERRUPTED }
@Serializable data class ResponseObservation(
    val task: LearningTask, val sessionId: String, val durationMs: Long? = null,
    val quality: TimingQuality = TimingQuality.UNAVAILABLE, val helped: Boolean = false,
    val replayed: Boolean = false,
)
@Serializable data class LongThought(val task: LearningTask, val at: Long, val durationMs: Long)

/** Candidate values are versioned here; real learner calibration remains a separate acceptance. */
object ExperiencePolicy {
    const val VERSION = 1
    const val MIN_INPUT_MS = 250L
    const val RETENTION_MS = 12L * 60 * 60 * 1000
    const val FLUENT_WINDOW = 20
    const val FLUENT_ALLOWED_ERRORS = 1
    fun deadline(d: Direction) = if (d == Direction.POSITION_TO_NOTE) 8_000L else 12_000L
    fun fast(d: Direction) = if (d == Direction.POSITION_TO_NOTE) 3_000L else 5_000L
    fun plain(t: LearningTask) = t.direction in AdaptiveEvidence.positionDirections && t.coordinate != null &&
        t.completion == CompletionKind.SINGLE && t.range.firstFret == (if (t.coordinate.fret <= 4) 0 else if (t.coordinate.fret <= 8) 5 else 9) &&
        t.range.lastFret == (if (t.coordinate.fret <= 4) 4 else if (t.coordinate.fret <= 8) 8 else 12) && t.range.strings == setOf(t.coordinate.string) && !t.guided && t.adaptive?.scaffolded != true && t.adaptive?.options.isNullOrEmpty()
}

/** A task-bound monotonic clock. Interruptions never become slow or fast evidence. */
class ResponseClock {
    private var id: String? = null
    private var start: Long? = null
    private var interrupted = false
    private var stopped: Pair<Long?, TimingQuality>? = null
    fun displayed(taskId: String, now: Long, restored: Boolean = false) {
        if (id == taskId) return
        id = taskId; start = now; interrupted = restored; stopped = null
    }
    fun interrupt() { if (stopped == null) interrupted = true }
    fun sample(taskId: String, now: Long): Pair<Long?, TimingQuality> {
        if (id != taskId || start == null) return null to TimingQuality.UNAVAILABLE
        stopped?.let { return it }
        if (interrupted) return null to TimingQuality.INTERRUPTED
        val ms = (now - requireNotNull(start)).coerceAtLeast(0)
        return ms to if (ms < ExperiencePolicy.MIN_INPUT_MS) TimingQuality.TOO_FAST else TimingQuality.VALID
    }
    fun stop(taskId: String, now: Long): Pair<Long?, TimingQuality> = sample(taskId, now).also { if (id == taskId) stopped = it }
}

object ResponseTiming {
    fun record(s: LearnerState, id: String, sample: Pair<Long?, TimingQuality>, help: Boolean = false, replay: Boolean = false): LearnerState {
        val o = s.responseObservations[id] ?: return s
        val a = s.active?.takeIf { it.task.id == id } ?: return s
        val first = a.firstCorrect == null && !o.helped
        val next = o.copy(durationMs = if (first) sample.first else o.durationMs,
            quality = if (first) sample.second else o.quality, helped = o.helped || help, replayed = o.replayed || replay)
        return s.copy(responseObservations = s.responseObservations + (id to next))
    }
    fun long(s: LearnerState, event: LongThought): LearnerState {
        if (event.task.id in s.longThoughts) return s
        return RegionProtection.transition(RegionProtection.protect(s.copy(longThoughts = s.longThoughts + (event.task.id to event)), event.task, event.at), event.at)
    }
    fun timely(s: LearnerState, a: Attempt): Boolean {
        val o = s.responseObservations[a.task.id] ?: return false
        return a.firstCorrect == true && a.firstUnassisted == true && !o.helped && o.quality == TimingQuality.VALID &&
            a.task.id !in s.longThoughts && (o.durationMs ?: Long.MAX_VALUE) < ExperiencePolicy.deadline(a.task.direction)
    }
}
