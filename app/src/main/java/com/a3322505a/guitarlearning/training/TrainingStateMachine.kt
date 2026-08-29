package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.Settings

const val CORRECT_FEEDBACK_DURATION_MS = 400L

/** Explicit lifecycle of one question from answering to manual or automatic advancement. */
sealed interface QuestionState {
    val question: Question

    data class AwaitingAnswer(
        override val question: Question,
    ) : QuestionState

    data class Correct(
        override val question: Question,
        val result: AnswerResult,
    ) : QuestionState

    data class Incorrect(
        override val question: Question,
        val result: AnswerResult,
    ) : QuestionState
}

/** Coordinates accepted answers, stable review feedback, and next-question actions. */
class TrainingStateMachine(
    private val session: TrainingSession,
) {
    private var current: QuestionState = QuestionState.AwaitingAnswer(session.nextQuestion())

    val state: QuestionState
        get() = current

    fun submitAnswer(answer: String): QuestionState {
        val awaiting = current as? QuestionState.AwaitingAnswer ?: return current
        val result = session.submitAnswer(answer)
        if (!result.accepted) return current

        current = if (result.isCorrect) {
            QuestionState.Correct(awaiting.question, result)
        } else {
            QuestionState.Incorrect(awaiting.question, result)
        }
        return current
    }

    fun nextQuestion(): QuestionState {
        check(current !is QuestionState.AwaitingAnswer) {
            "The current question must be answered before advancing"
        }
        current = QuestionState.AwaitingAnswer(session.nextQuestion())
        return current
    }

    /** Starts a new round while retaining per-item learning progress. */
    fun resetRound(settings: Settings): QuestionState {
        current = QuestionState.AwaitingAnswer(session.resetForSettings(settings))
        return current
    }

    fun resetNoteTrainingRange(range: NoteTrainingRange): QuestionState =
        resetRound(range.applyTo(session.currentSettings()))
}
