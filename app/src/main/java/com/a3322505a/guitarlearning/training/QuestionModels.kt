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

sealed interface QuestionPayload

data class FretNotePayload(
    val questionType: QuestionType,
    val position: FretPosition,
    val note: String,
    val solfege: String,
    val degree: Int,
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
) {
    init {
        require(answerChoices.isNotEmpty()) { "A question must have answer choices" }
        require(answerChoices.map { it.id }.distinct().size == answerChoices.size) {
            "Answer choice IDs must be unique"
        }
        require(answerChoices.any { it.id == correctChoiceId }) {
            "correctChoiceId must exist in answerChoices"
        }
    }

    /** Compatibility view used by the existing screens and tests. */
    val choices: List<String>
        get() = answerChoices.map { it.label }

    val correctAnswer: String
        get() = answerChoices.first { it.id == correctChoiceId }.label

    val type: QuestionType?
        get() = when (val value = payload) {
            is FretNotePayload -> value.questionType
            is MappingPayload -> value.questionType
            is IntervalPayload -> null
        }

    val fretPosition: FretPosition?
        get() = (payload as? FretNotePayload)?.position

    val note: String
        get() = when (val value = payload) {
            is FretNotePayload -> value.note
            is MappingPayload -> value.note
            is IntervalPayload -> value.startNote
        }

    val solfege: String
        get() = when (val value = payload) {
            is FretNotePayload -> value.solfege
            is MappingPayload -> value.solfege
            is IntervalPayload -> ""
        }

    val degree: Int
        get() = when (val value = payload) {
            is FretNotePayload -> value.degree
            is MappingPayload -> value.degree
            is IntervalPayload -> value.degreeSpan ?: 0
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
)
