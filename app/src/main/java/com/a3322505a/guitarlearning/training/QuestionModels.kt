package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.core.FretPosition

/** The physical-note direction plus the six directions of the three-way mapping. */
enum class QuestionType {
    FretToNote,
    FretToSolfege,
    NoteToSolfege,
    SolfegeToNote,
    NoteToDegree,
    DegreeToNote,
    SolfegeToDegree,
    DegreeToSolfege,
}

object TrainingModuleIds {
    const val FRET_NOTE = "fret_note"
    const val NOTE_MAPPING = "note_mapping"
    const val INTERVAL = "interval"
}

data class AnswerChoice(
    val id: String,
    val label: String,
)

enum class AnswerMode {
    CHOICE,
    FRETBOARD,
    FRETBOARD_SET,
    FRETBOARD_SEQUENCE,
}

sealed interface AnswerValue {
    data class Choice(val id: String) : AnswerValue

    /** An ordered symbolic answer such as C-D-E, do-re-mi, or 1-2-3. */
    data class SymbolSequence(
        val values: List<String>,
    ) : AnswerValue {
        init {
            require(values.size >= 2) { "A symbolic sequence needs at least two values" }
        }
    }

    /** An order-independent symbolic answer such as the chord tones 1-3-5. */
    data class SymbolSet(
        val values: Set<String>,
    ) : AnswerValue {
        init {
            require(values.size >= 2) { "A symbolic set needs at least two values" }
        }
    }

    data class FretPosition(
        val string: Int,
        val fret: Int,
    ) : AnswerValue

    data class FretSet(
        val positions: Set<FretPosition>,
    ) : AnswerValue {
        init {
            require(positions.isNotEmpty()) { "A submitted fret set must not be empty" }
        }
    }

    data class FretSequence(
        val positions: List<FretPosition>,
    ) : AnswerValue {
        init {
            require(positions.isNotEmpty()) { "A submitted fret sequence must not be empty" }
        }
    }
}

sealed interface QuestionPayload

data class FretNotePayload(
    val questionType: QuestionType,
    val position: FretPosition,
    val note: String,
    val solfege: String,
    val degree: Int,
) : QuestionPayload

data class FretboardCurriculumPayload(
    val level: Int,
    val anchor: FretPosition?,
    val target: FretPosition,
    val relationId: String,
) : QuestionPayload

data class FretboardNoteSetPayload(
    val note: String,
    /** Active Knowledge: the positions the learner must confirm for this question. */
    val targets: List<FretPosition>,
    val rangeId: String,
    /** Correct Universe: every real occurrence of the note inside the Training Range. */
    val correctUniverse: List<FretPosition> = targets,
) : QuestionPayload

data class FretboardSequencePayload(
    val level: Int,
    val targets: List<FretPosition>,
    val relationId: String,
) : QuestionPayload

data class MappingPayload(
    val questionType: QuestionType,
    val note: String,
    val solfege: String,
    val degree: Int,
) : QuestionPayload

data class IntervalPayload(
    val startNote: String,
    val endNote: String,
    val semitoneDistance: Int,
    val intervalName: String?,
    val degreeSpan: Int?,
    val octave: Boolean = false,
) : QuestionPayload

enum class QuestionWeightPolicy {
    MASTERY,
    BOUNDED_PER_ITEM,
}

/** A module-neutral question. Stable choice IDs keep judgement independent from UI labels. */
data class TrainingQuestion(
    val moduleId: String,
    val kind: String,
    val prompt: String,
    val answerChoices: List<AnswerChoice>,
    val correctChoiceId: String,
    val knowledgeItemId: String,
    val payload: QuestionPayload,
    val weightPolicy: QuestionWeightPolicy = QuestionWeightPolicy.MASTERY,
    val answerMode: AnswerMode = AnswerMode.CHOICE,
    val correctAnswerValue: AnswerValue = AnswerValue.Choice(correctChoiceId),
    val curriculumLevel: Int = 1,
) {
    init {
        require(curriculumLevel in 1..6) { "curriculumLevel must be between 1 and 6" }
        require(answerChoices.map { it.id }.distinct().size == answerChoices.size) {
            "Answer choice IDs must be unique"
        }
        when (answerMode) {
            AnswerMode.CHOICE -> {
                require(answerChoices.isNotEmpty()) { "A choice question must have answer choices" }
                require(answerChoices.any { it.id == correctChoiceId }) {
                    "correctChoiceId must exist in answerChoices"
                }
                require(correctAnswerValue == AnswerValue.Choice(correctChoiceId)) {
                    "Choice questions must use the correct choice ID as their semantic answer"
                }
            }
            AnswerMode.FRETBOARD -> require(correctAnswerValue is AnswerValue.FretPosition) {
                "Fretboard questions must use a fret position as their semantic answer"
            }
            AnswerMode.FRETBOARD_SET -> {
                require(correctAnswerValue is AnswerValue.FretSet) {
                    "Fretboard set questions must use a fret-position set as their semantic answer"
                }
                require(correctAnswerValue.positions.isNotEmpty()) {
                    "A correct fret set must not be empty"
                }
            }
            AnswerMode.FRETBOARD_SEQUENCE -> {
                require(correctAnswerValue is AnswerValue.FretSequence) {
                    "Fretboard sequence questions must use a fret sequence as their semantic answer"
                }
                require(correctAnswerValue.positions.size >= 2) {
                    "A correct fret sequence must contain at least two positions"
                }
            }
        }
    }

    /** Compatibility view used by the existing screens and tests. */
    val choices: List<String>
        get() = answerChoices.map { it.label }

    val correctAnswer: String
        get() = when (val value = correctAnswerValue) {
            is AnswerValue.Choice -> answerChoices.first { it.id == value.id }.label
            is AnswerValue.SymbolSequence -> value.values.joinToString(" → ")
            is AnswerValue.SymbolSet -> value.values.sorted().joinToString(" ")
            is AnswerValue.FretPosition -> value.string.toString() + "弦" + value.fret + "品"
            is AnswerValue.FretSet -> value.positions
                .sortedWith(compareBy({ it.string }, { it.fret }))
                .joinToString("、") { it.string.toString() + "弦" + it.fret + "品" }
            is AnswerValue.FretSequence -> value.positions.joinToString(" → ") {
                it.string.toString() + "弦" + it.fret + "品"
            }
        }

    val type: QuestionType?
        get() = when (val value = payload) {
            is FretNotePayload -> value.questionType
            is MappingPayload -> value.questionType
            is IntervalPayload -> null
            is FretboardCurriculumPayload -> null
            is FretboardNoteSetPayload -> QuestionType.FretToNote
            is FretboardSequencePayload -> null
        }

    val fretPosition: FretPosition?
        get() = when (val value = payload) {
            is FretNotePayload -> value.position
            is FretboardCurriculumPayload -> value.target
            else -> null
        }

    val targetPositions: List<FretPosition>
        get() = when (val value = payload) {
            is FretNotePayload -> listOf(value.position)
            is FretboardCurriculumPayload -> listOf(value.target)
            is FretboardNoteSetPayload -> value.targets
            is FretboardSequencePayload -> value.targets
            else -> emptyList()
        }

    val correctUniversePositions: List<FretPosition>
        get() = when (val value = payload) {
            is FretboardNoteSetPayload -> value.correctUniverse
            else -> targetPositions
        }

    val anchorPosition: FretPosition?
        get() = (payload as? FretboardCurriculumPayload)?.anchor

    val note: String
        get() = when (val value = payload) {
            is FretNotePayload -> value.note
            is MappingPayload -> value.note
            is IntervalPayload -> value.startNote
            is FretboardCurriculumPayload -> value.target.note
            is FretboardNoteSetPayload -> value.note
            is FretboardSequencePayload -> value.targets.first().note
        }

    val solfege: String
        get() = when (val value = payload) {
            is FretNotePayload -> value.solfege
            is MappingPayload -> value.solfege
            is IntervalPayload -> ""
            is FretboardCurriculumPayload -> value.target.solfege.orEmpty()
            is FretboardNoteSetPayload ->
                com.a3322505a.guitarlearning.core.GuitarCore.solfegeFor(value.note).orEmpty()
            is FretboardSequencePayload -> value.targets.first().solfege.orEmpty()
        }

    val degree: Int
        get() = when (val value = payload) {
            is FretNotePayload -> value.degree
            is MappingPayload -> value.degree
            is IntervalPayload -> value.degreeSpan ?: 0
            is FretboardCurriculumPayload ->
                com.a3322505a.guitarlearning.core.GuitarCore.degreeFor(value.target.note) ?: 0
            is FretboardNoteSetPayload ->
                com.a3322505a.guitarlearning.core.GuitarCore.degreeFor(value.note) ?: 0
            is FretboardSequencePayload ->
                com.a3322505a.guitarlearning.core.GuitarCore.degreeFor(value.targets.first().note)
                    ?: 0
        }

    fun choiceForLabel(label: String): AnswerChoice? =
        answerChoices.singleOrNull { it.label == label }
}

typealias Question = TrainingQuestion

/** The outcome of one accepted answer submission. */
data class AnswerResult(
    val accepted: Boolean,
    val isCorrect: Boolean,
    val submittedAnswer: String,
    val correctAnswer: String,
    val knowledgeItemId: String,
    val submittedChoiceId: String? = null,
    val correctChoiceId: String? = null,
    val submittedValue: AnswerValue? = null,
    val correctValue: AnswerValue? = null,
)
