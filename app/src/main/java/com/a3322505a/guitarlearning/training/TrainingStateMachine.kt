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

    data class CorrectionRequired(
        override val question: Question,
        val result: AnswerResult,
        val wrongPosition: AnswerValue.FretPosition,
        val correctPosition: AnswerValue.FretPosition,
    ) : QuestionState

    data class CorrectionConfirmed(
        override val question: Question,
        val result: AnswerResult,
        val wrongPosition: AnswerValue.FretPosition,
        val correctPosition: AnswerValue.FretPosition,
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
        return submitAwaitingAnswer { session.submitAnswer(answer) }
    }

    fun submitAnswer(answer: AnswerValue): QuestionState {
        val correction = current as? QuestionState.CorrectionRequired
        if (correction != null) {
            if (answer == correction.correctPosition) {
                current = QuestionState.CorrectionConfirmed(
                    question = correction.question,
                    result = correction.result,
                    wrongPosition = correction.wrongPosition,
                    correctPosition = correction.correctPosition,
                )
            }
            return current
        }
        return submitAwaitingAnswer { session.submitAnswer(answer) }
    }

    private fun submitAwaitingAnswer(submit: () -> AnswerResult): QuestionState {
        val awaiting = current as? QuestionState.AwaitingAnswer ?: return current
        val result = submit()
        if (!result.accepted) return current
        val submittedPosition = result.submittedValue as? AnswerValue.FretPosition
        val correctPosition = result.correctValue as? AnswerValue.FretPosition

        current = if (result.isCorrect) {
            QuestionState.Correct(awaiting.question, result)
        } else if (submittedPosition != null && correctPosition != null) {
            QuestionState.CorrectionRequired(
                question = awaiting.question,
                result = result,
                wrongPosition = submittedPosition,
                correctPosition = correctPosition,
            )
        } else {
            QuestionState.Incorrect(awaiting.question, result)
        }
        return current
    }

    fun nextQuestion(): QuestionState {
        check(
            current is QuestionState.Correct ||
                current is QuestionState.Incorrect ||
                current is QuestionState.CorrectionConfirmed,
        ) {
            "The current question must be completed before advancing"
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
