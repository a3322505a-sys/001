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
    ADVANCED("进阶应用", "和弦、音程、音阶与听觉"),
}

@Serializable enum class Direction { NOTE_TO_POSITION, POSITION_TO_NOTE, RECOGNIZE, TAB_TO_POSITION, NOTE_TO_SOLFEGE, SOLFEGE_TO_NOTE, NOTE_TO_DEGREE, DEGREE_TO_NOTE, CHORD_SHAPE, STAFF_TO_POSITION, RELATION, STRUCTURE, REFERENCE_EAR }
@Serializable enum class TaskSource { MAIN, REVIEW, PREVIEW, DEMONSTRATION, PRACTICE }
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
    val mappingNote: String? = null,
    val tonicPitchClass: Int? = null,
    val tonalMode: String? = null,
    val chord: ChordShape? = null,
    val targetSkillIds: List<String> = emptyList(),
    val notation: NotationPrompt? = null,
    val relation: RelationPrompt? = null,
    val referenceCoordinates: List<Coordinate> = emptyList(),
) {
    val guided: Boolean get() = source == TaskSource.DEMONSTRATION || source == TaskSource.PREVIEW
}

@Serializable
data class InputRecord(
    val at: Long,
    val coordinate: Coordinate? = null,
    val symbol: String? = null,
    val result: ClickResult,
    val targetIndex: Int? = null,
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
    val audioReady: Boolean = false,
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
    val members: List<TargetEvidence> = emptyList(),
    val audioPlayed: Boolean = false,
)

@Serializable
data class TargetEvidence(val index: Int, val skillId: String, val direction: Direction, val coordinate: Coordinate?,
    val firstCorrect: Boolean, val independent: Boolean, val at: Long, val completed: Boolean)

@Serializable
data class NodeProgress(val masteredAt: Long? = null, val retainedOn: String? = null, val needsReview: Boolean = false)

@Serializable
data class LearningSession(val id: String = newId(), val startedAt: Long, val endedAt: Long? = null, val mode: String = "learning")

@Serializable enum class PracticeKind(val title: String) {
    POSITION_MIXED("音位双向混合"), FIND_POSITION("音名找位置"), NAME_NOTE("看位置认音名"),
    MAPPING_MIXED("唱名与级数混合"), FIXED_MAPPING("固定唱名双向"), DEGREE_MAPPING("C 大调级数双向"), TAB("TAB 定位"), CHORD_SHAPE("指定和弦形态"), READING("短句与五线谱"), FULL_MIXED("全指板 0–12 品混合"), RELATIONS("关系与结构"), REFERENCE_EAR("带参照听辨")
}
@Serializable data class PracticePlan(val nodeIds: List<String>, val kind: PracticeKind)
@Serializable data class SuspendedLesson(val currentNode: String, val sessionId: String?, val active: ActiveTask?, val reviewMode: Boolean)

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
    val viewedPositions: Map<String, Int> = emptyMap(),
    val sessions: List<LearningSession> = emptyList(),
    val sessionId: String? = null,
    val soundEnabled: Boolean = true,
    val themeId: String = "clear",
    val reviewMode: Boolean = false,
    val endedSummary: String? = null,
    val practice: PracticePlan? = null,
    val suspendedLesson: SuspendedLesson? = null,
    val fingeringMode: String = "colors",
    val fingerLegendSeen: Boolean = false,
    val viewedSkills: Map<String, Int> = emptyMap(),
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
