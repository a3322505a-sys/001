package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.core.FretPosition

/** The four independent V0.1 training directions. */
enum class QuestionType {
    FretToNote,
    FretToSolfege,
    NoteToSolfege,
    SolfegeToNote,
}

/** A UI-independent question produced by the training engine. */
data class Question(
    val type: QuestionType,
    val prompt: String,
    val fretPosition: FretPosition?,
    val choices: List<String>,
    val correctAnswer: String,
    val knowledgeItemId: String,
)

/** The outcome of one accepted answer submission. */
data class AnswerResult(
    val accepted: Boolean,
    val isCorrect: Boolean,
    val correctAnswer: String,
    val responseMs: Long,
    val knowledgeItemId: String,
)
