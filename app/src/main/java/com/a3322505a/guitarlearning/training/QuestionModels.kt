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

/** A UI-independent question produced by the training engine. */
data class Question(
    val type: QuestionType,
    val prompt: String,
    val fretPosition: FretPosition?,
    val choices: List<String>,
    val correctAnswer: String,
    val knowledgeItemId: String,
    /** Canonical labels are kept with the question so feedback never parses UI text. */
    val note: String,
    val solfege: String,
    /** 1~7 scale degree; zero is retained only for source compatibility with old data. */
    val degree: Int = 0,
)

/** The outcome of one accepted answer submission. */
data class AnswerResult(
    val accepted: Boolean,
    val isCorrect: Boolean,
    val submittedAnswer: String,
    val correctAnswer: String,
    val responseMs: Long,
    val knowledgeItemId: String,
)
