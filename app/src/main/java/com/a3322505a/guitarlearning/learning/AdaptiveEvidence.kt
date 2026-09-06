package com.a3322505a.guitarlearning.learning

import com.a3322505a.guitarlearning.core.MusicFacts
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable data class KnowledgeExposure(val taskId: String, val target: String, val at: Long, val help: Boolean = false)
@Serializable enum class AnswerRepresentation { NOTE, FIXED, DEGREE }
@Serializable data class SemanticOption(val label: String, val pitchClass: Int, val representation: AnswerRepresentation)
@Serializable enum class PracticePurpose { NORMAL, WEAK, FAMILIAR, COVERAGE, RETEST, NEXT, DIAGNOSIS, MAPPING }
@Serializable data class AdaptiveTask(
    val config: String, val purpose: PracticePurpose, val stage: Int = 0,
    val options: List<SemanticOption> = emptyList(), val correctRepresentation: AnswerRepresentation = AnswerRepresentation.NOTE,
    val unit: String? = null,
)
@Serializable enum class RecoveryLayer { REGION, LOCAL, NATURAL, OPEN }
@Serializable data class AdaptiveRun(
    val generation: Int = 0, val sinceOrdinal: Int = 0, val layer: RecoveryLayer = RecoveryLayer.REGION,
    val previousLayer: RecoveryLayer = RecoveryLayer.REGION, val focus: List<String> = emptyList(),
    val diagnosing: Boolean = false, val diagnosisSince: Int = 0, val handledFailure: String? = null,
    val trial: Boolean = false, val sevenQualifiedAt: Long? = null,
    val representatives: List<Coordinate> = emptyList(), val mixStage: Int = 0,
    val excluded: Set<AnswerRepresentation> = emptySet(), val representationTrials: Map<AnswerRepresentation, Int> = emptyMap(),
    val reason: String? = null,
    val wide: Boolean = false,
) { val config: String get() = "$generation:${layer.name}:M$mixStage:${excluded.sortedBy { it.name }.joinToString { it.name }}" }
@Serializable data class WeakPoint(
    val unit: String, val target: String, val observedAt: Long, val confirmedAt: Long? = null,
    val taughtAt: Long? = null, val resolvedAt: Long? = null, val lastFailure: String? = null,
)

data class AssessmentSample(
    val taskId: String, val unit: String, val target: String, val at: Long, val ordinal: Int,
    val correct: Boolean, val retention: Boolean, val task: LearningTask,
)
data class RegionAssessment(val total: Int, val measured: Int, val raw: Double, val label: String)

/** Versioned projection over immutable first-answer conditions and the existing profile facts. */
object AdaptiveEvidence {
    const val VERSION = 1
    const val WINDOW_MS = 14L * 24 * 60 * 60 * 1000
    const val HOLD_MS = 30L * 60 * 1000
    val positionDirections = listOf(Direction.POSITION_TO_NOTE, Direction.NOTE_TO_POSITION)
    fun positionTarget(c: Coordinate) = "position:${c.id}"
    fun positionUnit(c: Coordinate, d: Direction) = "${positionTarget(c)}:${d.name}"
    fun targets(t: LearningTask): Set<String> {
        val targets = mutableSetOf<String>()
        t.coordinate?.takeIf { t.direction in positionDirections }?.let { targets += positionTarget(it) }
        MappingLessons.family(t)?.let { targets += it }
        if (t.adaptive?.options?.isNotEmpty() == true) {
            val note = t.coordinate?.let { MusicFacts.note(it.string, it.fret) }
            if (note != null) t.adaptive.options.map { it.representation }.toSet().forEach { representation ->
                when (representation) {
                    AnswerRepresentation.FIXED -> targets += "mapping:fixed:$note"
                    AnswerRepresentation.DEGREE -> targets += "mapping:major:${t.tonicPitchClass}:$note"
                    else -> Unit
                }
            }
        }
        return targets.ifEmpty { setOf("skill:${t.skillId}") }
    }
    fun unit(t: LearningTask): String? {
        if (t.completion != CompletionKind.SINGLE || t.guided) return null
        if (t.adaptive?.options?.isNotEmpty() == true) return "application:${t.coordinate?.id}:${t.adaptive.correctRepresentation.name}"
        t.coordinate?.takeIf { t.direction in positionDirections }?.let { return positionUnit(it, t.direction) }
        return MappingLessons.family(t)?.let { "$it:${t.direction.name}" }
    }
    fun expose(s: LearnerState, t: LearningTask, now: Long, help: Boolean = false, explanation: Boolean = help): LearnerState {
        val exposed = targets(t).toMutableSet()
        if (explanation) {
            t.coordinate?.takeIf { t.direction in positionDirections }?.let { c -> exposed += LessonExplanations.positionRoute(t.nodeId, c).map(::positionTarget) }
            if (t.direction in MappingLessons.fixedDirections) exposed += MappingLessons.notes.map { "mapping:fixed:$it" }
        }
        val events = exposed.map { KnowledgeExposure(t.id, it, now, help) }
        return s.copy(knowledgeExposures = s.knowledgeExposures + events.filterNot { it in s.knowledgeExposures })
    }
    fun present(s: LearnerState, task: LearningTask, now: Long): LearnerState {
        val t = task.copy(evidenceVersion = VERSION)
        val next = AdaptiveTraining.onPresented(s.copy(active = ActiveTask(t)), t, now)
        return if (t.guided) expose(next, t, now, true) else next
    }
    fun firstInput(a: Attempt) = a.inputs.firstOrNull { it.result !in listOf(ClickResult.OUTSIDE, ClickResult.REPEATED, ClickResult.EXTRA_CORRECT) }
    fun ready(samples: List<AssessmentSample>): Boolean = samples.takeLast(4).let { it.size == 4 && it.count { s -> s.correct } >= 3 && it.takeLast(2).all { s -> s.correct } }
    fun weak(samples: List<AssessmentSample>): Boolean =
        samples.takeLast(2).let { it.size == 2 && it.none { s -> s.correct } } ||
        samples.takeLast(6).let { it.size == 6 && it.count { s -> !s.correct } >= 3 }
    fun recovered(samples: List<AssessmentSample>): Boolean = samples.takeLast(6).let {
        it.size == 6 && it.count { s -> s.correct } >= 5 && it.takeLast(2).all { s -> s.correct }
    }
    fun targets(region: FretboardRegion): List<Coordinate> =
        (if (region == FretboardRegion.FULL) FretboardRegion.entries.flatMap { it.nodes } else region.nodes).flatMap { it.positions }.distinct()

    class View(s: LearnerState, val now: Long) {
        private data class Event(val at: Long, val rank: Int, val attempt: Attempt? = null, val exposure: KnowledgeExposure? = null)
        private data class Exposure(val at: Long, val completedIndex: Int)
        private val exposures = mutableMapOf<String, Exposure>()
        private val challenges = mutableMapOf<String, Long>()
        private val completions = mutableListOf<Set<String>>()
        val samples: List<AssessmentSample>
        val allSamples: List<AssessmentSample>
        init {
            val events = mutableListOf<Event>()
            s.knowledgeExposures.forEach { events += Event(it.at, 1, exposure = it) }
            s.attempts.forEach { a ->
                val input = firstInput(a)
                if (input != null && a.task.evidenceVersion == VERSION) events += Event(input.at, 0, a)
                // Legacy facts can reveal exposure but cannot establish independent performance.
                if (a.task.evidenceVersion == 0) targets(a.task).forEach {
                    events += Event(a.inputs.lastOrNull()?.at ?: a.at, 1, exposure = KnowledgeExposure(a.task.id, it, a.inputs.lastOrNull()?.at ?: a.at, a.hintLevel > 0 || a.task.guided))
                }
                if (a.completed && input != null && a.firstUnassisted == true && a.task.completion == CompletionKind.SINGLE) events += Event(a.inputs.last().at, 2, a)
            }
            val collected = mutableListOf<AssessmentSample>()
            val scoredTasks = mutableSetOf<String>()
            events.filter { it.at <= now }.sortedWith(compareBy<Event> { it.at }.thenBy { it.rank }.thenBy { it.attempt?.ordinal ?: 0 }).forEach { event ->
                val exposure = event.exposure
                if (exposure != null) {
                    exposures[exposure.target] = Exposure(event.at, completions.size)
                    if (exposure.help) challenges[exposure.target] = event.at
                } else {
                    val a = requireNotNull(event.attempt)
                    val facts = targets(a.task)
                    if (event.rank == 2) {
                        if (a.task.id in scoredTasks) completions += facts
                    } else {
                        val input = requireNotNull(firstInput(a))
                        val correct = input.result != ClickResult.WRONG
                        val key = unit(a.task)
                        val previous = facts.mapNotNull { exposures[it] }
                        val spaced = previous.all { gap(it, facts, event.at) }
                        if (key != null && a.firstUnassisted == true && spaced && (a.task.relation?.ear != true || a.audioPlayed)) {
                            val lastExposure = previous.maxOfOrNull { it.at }
                            collected += AssessmentSample(a.task.id, key, facts.first(), event.at, a.ordinal, correct,
                                correct && lastExposure != null && event.at - lastExposure >= HOLD_MS, a.task)
                            scoredTasks += a.task.id
                        }
                        if (!correct && a.firstUnassisted == true) facts.forEach { challenges[it] = event.at }
                    }
                }
            }
            allSamples = collected
            samples = collected.filter { it.at >= now - WINDOW_MS }.map { sample ->
                if (sample.retention && targets(sample.task).any { (challenges[it] ?: Long.MIN_VALUE) >= sample.at }) sample.copy(retention = false) else sample
            }
        }
        private fun gap(previous: Exposure, targets: Set<String>, at: Long = now): Boolean = at - previous.at >= HOLD_MS ||
            completions.drop(previous.completedIndex).filter { it.none { t -> t in targets } }.map { it.first() }.distinct().size >= 2
        fun eligible(t: LearningTask) = targets(t).let { facts -> facts.mapNotNull { exposures[it] }.all { gap(it, facts) } }
        fun unit(key: String) = samples.filter { it.unit == key }.takeLast(8)
        fun ready(key: String) = AdaptiveEvidence.ready(unit(key))
        fun weak(key: String) = AdaptiveEvidence.weak(unit(key))
        fun held(key: String) = samples.any { it.unit == key && it.retention }
        fun lastExposure(target: String): Long? = exposures[target]?.at
        fun region(region: FretboardRegion): RegionAssessment {
            val positions = targets(region)
            val pairs = positions.map { c -> positionDirections.map { d -> unit(positionUnit(c, d)) } }
            val measured = pairs.count { directions -> directions.all { it.size >= 4 } }
            fun score(window: List<AssessmentSample>): Double {
                if (window.isEmpty()) return 0.0
                return window.count { it.correct }.toDouble() / window.size * minOf(window.size / 4.0, 1.0) * if (held(window.first().unit)) 1.0 else 0.8
            }
            val raw = if (positions.isEmpty()) 0.0 else (100.0 / positions.size * pairs.sumOf { ds -> ds.minOf(::score) }).coerceIn(0.0, 100.0)
            val past = allSamples.any { sample -> sample.task.coordinate in positions && sample.unit.startsWith("position:") && sample.at < now - WINDOW_MS }
            val label = if (measured.toDouble() / positions.size.coerceAtLeast(1) < 0.3) {
                if (past) "待复测" else "评估中"
            } else "${raw.roundToInt()}%"
            return RegionAssessment(positions.size, measured, raw, "音位熟练度 · $label")
        }
    }
}
