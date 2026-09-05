package com.a3322505a.guitarlearning.learning

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Serializable
data class Coordinate(val string: Int, val fret: Int) {
    init { require(string in 1..6 && fret in 0..15) }
    val id: String get() = "s${string}:f$fret"
    val label: String get() = "${string}弦${if (fret == 0) "空弦" else "${fret}品"}"
}

enum class Category(val title: String, val description: String) {
    GUITAR("认识吉他", "认识弦、品格与定位圆点"),
    SYMBOL("基础认识", "认识音名，逐步理解唱名与级数"),
    READING("读谱入门", "用熟悉的位置看懂 TAB"),
    FRETBOARD("指板训练", "两个音位一小步，穿插旧知识"),
    ADVANCED("进阶应用", "音程、音阶、和弦与听觉 · 规划中"),
}

@Serializable enum class Direction { NOTE_TO_POSITION, POSITION_TO_NOTE, RECOGNIZE, TAB_TO_POSITION }
@Serializable enum class TaskSource { MAIN, REVIEW, PREVIEW, DEMONSTRATION }
@Serializable enum class ConstraintKind { NOTE_CLASS, PITCH, COORDINATE, STRING, FRET, SYMBOL }
@Serializable enum class CompletionKind { SINGLE, SET, SEQUENCE }
@Serializable enum class Phase { ANSWERING, CORRECT, CORRECTING, CORRECTED }
@Serializable enum class ClickResult { CORRECT, EXTRA_CORRECT, WRONG, OUTSIDE, REPEATED, CORRECTION, PARTIAL }

@Serializable
data class AnswerConstraint(
    val kind: ConstraintKind,
    val coordinate: Coordinate? = null,
    val symbol: String? = null,
    val midi: Int? = null,
    val string: Int? = null,
    val fret: Int? = null,
)

@Serializable
data class PhysicalRange(val firstFret: Int = 0, val lastFret: Int = 4, val strings: Set<Int> = (1..6).toSet()) {
    init { require(firstFret in 0..15 && lastFret in firstFret..15 && strings.all { it in 1..6 }) }
    fun contains(c: Coordinate): Boolean = c.fret in firstFret..lastFret && c.string in strings
    fun positions(): List<Coordinate> = strings.sorted().flatMap { s -> (firstFret..lastFret).map { Coordinate(s, it) } }
}

@Serializable
data class LearningTask(
    val id: String = newId(),
    val nodeId: String,
    val skillId: String,
    val coordinate: Coordinate? = null,
    val direction: Direction = Direction.RECOGNIZE,
    val prompt: String,
    val explanation: String,
    val constraint: AnswerConstraint,
    val range: PhysicalRange = PhysicalRange(),
    val source: TaskSource = TaskSource.MAIN,
    val options: List<String> = emptyList(),
    val completion: CompletionKind = CompletionKind.SINGLE,
    val requiredTargets: List<Coordinate> = emptyList(),
    val sequence: List<AnswerConstraint> = emptyList(),
    val showTab: Boolean = false,
    val hideStringLabels: Boolean = false,
    val hideFretLabels: Boolean = false,
    val introductionId: String? = null,
) {
    val guided: Boolean get() = source == TaskSource.DEMONSTRATION || source == TaskSource.PREVIEW
}

@Serializable
data class InputRecord(
    val at: Long,
    val coordinate: Coordinate? = null,
    val symbol: String? = null,
    val result: ClickResult,
)

@Serializable
data class ActiveTask(
    val task: LearningTask,
    val phase: Phase = Phase.ANSWERING,
    val hintLevel: Int = 0,
    val firstCorrect: Boolean? = null,
    val firstAnswerAt: Long? = null,
    val inputs: List<InputRecord> = emptyList(),
    val confirmed: List<Coordinate> = emptyList(),
    val sequenceIndex: Int = 0,
    val feedback: String = "",
    val hintRequested: Boolean = false,
)

@Serializable
data class Attempt(
    val task: LearningTask,
    val sessionId: String,
    val ordinal: Int,
    val at: Long,
    val localDay: String,
    val firstCorrect: Boolean?,
    val hintLevel: Int,
    val corrected: Boolean,
    val completed: Boolean,
    val inputs: List<InputRecord>,
    val independent: Boolean,
    val curriculumVersion: Int = 4,
    val policyVersion: Int = 1,
)

@Serializable
data class NodeProgress(val masteredAt: Long? = null, val retainedOn: String? = null, val needsReview: Boolean = false)

@Serializable
data class LearningSession(val id: String = newId(), val startedAt: Long, val endedAt: Long? = null)

@Serializable
data class LearnerState(
    val schemaVersion: Int = 1,
    val learnerId: String = newId(),
    val revision: Long = 0,
    val currentNode: String = "g00",
    val active: ActiveTask? = null,
    val attempts: List<Attempt> = emptyList(),
    val progress: Map<String, NodeProgress> = emptyMap(),
    val introductions: Set<String> = emptySet(),
    val sessions: List<LearningSession> = emptyList(),
    val sessionId: String? = null,
    val soundEnabled: Boolean = true,
    val reviewMode: Boolean = false,
    val endedSummary: String? = null,
)

data class CurriculumNode(
    val id: String,
    val title: String,
    val category: Category,
    val description: String,
    val prerequisites: List<String> = emptyList(),
    val positions: List<Coordinate> = emptyList(),
    val implemented: Boolean = true,
)
