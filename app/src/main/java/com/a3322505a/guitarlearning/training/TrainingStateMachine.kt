package com.a3322505a.guitarlearning.training

import com.a3322505a.guitarlearning.storage.Settings

const val CORRECT_FEEDBACK_DURATION_MS = 400L

/** Explicit lifecycle of one question from answering to manual or automatic advancement. */
sealed interface QuestionState {
    val question: Question

    data class AwaitingAnswer(
        override val question: Question,
    ) : QuestionState

    data class AwaitingSequenceAnswer(
        override val question: Question,
    ) : QuestionState

    data class SequenceProgress(
        override val question: Question,
        val selectedPositions: List<AnswerValue.FretPosition>,
    ) : QuestionState

    data class SequenceCompleted(
        override val question: Question,
        val result: AnswerResult,
        val selectedPositions: List<AnswerValue.FretPosition>,
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
        val confirmedPositions: List<AnswerValue.FretPosition> = emptyList(),
    ) : QuestionState

    data class CorrectionConfirmed(
        override val question: Question,
        val result: AnswerResult,
        val wrongPosition: AnswerValue.FretPosition,
        val correctPosition: AnswerValue.FretPosition,
        val confirmedPositions: List<AnswerValue.FretPosition> = emptyList(),
    ) : QuestionState
}

/** Coordinates accepted answers, stable review feedback, and next-question actions. */
class TrainingStateMachine(
    private val session: TrainingSession,
) {
    private var current: QuestionState = awaitingState(session.nextQuestion())

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
                    confirmedPositions = correction.confirmedPositions,
                )
            }
            return current
        }
        if (answer is AnswerValue.FretPosition && isSequenceInProgress()) {
            return submitSequencePosition(answer)
        }
        if (answer is AnswerValue.FretSequence && current is QuestionState.AwaitingSequenceAnswer) {
            val question = current.question
            val result = session.submitAnswer(answer)
            current = if (result.accepted && result.isCorrect) {
                QuestionState.SequenceCompleted(question, result, answer.positions)
            } else if (result.accepted) {
                QuestionState.Incorrect(question, result)
            } else {
                current
            }
            return current
        }
        return submitAwaitingAnswer { session.submitAnswer(answer) }
    }

    private fun isSequenceInProgress(): Boolean =
        current is QuestionState.AwaitingSequenceAnswer ||
            current is QuestionState.SequenceProgress

    private fun submitSequencePosition(answer: AnswerValue.FretPosition): QuestionState {
        val question = current.question
        val selected = (current as? QuestionState.SequenceProgress)?.selectedPositions.orEmpty()
        val expected = (question.correctAnswerValue as AnswerValue.FretSequence).positions
        val expectedPosition = expected[selected.size]

        if (answer != expectedPosition) {
            val result = session.submitAnswer(AnswerValue.FretSequence(selected + answer))
            if (result.accepted) {
                current = QuestionState.CorrectionRequired(
                    question = question,
                    result = result,
                    wrongPosition = answer,
                    correctPosition = expectedPosition,
                    confirmedPositions = selected,
                )
            }
            return current
        }

        val updated = selected + answer
        current = if (updated.size == expected.size) {
            val result = session.submitAnswer(AnswerValue.FretSequence(updated))
            check(result.accepted && result.isCorrect) {
                "A completed sequence must be accepted as correct"
            }
            QuestionState.SequenceCompleted(question, result, updated)
        } else {
            QuestionState.SequenceProgress(question, updated)
        }
        return current
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
                current is QuestionState.CorrectionConfirmed ||
                current is QuestionState.SequenceCompleted,
        ) {
            "The current question must be completed before advancing"
        }
        current = awaitingState(session.nextQuestion())
        return current
    }

    /** Starts a new round while retaining per-item learning progress. */
    fun resetRound(settings: Settings): QuestionState {
        current = awaitingState(session.resetForSettings(settings))
        return current
    }

    fun resetNoteTrainingRange(range: NoteTrainingRange): QuestionState =
        resetRound(range.applyTo(session.currentSettings()))

    private fun awaitingState(question: Question): QuestionState =
        if (question.answerMode == AnswerMode.FRETBOARD_SEQUENCE) {
            QuestionState.AwaitingSequenceAnswer(question)
        } else {
            QuestionState.AwaitingAnswer(question)
        }
}
